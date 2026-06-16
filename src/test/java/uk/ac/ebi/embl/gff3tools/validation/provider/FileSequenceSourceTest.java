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

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceFileFormat;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReader;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.sequence.GapRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

class FileSequenceSourceTest {

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
        when(mockReader.getSequenceSlice(0L, 1L, 9L)).thenReturn("ATGAAATAA");

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals("ATGAAATAA", source.getSequenceSlice("seq1", 1L, 9L));
    }

    @Test
    void getSequenceSliceDelegatesForPlain() throws Exception {
        SequenceFormatReader mockReader = mockPlainReader();
        when(mockReader.getSequenceSlice(0L, 1L, 9L)).thenReturn("ATGAAATAA");

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, null);
        assertEquals("ATGAAATAA", source.getSequenceSlice("any-id", 1L, 9L));
    }

    @Test
    void getSequenceSliceThrowsForUnknownFastaSeqId() {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);

        assertThrows(IllegalArgumentException.class, () -> source.getSequenceSlice("unknown", 1L, 9L));
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

    // --- new method tests ---

    @Test
    void getSequenceLengthDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getStats(0L))
                .thenReturn(new uk.ac.ebi.embl.fastareader.SequenceStats(200L, 190L, 0L, 0L, Map.of('N', 10L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(200L, source.getSequenceLength("seq1"));
    }

    @Test
    void getSequenceLengthDelegatesForPlain() throws Exception {
        SequenceFormatReader mockReader = mockPlainReader();
        when(mockReader.getStats(0L))
                .thenReturn(new uk.ac.ebi.embl.fastareader.SequenceStats(50L, 50L, 0L, 0L, Map.of()));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.plain, null);
        assertEquals(50L, source.getSequenceLength("any-id"));
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
        when(mockReader.getGapRegions(0L))
                .thenReturn(List.of(
                        new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(5L, 14L),
                        new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(50L, 99L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(List.of(new GapRegion(5L, 14L), new GapRegion(50L, 99L)), source.getGapRegions("seq1"));
    }

    @Test
    void getGapRegionsRangeDelegatesForFasta() throws Exception {
        SequenceFormatReader mockReader = mockFastaReader("seq1");
        when(mockReader.getGapRegions(0L, 1L, 20L))
                .thenReturn(List.of(new uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion(5L, 14L)));

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertEquals(List.of(new GapRegion(5L, 14L)), source.getGapRegions("seq1", 1L, 20L));
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
        when(mockReader.getSequenceSliceReader(0L, 1L, 6L)).thenReturn(expected);

        FileSequenceSource source = new FileSequenceSource(mockReader, SequenceFormat.fasta, null);
        assertSame(expected, source.getSequenceSliceReader("seq1", 1L, 6L));
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
}
