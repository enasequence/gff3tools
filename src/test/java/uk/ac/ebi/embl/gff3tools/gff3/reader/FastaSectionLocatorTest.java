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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FastaSectionLocatorTest {

    private Path tempFile;

    @AfterEach
    void cleanup() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    private Path writeFile(String content) throws IOException {
        tempFile = Files.createTempFile("fasta_locator", ".gff3");
        Files.write(tempFile, content.getBytes(StandardCharsets.US_ASCII));
        return tempFile;
    }

    @Test
    void emptyFileReturnsEmpty() throws IOException {
        Path file = writeFile("");

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isEmpty());
    }

    @Test
    void noFastaMarkerAndNoHeaderReturnsEmpty() throws IOException {
        Path file = writeFile(String.join(
                "\n",
                "##gff-version 3",
                "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1",
                "chr1\t.\tmRNA\t1\t1000\t.\t+\t.\tID=mrna1",
                ""));

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isEmpty());
    }

    @Test
    void bareHeaderBlockWithNoFastaMarkerResolvesToFirstHeader() throws IOException {
        String content = String.join(
                "\n", "##gff-version 3", "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1", ">seq1", "ACDEFG", "");
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(content.indexOf('>'), offset.getAsLong());
    }

    @Test
    void fastaMarkerAtByteZero() throws IOException {
        String content = String.join("\n", "##FASTA", ">seq1", "ACDEFG", "");
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(0L, offset.getAsLong());
    }

    @Test
    void fastaMarkerAtEofWithNoTrailingRecords() throws IOException {
        String content =
                String.join("\n", "##gff-version 3", "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1", "##FASTA", "");
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
    }

    @Test
    void windowsLineEndingsAreHandled() throws IOException {
        String content = String.join(
                "\r\n",
                "##gff-version 3",
                "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1",
                "##FASTA",
                ">seq1",
                "ACDEFG",
                "");
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
    }

    @Test
    void mixedLineEndingsAreHandled() throws IOException {
        String content = "##gff-version 3\n" + "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\r\n" + "##FASTA\n"
                + ">seq1\r\n" + "ACDEFG\n";
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
    }

    @Test
    void blankLinesBetweenAnnotationAndFastaAreSkipped() throws IOException {
        String content = String.join(
                "\n",
                "##gff-version 3",
                "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1",
                "",
                "",
                "##FASTA",
                ">seq1",
                "ACDEFG",
                "");
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
    }

    @Test
    void probeLandingMidLineIsHandledCorrectly() throws IOException {
        // A single annotation line long enough that the file midpoint necessarily
        // lands inside it, forcing the first probe to resolve forward to the next
        // line rather than starting exactly on a line boundary.
        String longAnnotationLine = "#" + "x".repeat(10_000) + "\n";
        String content = longAnnotationLine + "##FASTA\n>seq1\nACDEFG\n";
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
    }

    @Test
    void probeLandingExactlyOnLineBoundaryIsHandledCorrectly() throws IOException {
        // Two equal-length lines so the file midpoint coincides exactly with the
        // boundary between them.
        String marker = "##FASTA\n";
        String annotationLine = "#" + "x".repeat(marker.length() - 2) + "\n";
        assertEquals(annotationLine.length(), marker.length());

        String content = annotationLine + marker + ">seq1\nACDEFG\n";
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
    }

    @Test
    void hugeAnnotationSectionWithTinyFastaSectionUsesLogarithmicProbes() throws Exception {
        StringBuilder sb = new StringBuilder();
        int lineCount = 200_000;
        for (int i = 0; i < lineCount; i++) {
            sb.append("chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene").append(i).append('\n');
        }
        sb.append("##FASTA\n>seq1\nACDEFG\n");
        String content = sb.toString();
        Path file = writeFile(content);

        try (CountingSeekableByteChannel channel =
                new CountingSeekableByteChannel(Files.newByteChannel(file, StandardOpenOption.READ))) {
            OptionalLong offset = FastaSectionLocator.locate(channel);

            assertTrue(offset.isPresent());
            assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
            assertNoFullScan(channel, content.length());
        }
    }

    @Test
    void hugeFastaSectionWithTinyAnnotationSectionUsesLogarithmicProbes() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("##gff-version 3\n").append("##FASTA\n>seq1\n");
        int lineCount = 200_000;
        for (int i = 0; i < lineCount; i++) {
            sb.append("ACDEFGHIKLMNPQRSTVWY\n");
        }
        String content = sb.toString();
        Path file = writeFile(content);

        try (CountingSeekableByteChannel channel =
                new CountingSeekableByteChannel(Files.newByteChannel(file, StandardOpenOption.READ))) {
            OptionalLong offset = FastaSectionLocator.locate(channel);

            assertTrue(offset.isPresent());
            assertEquals(content.indexOf("##FASTA"), offset.getAsLong());
            assertNoFullScan(channel, content.length());
        }
    }

    @Test
    void locatedOffsetIsAlwaysAtOrBeforeFirstHeader() throws IOException {
        String content = String.join(
                "\n",
                "##gff-version 3",
                "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1",
                "##FASTA",
                ">seq1",
                "ACDEFG",
                ">seq2",
                "GHIKLM",
                "");
        Path file = writeFile(content);

        OptionalLong offset = FastaSectionLocator.locate(file);

        assertTrue(offset.isPresent());
        assertTrue(offset.getAsLong() <= content.indexOf('>'));
    }

    private static void assertNoFullScan(CountingSeekableByteChannel channel, long fileSize) {
        // A full linear scan would need to touch every byte of the file at least
        // once; the binary search must stay well under that, proving it never
        // scans a whole section.
        assertTrue(
                channel.totalBytesRead() < fileSize / 4,
                "expected far fewer bytes read than a full scan, got " + channel.totalBytesRead() + " of " + fileSize);
        assertTrue(
                channel.readCallCount() < 500,
                "expected a bounded (logarithmic) number of read calls, got " + channel.readCallCount());
    }

    /** Wraps a {@link SeekableByteChannel} to count read invocations and bytes read, for asserting no full scan occurs. */
    private static final class CountingSeekableByteChannel implements SeekableByteChannel {
        private final SeekableByteChannel delegate;
        private long totalBytesRead;
        private long readCallCount;

        CountingSeekableByteChannel(SeekableByteChannel delegate) {
            this.delegate = delegate;
        }

        long totalBytesRead() {
            return totalBytesRead;
        }

        long readCallCount() {
            return readCallCount;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            readCallCount++;
            int read = delegate.read(dst);
            if (read > 0) {
                totalBytesRead += read;
            }
            return read;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new NonWritableChannelException();
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
