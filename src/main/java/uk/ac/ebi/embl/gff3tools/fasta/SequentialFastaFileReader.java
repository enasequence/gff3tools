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
package uk.ac.ebi.embl.gff3tools.fasta;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.JsonHeaderParser;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.ParsedHeader;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.ByteSpan;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndexBuilder;

public class SequentialFastaFileReader implements AutoCloseable {

    private static final int BUFFER_SIZE = 4 * 1024 * 1024; // 4 MB
    private static final int CHAR_BUF_SIZE = 512 * 1024; // 512 KB
    private static final byte GT = (byte) '>';
    private static final byte LF = (byte) '\n';
    private static final byte CR = (byte) '\r';

    private final FileChannel channel;
    private final long fileSize;
    private final JsonHeaderParser headerParser;
    private final SequenceAlphabet alphabet;

    public SequentialFastaFileReader(File file) throws IOException {
        this(file, new JsonHeaderParser(), SequenceAlphabet.defaultNucleotideAlphabet());
    }

    public SequentialFastaFileReader(File file, JsonHeaderParser parser, SequenceAlphabet alphabet) throws IOException {
        Objects.requireNonNull(file, "Input FASTA file is null");
        if (!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
        if (file.isDirectory()) throw new FileNotFoundException("Directory: " + file.getAbsolutePath());
        if (!file.canRead()) throw new IllegalArgumentException("No read permission: " + file.getAbsolutePath());
        this.headerParser = Objects.requireNonNull(parser, "parser");
        this.alphabet = Objects.requireNonNull(alphabet, "alphabet");
        this.channel = new FileInputStream(file).getChannel();
        this.fileSize = channel.size();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public boolean readingFile() {
        return channel.isOpen();
    }

    public String getSequenceSliceString(ByteSpan span) throws IOException {
        return readAsciiWithoutNewlines(span.start, span.endEx);
    }

    /** Char-stream view over [span.start, span.endEx): ASCII decode, skip LF/CR.
     *  Uses absolute reads; does NOT change channel.position(). */
    public java.io.Reader getSequenceSliceReader(ByteSpan span) {
        final long start = span.start;
        final long endEx = span.endEx;

        return new java.io.Reader() {
            private long pos = start;

            private final java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocateDirect(CHAR_BUF_SIZE);

            {
                // allocate buffer and mark it EMPTY so the very first read() refills it from the channel.
                // Without this, hasRemaining() is true and we'll read uninitialized bytes (→ '\0').
                buf.limit(0);
            }

            @Override
            public int read(
                    char[] characterBuffer, int startingWriteIndexInCharacterBuffer, int maximumNumberOfCharsToRead)
                    throws java.io.IOException {
                // --- Validate caller’s target window [off .. off + len) ---
                ValidateTargetWindow(characterBuffer, startingWriteIndexInCharacterBuffer, maximumNumberOfCharsToRead);
                if (maximumNumberOfCharsToRead == 0) return 0;

                int out = 0;
                while (out < maximumNumberOfCharsToRead) {
                    // --- Prep the buffer for next read & fill it out ---
                    if (!buf.hasRemaining()) {
                        if (pos >= endEx) break; // if end of slice reached, stop reading

                        buf.clear();
                        int toRead = (int) Math.min(buf.capacity(), endEx - pos);
                        buf.limit(toRead);

                        int n = channel.read(buf, pos);
                        if (n <= 0) break; // if no bytes were read, break
                        pos += n;
                        buf.flip();
                    }
                    // Drain bytes + ASCII decode -> writees chars into caller’s window [off .. off+len)
                    while (buf.hasRemaining() && out < maximumNumberOfCharsToRead) {
                        byte b = buf.get();
                        if (b == LF || b == CR) continue; // skip irrelevant bytes
                        characterBuffer[startingWriteIndexInCharacterBuffer + out] = (char) (b & 0xFF);
                        out++;
                    }
                }
                // If we produced nothing AND we’re at EOF, signal -1
                return (out == 0) ? -1 : out;
            }

            private void ValidateTargetWindow(
                    char[] characterBuffer, int startingWriteIndexInCharacterBuffer, int maximumNumberOfCharsToRead)
                    throws java.io.IOException {
                if (characterBuffer == null) throw new NullPointerException("characterBuffer");
                if (startingWriteIndexInCharacterBuffer < 0
                        || maximumNumberOfCharsToRead < 0
                        || startingWriteIndexInCharacterBuffer + maximumNumberOfCharsToRead > characterBuffer.length) {
                    throw new IndexOutOfBoundsException("off=" + startingWriteIndexInCharacterBuffer + " len="
                            + maximumNumberOfCharsToRead + " bufLen="
                            + characterBuffer.length);
                }
            }

            @Override
            public int read() throws java.io.IOException {
                char[] one = new char[1];
                int n = read(one, 0, 1);
                return (n == -1) ? -1 : one[0];
            }

            @Override
            public boolean ready() {
                return buf.hasRemaining() || pos < endEx;
            }

            @Override
            public void close() {
                /* no-op, channel is kept alive */
            }
        };
    }

    public List<FastaEntryInternal> readAll() throws FastaFileException, IOException {
        long position = 0;
        List<FastaEntryInternal> entries = new ArrayList<>();
        while (true) {
            var entry = readNext(position);
            if (entry.isEmpty()) break;
            entries.add(entry.get());
            position = channel.position();
        }
        return entries;
    }

    /** Reads the next FASTA entry starting at or after 'from'. */
    private Optional<FastaEntryInternal> readNext(long from) throws FastaFileException {
        try {
            OptionalLong headerPosOpt = seekToNextHeader(from);
            if (headerPosOpt.isEmpty()) return Optional.empty();

            long headerPos = headerPosOpt.getAsLong();
            channel.position(headerPos);

            String headerLine = readAsciiLineFromCurrentPosition();
            if (headerLine == null) throw new FastaFileException("Header is malformed at byte " + headerPos);
            ParsedHeader ph = headerParser.parse(headerLine);

            long sequenceStartPos = channel.position(); // first byte after header line is the sequence position
            SequenceIndexBuilder sib = new SequenceIndexBuilder(channel, fileSize, alphabet);
            SequenceIndexBuilder.Result res = sib.buildFrom(sequenceStartPos);

            // Move reader cursor to the sequence start position
            channel.position(sequenceStartPos);

            FastaEntryInternal e = new FastaEntryInternal();
            e.setSubmissionId(ph.getId());
            e.setHeader(ph.getHeader());
            e.setFastaStartByte(headerPos);
            e.setSequenceIndex(res.index);

            return Optional.of(e);
        } catch (IOException io) {
            long pos = safePos();
            throw new FastaFileException("I/O while reading FASTA at byte " + pos + ": " + io.getMessage(), io);
        }
    }

    /** Read ASCII bytes from [byteStart, byteEndExclusive) skipping LF/CR; does not change channel.position(). */
    public String readAsciiWithoutNewlines(long byteStart, long byteEndExclusive) throws IOException {
        if (byteStart < 0 || byteEndExclusive < byteStart || byteEndExclusive > fileSize) {
            throw new IllegalArgumentException("Bad byte window: " + byteStart + ".." + byteEndExclusive);
        }
        long remain = byteEndExclusive - byteStart;
        long off = byteStart;

        // pre-size builder with a sane cap (skip newlines, so content <= remain)
        int expect = (int) Math.min(remain, 1_000_000L);
        StringBuilder sb = new StringBuilder(expect);

        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);
        while (remain > 0) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), remain);
            buf.limit(want);
            int n = channel.read(buf, off);
            if (n <= 0) break;
            buf.flip();
            while (buf.hasRemaining()) {
                byte b = buf.get();
                if (b == LF || b == CR) continue; // omit line breaks on the fly
                sb.append((char) (b & 0xFF)); // ASCII
            }
            remain -= n;
            off += n;
        }
        return sb.toString();
    }

    // ------------------ header seeking & line reading ------------------

    private OptionalLong seekToNextHeader(long from) throws IOException {
        if (from >= fileSize) return OptionalLong.empty();
        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);

        while (from < fileSize) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), fileSize - from);
            buf.limit(want);
            int n = channel.read(buf, from);
            if (n <= 0) break;
            buf.flip();
            while (buf.hasRemaining()) {
                long abs = from + buf.position();
                if (buf.get() == GT && isLineStart(abs)) {
                    return OptionalLong.of(abs);
                }
            }
            from += n;
        }
        return OptionalLong.empty();
    }

    private boolean isLineStart(long abs) throws IOException {
        if (abs == 0) return true;
        if (abs > fileSize) return false;
        return peek(abs - 1) == LF;
    }

    private byte peek(long abs) throws IOException {
        if (abs < 0 || abs >= fileSize) return 0;
        ByteBuffer one = ByteBuffer.allocate(1);
        int n = channel.read(one, abs);
        return (n == 1) ? one.get(0) : 0;
    }

    /** Reads one ASCII line from current position, advances past LF or to EOF. */
    private String readAsciiLineFromCurrentPosition() throws IOException {
        long scanPos = channel.position();
        if (scanPos >= fileSize) return null;

        StringBuilder sb = new StringBuilder(256);
        ByteBuffer buf = ByteBuffer.allocateDirect(BUFFER_SIZE);

        while (scanPos < fileSize) {
            buf.clear();
            int want = (int) Math.min(buf.capacity(), fileSize - scanPos);
            buf.limit(want);
            int n = channel.read(buf, scanPos);
            if (n <= 0) break;
            buf.flip();

            int lfIndex = indexOf(buf, LF);
            if (lfIndex >= 0) {
                appendAscii(sb, buf, lfIndex);
                long nextLineStart = scanPos + lfIndex + 1; // consume LF
                channel.position(nextLineStart);
                return sb.toString();
            } else {
                appendAscii(sb, buf, buf.remaining());
                scanPos += n;
            }
        }
        channel.position(fileSize);
        return sb.toString();
    }

    private static int indexOf(ByteBuffer buf, byte target) {
        for (int i = 0; i < buf.remaining(); i++) {
            if (buf.get(buf.position() + i) == target) return i;
        }
        return -1;
    }

    private static void appendAscii(StringBuilder sb, ByteBuffer buf, int len) {
        byte[] chunk = new byte[len];
        buf.get(chunk);
        sb.append(new String(chunk, java.nio.charset.StandardCharsets.US_ASCII));
    }

    private long safePos() {
        try {
            return channel.position();
        } catch (IOException e) {
            return -1;
        }
    }
}
