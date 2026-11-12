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
import java.util.Random;

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

        long offset = FastaSectionLocator.locate(file);

        assertEquals(-1L, offset);
    }

    @Test
    void noFastaMarkerAndNoHeaderReturnsEmpty() throws IOException {
        Path file = writeFile(String.join(
                "\n",
                "##gff-version 3",
                "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1",
                "chr1\t.\tmRNA\t1\t1000\t.\t+\t.\tID=mrna1",
                ""));

        long offset = FastaSectionLocator.locate(file);

        assertEquals(-1L, offset);
    }

    @Test
    void bareHeaderBlockWithNoFastaMarkerResolvesToFirstHeader() throws IOException {
        String content = String.join(
                "\n", "##gff-version 3", "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1", ">seq1", "ACDEFG", "");
        Path file = writeFile(content);

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf('>'), offset);
    }

    @Test
    void fastaMarkerAtByteZero() throws IOException {
        String content = String.join("\n", "##FASTA", ">seq1", "ACDEFG", "");
        Path file = writeFile(content);

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(0L, offset);
    }

    @Test
    void fastaMarkerAtEofWithNoTrailingRecords() throws IOException {
        String content =
                String.join("\n", "##gff-version 3", "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1", "##FASTA", "");
        Path file = writeFile(content);

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
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

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
    }

    @Test
    void mixedLineEndingsAreHandled() throws IOException {
        String content = "##gff-version 3\n" + "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\r\n" + "##FASTA\n"
                + ">seq1\r\n" + "ACDEFG\n";
        Path file = writeFile(content);

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
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

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
    }

    @Test
    void probeLandingMidLineIsHandledCorrectly() throws IOException {
        // A single annotation line long enough that the file midpoint necessarily
        // lands inside it, forcing the first probe to resolve forward to the next
        // line rather than starting exactly on a line boundary.
        String longAnnotationLine = "#" + "x".repeat(10_000) + "\n";
        String content = longAnnotationLine + "##FASTA\n>seq1\nACDEFG\n";
        Path file = writeFile(content);

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
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

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
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
            long offset = FastaSectionLocator.locate(channel);

            assertTrue(offset >= 0);
            assertEquals(content.indexOf("##FASTA"), offset);
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
            long offset = FastaSectionLocator.locate(channel);

            assertTrue(offset >= 0);
            assertEquals(content.indexOf("##FASTA"), offset);
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

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertTrue(offset <= content.indexOf('>'));
    }

    // ---- locate(InputStream) tests ----

    @Test
    void streamEmptyReturnsMinusOne() throws IOException {
        long offset = FastaSectionLocator.locate(new java.io.ByteArrayInputStream(new byte[0]));
        assertEquals(-1L, offset);
    }

    @Test
    void streamNoFastaMarkerReturnsMinusOne() throws IOException {
        String content = "##gff-version 3\nchr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";
        long offset = FastaSectionLocator.locate(
                new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(-1L, offset);
    }

    @Test
    void streamFastaMarkerAtStartReturnsZero() throws IOException {
        String content = "##FASTA\n>seq1\nACDEFG\n";
        long offset = FastaSectionLocator.locate(
                new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(0L, offset);
    }

    @Test
    void streamFastaMarkerAfterAnnotationsReturnsCorrectOffset() throws IOException {
        String content = "##gff-version 3\nchr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n##FASTA\n>seq1\nACDEFG\n";
        long expected = content.indexOf("##FASTA");
        long offset = FastaSectionLocator.locate(
                new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(expected, offset);
    }

    @Test
    void streamFastaMarkerMidLineIsIgnored() throws IOException {
        String content = "##gff-version 3\nsome ##FASTA inline text\nchr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";
        long offset = FastaSectionLocator.locate(
                new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(-1L, offset);
    }

    // ---- locate(byte[]) tests ----

    @Test
    void bytesEmptyReturnsMinusOne() {
        assertEquals(-1L, FastaSectionLocator.locate(new byte[0]));
    }

    @Test
    void bytesNoFastaMarkerReturnsMinusOne() {
        String content = "##gff-version 3\nchr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n";
        assertEquals(-1L, FastaSectionLocator.locate(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void bytesFastaMarkerFoundReturnsCorrectOffset() {
        String content = "##gff-version 3\nchr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1\n##FASTA\n>seq1\nACDEFG\n";
        long expected = content.indexOf("##FASTA");
        assertEquals(expected, FastaSectionLocator.locate(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void bytesFastaMarkerOnlyReturnsZero() {
        String content = "##FASTA\n>seq1\nACDEFG\n";
        assertEquals(0L, FastaSectionLocator.locate(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void trailingBlankLinesAfterFastaSectionAreHandled() throws IOException {
        String content = String.join(
                "\n",
                "##gff-version 3",
                "s1\t.\tgene\t1\t10\t.\t+\t.\tID=g0",
                "s1\t.\tgene\t1\t10\t.\t+\t.\tID=g1",
                "s1\t.\tgene\t1\t10\t.\t+\t.\tID=g2",
                "##FASTA",
                ">s1|p0",
                "MKVLMKVLMKVL",
                "",
                "",
                "");
        Path file = writeFile(content);

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
    }

    @Test
    void blankLinesInsideFastaRecordAreHandled() throws IOException {
        String content = String.join(
                "\n",
                "##gff-version 3",
                "s1\t.\tgene\t1\t10\t.\t+\t.\tID=g0",
                "s1\t.\tgene\t1\t10\t.\t+\t.\tID=g1",
                "s1\t.\tgene\t1\t10\t.\t+\t.\tID=g2",
                "##FASTA",
                ">s1|p0",
                "MKVLMKVLMKVL",
                "",
                "",
                "",
                "MKVLMKVL",
                "");
        Path file = writeFile(content);

        long offset = FastaSectionLocator.locate(file);

        assertTrue(offset >= 0);
        assertEquals(content.indexOf("##FASTA"), offset);
    }

    @Test
    void blankLinesAnywhereInFastaSectionNeverMoveTheBoundary() throws IOException {
        Random random = new Random(1);
        for (int iteration = 0; iteration < 2_000; iteration++) {
            StringBuilder sb = new StringBuilder("##gff-version 3\n");
            int featureCount = random.nextInt(6);
            for (int i = 0; i < featureCount; i++) {
                sb.append("s1\t.\tgene\t1\t10\t.\t+\t.\tID=g").append(i).append('\n');
            }
            sb.append("##FASTA\n");
            int recordCount = 1 + random.nextInt(5);
            for (int i = 0; i < recordCount; i++) {
                sb.append(">s1|p").append(i).append('\n');
                int lineCount = 1 + random.nextInt(3);
                for (int j = 0; j < lineCount; j++) {
                    sb.append("MKVL".repeat(1 + random.nextInt(5))).append('\n');
                    if (random.nextInt(4) == 0) {
                        sb.append("\n".repeat(1 + random.nextInt(5)));
                    }
                }
            }
            String content = sb.toString();
            Path file = writeFile(content);

            long offset = FastaSectionLocator.locate(file);

            assertTrue(offset >= 0, "no FASTA section found in:\n" + content);
            assertEquals(content.indexOf("##FASTA"), offset, "wrong boundary in:\n" + content);
            Files.delete(file);
        }
    }

    @Test
    void commentLinesAnywhereInFastaSectionNeverMoveTheBoundary() throws IOException {
        // A '#' line is not valid FASTA, but if the boundary moved past it every record
        // before it would be silently dropped. It must stay on ##FASTA so FastaReader
        // reads the '#' line and rejects it. Whether a probe lands on the line depends on
        // byte positions, so many layouts are tried rather than one fixed example.
        Random random = new Random(1);
        for (int iteration = 0; iteration < 2_000; iteration++) {
            StringBuilder sb = new StringBuilder("##gff-version 3\n");
            int featureCount = random.nextInt(6);
            for (int i = 0; i < featureCount; i++) {
                sb.append("s1\t.\tgene\t1\t10\t.\t+\t.\tID=g").append(i).append('\n');
            }
            sb.append("##FASTA\n");
            int recordCount = 2 + random.nextInt(5);
            int commentedRecord = random.nextInt(recordCount - 1);
            for (int i = 0; i < recordCount; i++) {
                sb.append(">s1|p").append(i).append('\n');
                int lineCount = 1 + random.nextInt(3);
                for (int j = 0; j < lineCount; j++) {
                    sb.append("MKVL".repeat(1 + random.nextInt(5))).append('\n');
                }
                if (i == commentedRecord || random.nextInt(4) == 0) {
                    sb.append("# comment\n");
                }
            }
            String content = sb.toString();
            Path file = writeFile(content);

            long offset = FastaSectionLocator.locate(file);

            assertTrue(offset >= 0, "no FASTA section found in:\n" + content);
            assertEquals(content.indexOf("##FASTA"), offset, "wrong boundary in:\n" + content);
            Files.delete(file);
        }
    }

    @Test
    void tabbedSequenceLinesAnywhereInFastaSectionNeverMoveTheBoundary() throws IOException {
        // A tab is not valid FASTA, but if a tabbed sequence line were taken for annotation the
        // boundary would move past it and every record before it would be silently dropped. It
        // must stay on ##FASTA so FastaReader reads the line and rejects the tab. Whether a probe
        // lands on the line depends on byte positions, so many layouts are tried.
        String[] tabbedLines = {"MKVL\t", "MKVL\tMKVL", "\tMKVL", "MKV1\t", "MKV.\tL"};
        Random random = new Random(1);
        for (int iteration = 0; iteration < 2_000; iteration++) {
            StringBuilder sb = new StringBuilder("##gff-version 3\n");
            int featureCount = random.nextInt(6);
            for (int i = 0; i < featureCount; i++) {
                sb.append("s1\t.\tgene\t1\t10\t.\t+\t.\tID=g").append(i).append('\n');
            }
            sb.append("##FASTA\n");
            int recordCount = 2 + random.nextInt(5);
            int tabbedRecord = random.nextInt(recordCount - 1);
            for (int i = 0; i < recordCount; i++) {
                sb.append(">s1|p").append(i).append('\n');
                int lineCount = 1 + random.nextInt(3);
                for (int j = 0; j < lineCount; j++) {
                    sb.append("MKVL".repeat(1 + random.nextInt(5))).append('\n');
                }
                if (i == tabbedRecord || random.nextInt(4) == 0) {
                    sb.append(tabbedLines[random.nextInt(tabbedLines.length)]).append('\n');
                }
            }
            String content = sb.toString();
            Path file = writeFile(content);

            long offset = FastaSectionLocator.locate(file);

            assertTrue(offset >= 0, "no FASTA section found in:\n" + content);
            assertEquals(content.indexOf("##FASTA"), offset, "wrong boundary in:\n" + content);
            Files.delete(file);
        }
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
