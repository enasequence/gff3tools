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
package uk.ac.ebi.embl.gff3tools.gff3.reader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Directives;

/**
 * Locates the byte offset of the first FASTA-side line (the {@code ##FASTA}
 * directive, or the first {@code >} header if no directive is present) in a
 * GFF3 file, using a binary search over raw byte offsets.
 *
 * <p>The GFF3 file structure — zero or more annotation lines, then
 * optionally exactly one {@code ##FASTA} directive, then zero or more FASTA
 * lines to EOF — makes "is byte P at-or-after the FASTA boundary" a
 * monotonic predicate, which is what makes the binary search sound. Each
 * probe reads at most one line (an O(line-length) forward read), so locating
 * the boundary takes O(log fileSize) probes with no full linear scan of
 * either section.
 */
public final class FastaSectionLocator {

    private static final int LINE_PROBE_BUFFER_SIZE = 8192;
    private static final int STREAM_BUFFER_SIZE = 4096;
    private static final byte[] FASTA_MARKER = GFF3Directives.FASTA.getBytes(StandardCharsets.UTF_8);

    private FastaSectionLocator() {}

    /**
     * Returns the byte offset of the first FASTA-side line in {@code gff3Path},
     * or {@code -1} if the file has no FASTA-side line anywhere. The returned
     * offset is guaranteed to be at or before the first {@code >} header byte.
     */
    public static long locate(Path gff3Path) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(gff3Path, StandardOpenOption.READ)) {
            return locate(channel);
        }
    }

    /**
     * Returns the byte offset of the {@code ##FASTA} directive in the stream, or {@code -1} if not
     * found. Used for legacy records where the offset was not stored at upload time. The caller is
     * responsible for opening and closing the stream.
     */
    public static long locate(InputStream in) throws IOException {
        byte[] buf = new byte[STREAM_BUFFER_SIZE];
        long offset = 0;
        long markerStart = -1;
        int matchLen = 0;
        boolean atLineStart = true;
        int n;
        while ((n = in.read(buf)) != -1) {
            for (int i = 0; i < n; i++) {
                int b = buf[i] & 0xFF;
                if (matchLen == 0) {
                    if (atLineStart && b == FASTA_MARKER[0]) {
                        markerStart = offset;
                        matchLen = 1;
                    }
                } else if (b == FASTA_MARKER[matchLen]) {
                    matchLen++;
                    if (matchLen == FASTA_MARKER.length) {
                        return markerStart;
                    }
                } else {
                    matchLen = 0;
                    markerStart = -1;
                }
                atLineStart = (b == '\n');
                offset++;
            }
        }
        return -1L;
    }

    /**
     * Returns the byte offset of the {@code ##FASTA} directive in the byte array, or {@code -1} if
     * not found. Used when the GFF3 document is already in memory.
     */
    public static long locate(byte[] bytes) {
        try {
            return locate(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static long locate(SeekableByteChannel channel) throws IOException {
        long fileSize = channel.size();

        long lo = 0;
        long hi = fileSize;
        boolean fastaSideSeen = false;
        // Start of the earliest line classified FASTA-side so far. Kept apart from hi
        // because hi may also shrink past blank lines, which must never be returned.
        long boundary = fileSize;

        while (lo < hi) {
            long mid = (lo + hi) >>> 1;
            long lineStart = findLineStart(channel, mid, fileSize);

            if (lineStart >= hi) {
                // The forward probe overshot hi without discovering a new line boundary
                // strictly between mid and hi (e.g. mid landed inside the single
                // remaining line of the bracket). lo is always maintained as a genuine,
                // as-yet-unclassified line start, so fall back to classifying that line
                // directly instead of losing it.
                lineStart = lo;
            }

            // Blank lines and '#' lines (other than ##FASTA) are ambiguous and can occur on
            // either side (e.g. between two FASTA records), so they carry no boundary
            // information: skip forward within this probe, without touching lo/hi, until a
            // classifiable line is found. The skip is bounded by hi as well as fileSize:
            // running into hi or EOF means only [lineStart, hi) is ambiguous. Lines in
            // [lo, lineStart) are still unclassified, so shrink the bracket from above and
            // keep searching them.
            Line line = readLine(channel, lineStart, fileSize);
            while (line.isAmbiguous() && line.nextLineStart() < fileSize && line.nextLineStart() < hi) {
                line = readLine(channel, line.nextLineStart(), fileSize);
            }

            if (line.isAmbiguous()) {
                hi = lineStart;
                continue;
            }

            if (classify(line.content()) == Classification.FASTA_SIDE) {
                fastaSideSeen = true;
                hi = line.lineStart();
                boundary = hi;
            } else {
                lo = line.nextLineStart();
            }
        }

        return fastaSideSeen ? boundary : -1L;
    }

    private enum Classification {
        FASTA_SIDE,
        ANNOTATION_SIDE
    }

    private static Classification classify(String line) {
        if (line.equals("##FASTA")) {
            return Classification.FASTA_SIDE;
        }
        if (line.startsWith(">")) {
            return Classification.FASTA_SIDE;
        }
        // Only a feature line is definitely annotation. A tab alone is not enough: a broken
        // sequence line can carry a stray one. '#' lines never get here; they are skipped as
        // ambiguous by the caller.
        if (isFeatureLine(line)) {
            return Classification.ANNOTATION_SIDE;
        }
        // Any other line is treated as a sequence line, valid or not. Calling it annotation
        // would move the boundary past it and silently drop the records before it; as
        // FASTA-side it is handed to FastaReader, which rejects bad characters. A malformed
        // feature line lands here too, which is harmless: the boundary is then too early, and
        // FastaReader skips everything before the first '>' header.
        return Classification.FASTA_SIDE;
    }

    /**
     * A feature line as {@code GFF3FileReader.readFeature} reads one: at least 9
     * tab-separated columns, with start and end (columns 4 and 5) numeric.
     */
    private static boolean isFeatureLine(String line) {
        if (line.indexOf('\t') < 0) {
            return false;
        }
        String[] columns = line.split("\t");
        return columns.length >= 9 && isDigits(columns[3]) && isDigits(columns[4]);
    }

    private static boolean isDigits(String s) {
        return !s.isEmpty() && s.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * Finds the start of the next full line at/after {@code from}, by reading
     * a small buffer forward from {@code from} to the next {@code \n}/{@code \r}.
     * The byte after that terminator is the line start. {@code from == 0} is
     * special-cased as line start 0.
     */
    private static long findLineStart(SeekableByteChannel channel, long from, long fileSize) throws IOException {
        if (from == 0) {
            return 0;
        }
        if (from >= fileSize) {
            return fileSize;
        }

        ByteBuffer buffer = ByteBuffer.allocate(LINE_PROBE_BUFFER_SIZE);
        long position = from;

        while (position < fileSize) {
            buffer.clear();
            channel.position(position);
            int read = channel.read(buffer);
            if (read <= 0) {
                return fileSize;
            }
            buffer.flip();

            for (int i = 0; i < read; i++) {
                byte b = buffer.get(i);
                if (b == '\n' || b == '\r') {
                    long terminatorPos = position + i;
                    return skipLineEnding(channel, terminatorPos, fileSize);
                }
            }
            position += read;
        }

        return fileSize;
    }

    /**
     * Given the position of a line-ending byte, returns the start of the next
     * line, treating a {@code \r\n} pair as a single line ending.
     */
    private static long skipLineEnding(SeekableByteChannel channel, long terminatorPos, long fileSize)
            throws IOException {
        long next = terminatorPos + 1;
        if (next >= fileSize) {
            return fileSize;
        }

        ByteBuffer buffer = ByteBuffer.allocate(1);
        channel.position(terminatorPos);
        channel.read(buffer);
        buffer.flip();
        byte terminator = buffer.get();

        if (terminator == '\r') {
            buffer.clear();
            channel.position(next);
            int read = channel.read(buffer);
            if (read > 0) {
                buffer.flip();
                if (buffer.get() == '\n') {
                    return next + 1;
                }
            }
        }

        return next;
    }

    /**
     * Reads a single line starting at {@code lineStart}, up to the next
     * {@code \n}/{@code \r} or EOF.
     */
    private static Line readLine(SeekableByteChannel channel, long lineStart, long fileSize) throws IOException {
        if (lineStart >= fileSize) {
            return new Line("", lineStart, fileSize);
        }

        StringBuilder content = new StringBuilder();
        ByteBuffer buffer = ByteBuffer.allocate(LINE_PROBE_BUFFER_SIZE);
        long position = lineStart;

        while (position < fileSize) {
            buffer.clear();
            channel.position(position);
            int read = channel.read(buffer);
            if (read <= 0) {
                break;
            }
            buffer.flip();

            boolean terminated = false;
            int i = 0;
            for (; i < read; i++) {
                byte b = buffer.get(i);
                if (b == '\n' || b == '\r') {
                    terminated = true;
                    break;
                }
            }

            byte[] bytes = new byte[i];
            buffer.get(bytes, 0, i);
            content.append(new String(bytes, StandardCharsets.US_ASCII));

            if (terminated) {
                long terminatorPos = position + i;
                long nextLineStart = skipLineEnding(channel, terminatorPos, fileSize);
                return new Line(content.toString(), lineStart, nextLineStart);
            }

            position += read;
        }

        return new Line(content.toString(), lineStart, fileSize);
    }

    private record Line(String content, long lineStart, long nextLineStart) {
        /** Blank, or a '#' line other than ##FASTA: either can sit on either side of the boundary. */
        boolean isAmbiguous() {
            return content.isBlank() || (content.startsWith("#") && !content.equals("##FASTA"));
        }
    }
}
