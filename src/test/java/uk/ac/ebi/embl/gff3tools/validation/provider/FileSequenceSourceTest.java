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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.fastareader.SequenceFileFormat;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReader;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

class FileSequenceSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void hasSequenceReturnsTrueForPlainSequenceAnyId_noKey() {
        SequenceFormatReader mockReader = mockPlainReader();
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, null);

        assertTrue(source.hasSequence("any-id"));
        assertTrue(source.hasSequence("other-id"));
    }

    @Test
    void hasSequenceMatchesKeyForPlainSequence() {
        SequenceFormatReader mockReader = mockPlainReader();
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, "chr1");

        assertTrue(source.hasSequence("chr1"));
        assertFalse(source.hasSequence("chr2"));
    }

    @Test
    void hasSequenceChecksFastaHeaders() {
        SequenceFormatReader mockReader = mockFastaReader("seq1", "seq2");
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);

        assertTrue(source.hasSequence("seq1"));
        assertTrue(source.hasSequence("seq2"));
        assertFalse(source.hasSequence("seq3"));
    }

    @Test
    void fastaIgnoresKey() {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        // Key is ignored for FASTA — ID matching uses parsed headers
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, "irrelevant");

        assertTrue(source.hasSequence("seq1"));
        assertFalse(source.hasSequence("irrelevant"));
    }

    @Test
    void getSequenceSliceDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getSequenceSlice(0L, 1L, 9L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn("ATGAAATAA");

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals("ATGAAATAA", source.getSequenceSlice("seq1", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getSequenceSliceDelegatesForPlain() throws Exception {
        SequenceFormatReader mockReader = mockPlainReader();
        when(mockReader.getSequenceSlice(0L, 1L, 9L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn("ATGAAATAA");

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, null);
        assertEquals("ATGAAATAA", source.getSequenceSlice("any-id", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getSequenceSliceThrowsForUnknownFastaSeqId() {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> source.getSequenceSlice("unknown", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void closeClosesReader() throws Exception {
        SequenceFormatReader mockReader = mockPlainReader();
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, null);
        // Trigger initialization
        source.hasSequence("x");
        source.close();
        verify(mockReader).close();
    }

    @Test
    void canReadGzippedFastaFile() throws Exception {
        Path gzippedFasta = gzipToTempFile(">seq1 | {\"description\":\"test\"}\nACGT\n");
        FileSequenceSource source = new FileSequenceSource(gzippedFasta, SequenceFormat.fasta, null);

        assertTrue(source.hasSequence("seq1"));
        assertNotNull(source.getDecompressedPath(), "A gzipped file should be decompressed to a temp file");
        assertEquals("ACGT", source.getSequenceSlice("seq1", 1L, 4L, SequenceRangeOption.WHOLE_SEQUENCE));

        source.close();
        assertFalse(
                Files.exists(source.getDecompressedPath()), "Temporary decompressed file should be deleted on close");
    }

    @Test
    void canReadGzippedPlainSequenceFile() throws Exception {
        Path gzippedSeq = gzipToTempFile("ACGTACGT");
        FileSequenceSource source = new FileSequenceSource(gzippedSeq, SequenceFormat.plain, null);

        assertTrue(source.hasSequence("any-id"));
        assertNotNull(source.getDecompressedPath());
        assertEquals("ACGT", source.getSequenceSlice("any-id", 1L, 4L, SequenceRangeOption.WHOLE_SEQUENCE));

        source.close();
        assertFalse(Files.exists(source.getDecompressedPath()));
    }

    @Test
    void nonGzippedFileStillWorks() throws Exception {
        Path fasta = Files.writeString(tempDir.resolve("plain.fasta"), ">seq1 | {\"description\":\"test\"}\nACGT\n");
        FileSequenceSource source = new FileSequenceSource(fasta, SequenceFormat.fasta, null);

        assertTrue(source.hasSequence("seq1"));
        assertNull(source.getDecompressedPath(), "No temp file should be created for an uncompressed file");
        assertEquals("ACGT", source.getSequenceSlice("seq1", 1L, 4L, SequenceRangeOption.WHOLE_SEQUENCE));

        source.close();
    }

    @Test
    void corruptGzipDoesNotCreateDecompressedFile() throws Exception {
        Path corruptGz = tempDir.resolve("corrupt.gz");
        Files.write(corruptGz, new byte[] {0x1f, (byte) 0x8b, 0x00, 0x00});
        FileSequenceSource source = new FileSequenceSource(corruptGz, SequenceFormat.fasta, null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> source.hasSequence("seq1"));
        assertTrue(ex.getMessage().contains("Failed to open sequence file"));
        assertNull(source.getDecompressedPath(), "No decompressed path should be recorded on failure");
    }

    @Test
    void getSeqIdToHeaderReturnsPopulatedMapForFasta() {
        SequenceFormatReader mockReader = mockFastaReader("seq1", "seq2");
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);

        Map<String, FastaHeader> headerMap = source.getSeqIdToHeader();

        assertEquals(2, headerMap.size());
        assertTrue(headerMap.containsKey("seq1"));
        assertTrue(headerMap.containsKey("seq2"));
        assertEquals("test", headerMap.get("seq1").getDescription());
        assertEquals("test", headerMap.get("seq2").getDescription());
    }

    @Test
    void duplicateFastaIdsThrowsRuntimeException() {
        SequenceFormatReader mockReader = mock(SequenceFormatReader.class);
        when(mockReader.getSequenceFileFormat()).thenReturn(SequenceFileFormat.FASTA);
        when(mockReader.getOrderedIds()).thenReturn(List.of(0L, 1L));
        when(mockReader.getHeaderline(0L)).thenReturn(Optional.of(">dup_id|{\"description\":\"first\"}"));
        when(mockReader.getHeaderline(1L)).thenReturn(Optional.of(">dup_id|{\"description\":\"second\"}"));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> source.hasSequence("dup_id"));
        assertTrue(ex.getMessage().contains("Duplicate submission ID"));
    }

    @Test
    void getSequenceLengthDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getStats(0L))
                .thenReturn(new uk.ac.ebi.embl.fastareader.SequenceStats(200L, 190L, 0L, 0L, Map.of('N', 10L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(200L, source.getSequenceLength("seq1", SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getSequenceLengthDelegatesForPlain() throws Exception {
        SequenceFormatReader mockReader = mockPlainReader();
        when(mockReader.getStats(0L))
                .thenReturn(new uk.ac.ebi.embl.fastareader.SequenceStats(50L, 50L, 0L, 0L, Map.of()));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, null);
        assertEquals(50L, source.getSequenceLength("any-id", SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getSequenceStatsDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getStats(0L))
                .thenReturn(new uk.ac.ebi.embl.fastareader.SequenceStats(100L, 90L, 2L, 3L, Map.of('N', 10L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(new SequenceStats(100L, 90L, 2L, 3L, Map.of('N', 10L)), source.getSequenceStats("seq1"));
    }

    @Test
    void getGapRegionsWholeSequenceDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getGapRegions(0L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of(
                        new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(5L, 14L),
                        new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(50L, 99L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(
                List.of(new GapRegion(5L, 14L), new GapRegion(50L, 99L)),
                source.getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getGapRegionsRangeDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getGapRegions(0L, 1L, 20L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(List.of(new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(5L, 14L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(
                List.of(new GapRegion(5L, 14L)),
                source.getGapRegions("seq1", 1L, 20L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void knownSeqIdsReturnsFastaIds() {
        SequenceFormatReader mockReader = mockFastaReader("seq1", "seq2");
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(Set.of("seq1", "seq2"), source.knownSeqIds());
    }

    @Test
    void knownSeqIdsReturnsSingletonForPlainWithKey() {
        SequenceFormatReader mockReader = mockPlainReader();
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, "chr1");
        assertEquals(Set.of("chr1"), source.knownSeqIds());
    }

    @Test
    void knownSeqIdsReturnsEmptySetForPlainWithoutKey() {
        SequenceFormatReader mockReader = mockPlainReader();
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, null);
        assertTrue(source.knownSeqIds().isEmpty());
    }

    @Test
    void getSequenceSliceReaderDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        Reader expected = new StringReader("ATGAAA");
        when(mockReader.getSequenceSliceReader(
                        0L, 1L, 6L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(expected);

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertSame(expected, source.getSequenceSliceReader("seq1", 1L, 6L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    // --- WITHOUT_EDGE_N_BASES option tests ---

    @Test
    void getSequenceSlicePassesThroughWithoutEdgeNBasesOption() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getSequenceSlice(
                        0L, 1L, 9L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WITHOUT_EDGE_N_BASES))
                .thenReturn("ATGAAATAA");

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals("ATGAAATAA", source.getSequenceSlice("seq1", 1L, 9L, SequenceRangeOption.WITHOUT_EDGE_N_BASES));
    }

    @Test
    void getSequenceLengthWithoutEdgeNBasesSubtractsLeadingAndTrailingNs() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        // totalBases=100, leadingNs=5, trailingNs=3 → expected length = 92
        when(mockReader.getStats(0L))
                .thenReturn(new uk.ac.ebi.embl.fastareader.SequenceStats(100L, 88L, 5L, 3L, Map.of('N', 12L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(88L, source.getSequenceLength("seq1", SequenceRangeOption.WITHOUT_EDGE_N_BASES));
    }

    @Test
    void getSequenceLengthWholeSequenceReturnsTotalBases() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getStats(0L))
                .thenReturn(new uk.ac.ebi.embl.fastareader.SequenceStats(100L, 88L, 5L, 3L, Map.of('N', 12L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(100L, source.getSequenceLength("seq1", SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getGapRegionsPassesThroughWithoutEdgeNBasesOption() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getGapRegions(0L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WITHOUT_EDGE_N_BASES))
                .thenReturn(List.of(new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(10L, 19L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(
                List.of(new GapRegion(10L, 19L)),
                source.getGapRegions("seq1", SequenceRangeOption.WITHOUT_EDGE_N_BASES));
    }

    @Test
    void getGapRegionsRangePassesThroughWithoutEdgeNBasesOption() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getGapRegions(0L, 1L, 50L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WITHOUT_EDGE_N_BASES))
                .thenReturn(List.of(new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(10L, 19L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(
                List.of(new GapRegion(10L, 19L)),
                source.getGapRegions("seq1", 1L, 50L, SequenceRangeOption.WITHOUT_EDGE_N_BASES));
    }

    @Test
    void getSequenceSliceReaderPassesThroughWithoutEdgeNBasesOption() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        Reader expected = new StringReader("ATGAAA");
        when(mockReader.getSequenceSliceReader(
                        0L, 1L, 6L, uk.ac.ebi.embl.fastareader.SequenceRangeOption.WITHOUT_EDGE_N_BASES))
                .thenReturn(expected);

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertSame(expected, source.getSequenceSliceReader("seq1", 1L, 6L, SequenceRangeOption.WITHOUT_EDGE_N_BASES));
    }

    /** Creates a mock plain sequence reader with a single ordinal 0. */
    private SequenceFormatReader mockPlainReader() {
        SequenceFormatReader reader = mock(SequenceFormatReader.class);
        when(reader.getSequenceFileFormat()).thenReturn(SequenceFileFormat.PLAIN_SEQUENCE);
        when(reader.getOrderedIds()).thenReturn(List.of(0L));
        return reader;
    }

    /** Creates a mock FASTA reader with headers like ">seqId|{}" for each ID. */
    private SequenceFormatReader mockFastaReader(String... seqIds) {
        SequenceFormatReader reader = mock(SequenceFormatReader.class);
        when(reader.getSequenceFileFormat()).thenReturn(SequenceFileFormat.FASTA);

        List<Long> ordinals = new java.util.ArrayList<>();
        for (int i = 0; i < seqIds.length; i++) {
            long ordinal = i;
            ordinals.add(ordinal);
            when(reader.getHeaderline(ordinal))
                    .thenReturn(Optional.of(">" + seqIds[i] + "|{\"description\":\"test\"}"));
        }
        when(reader.getOrderedIds()).thenReturn(ordinals);
        return reader;
    }

    private Path gzipToTempFile(String content) throws IOException {
        Path tempFile = tempDir.resolve("test-%d.gz".formatted(System.nanoTime()));
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(Files.newOutputStream(tempFile))) {
            gzipOut.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return tempFile;
    }
}
