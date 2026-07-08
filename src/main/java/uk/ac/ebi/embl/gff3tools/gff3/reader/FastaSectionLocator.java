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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalLong;

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

    private FastaSectionLocator() {}

    /**
     * Returns the byte offset of the first FASTA-side line in {@code gff3Path},
     * or an empty result if the file has no FASTA-side line anywhere. The
     * returned offset is guaranteed to be at or before the first {@code >}
     * header byte in the file.
     */
    public static OptionalLong locate(Path gff3Path) {
        try (SeekableByteChannel channel = Files.newByteChannel(gff3Path, StandardOpenOption.READ)) {
            return locate(channel);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static OptionalLong locate(SeekableByteChannel channel) throws IOException {
        long fileSize = channel.size();

        long lo = 0;
        long hi = fileSize;
        boolean fastaSideSeen = false;

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

            // Blank lines are ambiguous and can occur on either side (e.g. between two
            // FASTA records), so they carry no boundary information: skip forward within
            // this probe, without touching lo/hi, until a classifiable line is found. The
            // skip is bounded by hi as well as fileSize: running into the already-known
            // FASTA boundary (e.g. a lone blank line immediately preceding it) means the
            // whole [lo, hi) bracket is blank, so there is nothing left to classify.
            Line line = readLine(channel, lineStart, fileSize);
            while (line.isBlank() && line.nextLineStart() < fileSize && line.nextLineStart() < hi) {
                line = readLine(channel, line.nextLineStart(), fileSize);
            }

            if (line.isBlank()) {
                lo = hi;
                continue;
            }

            if (classify(line.content()) == Classification.FASTA_SIDE) {
                fastaSideSeen = true;
                hi = line.lineStart();
            } else {
                lo = line.nextLineStart();
            }
        }

        return fastaSideSeen ? OptionalLong.of(hi) : OptionalLong.empty();
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
        if (line.indexOf('\t') >= 0 || line.startsWith("#")) {
            return Classification.ANNOTATION_SIDE;
        }
        if (isProteinAlphabetLine(line)) {
            return Classification.FASTA_SIDE;
        }
        return Classification.ANNOTATION_SIDE;
    }

    private static boolean isProteinAlphabetLine(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            boolean letter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            if (!letter && c != '*') {
                return false;
            }
        }
        return true;
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
        boolean isBlank() {
            return content.isBlank();
        }
    }
}
