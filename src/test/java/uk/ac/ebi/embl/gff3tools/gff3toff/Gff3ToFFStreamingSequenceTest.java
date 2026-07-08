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
package uk.ac.ebi.embl.gff3tools.gff3toff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

class Gff3ToFFStreamingSequenceTest {

    @TempDir
    Path tempDir;

    // Mixed case, plus non-acgt bases (n/N) to exercise Decisions 1, 5 and OQ-2.
    private static final String BASES = "aCgTnACGTacgtN";

    private GFF3FileReader mockReader() {
        GFF3FileReader reader = mock(GFF3FileReader.class);
        when(reader.getTranslationOffsetMap()).thenReturn(Map.of());
        return reader;
    }

    private GFF3Annotation annotation(long start, long end) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion("chr1", Optional.empty(), start, end));
        return annotation;
    }

    private SequenceLookup fastaLookup() throws IOException {
        Path fasta = tempDir.resolve("seq.fasta");
        Files.writeString(fasta, ">chr1 | {\"molecule_type\":\"dna\", \"topology\":\"linear\"}\n" + BASES + "\n");
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new FileSequenceSource(fasta, SequenceFormat.fasta, null));
        return provider.get(new ValidationContext());
    }

    @Test
    void streamingOutputIsByteIdenticalToBytePath() throws Exception {
        SequenceLookup lookup = fastaLookup();
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), new ValidationContext(), lookup);

        Entry streamingEntry = mapper.mapGFF3ToEntry(annotation(1, BASES.length()));
        StreamingSequenceContext ctx = mapper.getStreamingContext();
        assertNotNull(ctx, "whole-sequence region must take the streaming path");
        assertNull(streamingEntry.getSequence().getSequenceByte(), "streaming path must not materialise byte[]");
        assertEquals(BASES.length(), streamingEntry.getSequence().getLength());

        StringWriter streamingOut = new StringWriter();
        StreamingEmblEntryWriter streamingWriter = new StreamingEmblEntryWriter(streamingEntry, lookup, ctx);
        streamingWriter.setShowAcStartLine(false);
        streamingWriter.write(streamingOut);

        // Reference: reproduce the current byte[] path on an otherwise identical entry.
        Entry byteEntry = mapper.mapGFF3ToEntry(annotation(1, BASES.length()));
        String slice = lookup.getSequenceSlice("chr1", 1, BASES.length(), SequenceRangeOption.WHOLE_SEQUENCE);
        byte[] bytes = new byte[slice.length()];
        for (int i = 0; i < slice.length(); i++) {
            bytes[i] = (byte) Character.toLowerCase(slice.charAt(i));
        }
        byteEntry.getSequence().setSequence(ByteBuffer.wrap(bytes));
        byteEntry.getSequence().setLength(bytes.length);

        StringWriter byteOut = new StringWriter();
        EmblEntryWriter byteWriter = new EmblEntryWriter(byteEntry);
        byteWriter.setShowAcStartLine(false);
        byteWriter.write(byteOut);

        assertEquals(byteOut.toString(), streamingOut.toString());
        assertTrue(streamingOut.toString().contains("SQ   "), "streaming output should contain an SQ block");
    }

    @Test
    void nullLookupEmitsNoSqBlock() throws Exception {
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), new ValidationContext(), null);
        Entry entry = mapper.mapGFF3ToEntry(annotation(1, BASES.length()));

        assertNull(mapper.getStreamingContext());
        assertNull(entry.getSequence().getSequenceByte());

        StringWriter out = new StringWriter();
        EmblEntryWriter writer = new EmblEntryWriter(entry);
        writer.setShowAcStartLine(false);
        writer.write(out);
        assertFalse(out.toString().contains("SQ   "), "no sequence source should emit no SQ block");
    }

    @Test
    void subRangeRegionUsesBytePath() throws Exception {
        SequenceLookup lookup = fastaLookup();
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), new ValidationContext(), lookup);

        Entry entry = mapper.mapGFF3ToEntry(annotation(1, BASES.length() - 1));

        assertNull(mapper.getStreamingContext(), "sub-range must fall back to the byte[] path");
        assertNotNull(entry.getSequence().getSequenceByte());

        StringWriter out = new StringWriter();
        EmblEntryWriter writer = new EmblEntryWriter(entry);
        writer.setShowAcStartLine(false);
        writer.write(out);
        assertTrue(out.toString().contains("SQ   "));
    }

    @Test
    void readerIsClosedExactlyOnce() throws Exception {
        SequenceLookup lookup = fastaLookup();
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), new ValidationContext(), lookup);
        Entry entry = mapper.mapGFF3ToEntry(annotation(1, BASES.length()));
        StreamingSequenceContext ctx = mapper.getStreamingContext();
        assertNotNull(ctx);

        AtomicInteger closeCount = new AtomicInteger();
        SequenceLookup trackingLookup = new SequenceLookup() {
            @Override
            public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                return new FilterReader(new StringReader(BASES.toUpperCase())) {
                    @Override
                    public void close() throws IOException {
                        closeCount.incrementAndGet();
                        super.close();
                    }
                };
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getSequenceLength(String seqId, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SequenceStats getSequenceStats(String seqId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<String> knownSeqIds() {
                return Set.of("chr1");
            }
        };

        StringWriter out = new StringWriter();
        StreamingEmblEntryWriter writer = new StreamingEmblEntryWriter(entry, trackingLookup, ctx);
        writer.setShowAcStartLine(false);
        writer.write(out);

        assertEquals(1, closeCount.get(), "streamed reader must be closed exactly once");
    }
}
