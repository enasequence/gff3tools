/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;

public final class SequenceIndexBuilder {
    private static final int SCAN_BUF_SIZE = 4 * 1024 * 1024; // 4 MB
    private static final int COUNT_BUF_SIZE = 4 * 1024 * 1024; // 2 MB

    private static final byte GT = (byte) '>';
    private static final byte LF = (byte) '\n';

    private final FileChannel ch;
    private final long fileSize;
    private final SequenceAlphabet alphabet;

    public SequenceIndexBuilder(FileChannel ch, long fileSize, SequenceAlphabet alphabet) {
        this.ch = ch;
        this.fileSize = fileSize;
        this.alphabet = alphabet;
    }

    /** Build a SequenceIndex starting at 'startPos' (first byte after header line). */
    public SequenceIndex buildFrom(long startPos) throws IOException, FastaFileException {
        ScanState s = new ScanState(startPos, fileSize);
        ByteBuffer buf = newScanBuffer();

        // ------------- scan raw bytes into provisional "sequence lines" -------------
        while (s.pos < fileSize) {
            int n = fillBuffer(buf, s.pos);
            if (n <= 0) break;
            if (processBuffer(buf, s)) break; // found next header
            s.pos += n;
        }
        commitOpenLineIfAny(s);

        // ------------- filter window & compute metadata -------------
        List<LineEntry> filtered = filterLinesWithinWindow(s.lines, s.firstBaseByte, s.nextHdr);

        long firstBaseByte = filtered.isEmpty() ? -1 : filtered.get(0).byteStart;
        long lastBaseByte = filtered.isEmpty() ? -1 : (filtered.get(filtered.size() - 1).byteEndExclusive - 1);

        long startN = 0, endN = 0;
        if (!filtered.isEmpty()) {
            startN = countLeadingNs(firstBaseByte, lastBaseByte); // (3) only first line
            endN = countTrailingNs(filtered.get(filtered.size() - 1)); // (4) only last line
        }

        return new SequenceIndex(firstBaseByte, startN, lastBaseByte, endN, filtered, s.nextHdr);
    }

    // =====================================================================
    // =                          scanning core                            =
    // =====================================================================

    private static final class ScanState {
        long pos; // absolute scan position
        long firstBaseByte = -1; // first allowed base byte seen
        long lastBaseByte = -1; // last  allowed base byte seen
        long nextHdr; // byte of next header (or file end)

        long lineFirstByte = -1; // first allowed base byte in current line
        long lineLastByte = -1; // last  allowed base byte in current line
        long basesSoFar = 0;
        long basesInLine = 0;

        final ArrayList<LineEntry> lines = new ArrayList<>(256);

        ScanState(long startPos, long fileSize) {
            this.pos = startPos;
            this.nextHdr = fileSize;
        }
    }

    private ByteBuffer newScanBuffer() {
        return ByteBuffer.allocateDirect(SCAN_BUF_SIZE);
    }

    private int fillBuffer(ByteBuffer buf, long at) throws IOException {
        buf.clear();
        int want = (int) Math.min(buf.capacity(), fileSize - at);
        buf.limit(want);
        return ch.read(buf, at); // absolute read; does not touch ch.position()
    }

    /** Returns true if we hit the next header and should stop scanning this entry. */
    private boolean processBuffer(ByteBuffer buf, ScanState s) throws IOException, FastaFileException {
        buf.flip();
        while (buf.hasRemaining()) {
            int idx = buf.position();
            byte b = buf.get();
            long abs = s.pos + idx;

            if (isHeaderStart(b, abs)) {
                s.nextHdr = abs; // stop window at header byte
                commitOpenLineIfAny(s); // finalize any in-flight line
                return true;
            } else if (alphabet.isNonSequenceAllowedChar(b)) { // end of a displayed sequence line or CR
                commitOpenLineIfAny(s); // only lines with bases are committed
                continue;
            } else if (alphabet.isAllowedBase(b)) {
                observeBase(abs, s);
            } else {
                throw new FastaFileException(String.format(
                        "Illegal character '%s' (byte value: %d) at absolute file position %d. "
                                + "This character is not allowed by the current FASTA alphabet. "
                                + "Expected only characters: %s",
                        (char) (b & 0xFF), b & 0xFF, abs, alphabet.describeAllowed()));
            }
        }
        return false;
    }

    private boolean isHeaderStart(byte b, long abs) throws IOException {
        return b == GT && isLineStart(abs);
    }

    /** header must be at file start or immediately after LF */
    private boolean isLineStart(long abs) throws IOException {
        if (abs == 0) return true;
        if (abs > fileSize) return false;
        return peek(abs - 1) == LF;
    }

    private byte peek(long abs) throws IOException {
        if (abs < 0 || abs >= fileSize) return 0;
        ByteBuffer one = ByteBuffer.allocate(1);
        int n = ch.read(one, abs);
        return (n == 1) ? one.get(0) : 0;
    }

    private void observeBase(long abs, ScanState s) {
        if (s.lineFirstByte < 0) s.lineFirstByte = abs;
        s.lineLastByte = abs;
        s.basesInLine++;

        if (s.firstBaseByte < 0) s.firstBaseByte = abs;
        s.lastBaseByte = abs;
    }

    private void commitOpenLineIfAny(ScanState s) {
        if (s.basesInLine <= 0) return; // skip empty lines
        long baseStart = s.basesSoFar + 1;
        long baseEnd = s.basesSoFar + s.basesInLine;
        long byteStart = s.lineFirstByte;
        long byteEndEx = s.lineLastByte + 1; // half-open

        s.lines.add(new LineEntry(baseStart, baseEnd, byteStart, byteEndEx));

        s.basesSoFar += s.basesInLine;
        s.basesInLine = 0;
        s.lineFirstByte = -1;
        s.lineLastByte = -1;
    }

    // =====================================================================
    // =                  window filter & edge N counting                  =
    // =====================================================================

    /** (1)+(2) Keep only lines fully inside [firstBaseByte, nextHdr) and already non-empty. */
    private List<LineEntry> filterLinesWithinWindow(List<LineEntry> raw, long firstBaseByte, long nextHdr) {
        if (firstBaseByte < 0 || raw.isEmpty()) return List.of();
        ArrayList<LineEntry> out = new ArrayList<>(raw.size());
        for (LineEntry L : raw) {
            if (L.byteStart >= firstBaseByte && L.byteEndExclusive <= nextHdr) {
                out.add(L);
            }
        }
        return out;
    }

    /** (3) count 'N'/'n' from the start of the first sequence line only. */
    private long countLeadingNs(long byteStart, long byteEnd) throws IOException {
        long remaining = byteEnd - byteStart;
        long offset = byteStart;
        long count = 0;

        ByteBuffer buf = ByteBuffer.allocateDirect(COUNT_BUF_SIZE);
        while (remaining > 0) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), remaining);
            buf.limit(want);
            int n = ch.read(buf, offset);
            if (n <= 0) break;
            buf.flip();
            for (int i = 0; i < n; i++) {
                byte b = buf.get();
                if (alphabet.isNBase(b)) {
                    count++;
                } else if (alphabet.isAllowedBase(b)) return count; // found non-N base
            }
            remaining -= n;
            offset += n;
        }
        return count;
    }

    /** (4) count 'N'/'n' at the tail of the last sequence line only. */
    private long countTrailingNs(LineEntry line) throws IOException {
        long remaining = line.lengthBytes();
        long offset = line.byteStart;
        long trailing = 0;

        ByteBuffer buf = ByteBuffer.allocateDirect(COUNT_BUF_SIZE);
        while (remaining > 0) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), remaining);
            buf.limit(want);
            int n = ch.read(buf, offset);
            if (n <= 0) break;
            buf.flip();
            for (int i = 0; i < n; i++) {
                byte b = buf.get();
                if (alphabet.isNBase(b)) trailing++;
                else trailing = 0;
            }
            remaining -= n;
            offset += n;
        }
        return trailing;
    }
}
