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

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class CompositeSequenceProviderTest {

    @Test
    void getReturnsNullWhenNoSources() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        assertNull(provider.get(new ValidationContext()));
        assertFalse(provider.hasSources());
    }

    @Test
    void getReturnsLookupWhenSourceAdded() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "ATGAAA"));

        assertTrue(provider.hasSources());
        assertNotNull(provider.get(new ValidationContext()));
    }

    @Test
    void typeReturnsSequenceLookupClass() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        assertEquals(SequenceLookup.class, provider.type());
    }

    @Test
    void singleSourceDelegatesCorrectly() throws Exception {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "ATGAAA"));

        SequenceLookup lookup = provider.get(new ValidationContext());
        assertEquals("ATGAAA", lookup.getSequenceSlice("seq1", 1L, 6L));
    }

    @Test
    void chainFirstSourceMissesSecondSourceHits() throws Exception {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));
        provider.addSource(stubSource("seq2", "TTT"));

        SequenceLookup lookup = provider.get(new ValidationContext());
        assertEquals("TTT", lookup.getSequenceSlice("seq2", 1L, 3L));
    }

    @Test
    void throwsWhenNoSourceMatches() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));

        SequenceLookup lookup = provider.get(new ValidationContext());
        assertThrows(IllegalArgumentException.class, () -> lookup.getSequenceSlice("unknown", 1L, 3L));
    }

    @Test
    void closeClosesSources() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        boolean[] closed = {false, false};
        provider.addSource(new SequenceSource() {
            @Override
            public boolean hasSequence(String seqId) {
                return false;
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase) {
                return "";
            }

            @Override
            public void close() {
                closed[0] = true;
            }
        });
        provider.addSource(new SequenceSource() {
            @Override
            public boolean hasSequence(String seqId) {
                return false;
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase) {
                return "";
            }

            @Override
            public void close() {
                closed[1] = true;
            }
        });

        provider.close();
        assertTrue(closed[0]);
        assertTrue(closed[1]);
    }

    @Test
    void getCachesSameInstance() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));

        ValidationContext ctx = new ValidationContext();
        SequenceLookup first = provider.get(ctx);
        SequenceLookup second = provider.get(ctx);
        assertSame(first, second);
    }

    @Test
    void addSourceInvalidatesCache() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));

        ValidationContext ctx = new ValidationContext();
        SequenceLookup first = provider.get(ctx);

        provider.addSource(stubSource("seq2", "TTT"));
        SequenceLookup second = provider.get(ctx);
        assertNotSame(first, second);
    }

    // --- new method tests ---

    @Test
    void getSequenceLengthDelegatesToSource() throws Exception {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(fullStubSource("seq1", 300L));

        assertEquals(300L, provider.get(new ValidationContext()).getSequenceLength("seq1"));
    }

    @Test
    void getSequenceStatsDelegatesToSource() throws Exception {
        SequenceStats stats = new SequenceStats(100L, 90L, 0L, 0L, Map.of('N', 10L));
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(fullStubSource("seq1", stats));

        assertSame(stats, provider.get(new ValidationContext()).getSequenceStats("seq1"));
    }

    @Test
    void getGapRegionsWholeSequenceDelegatesToSource() throws Exception {
        List<GapRegion> gaps = List.of(new GapRegion(5L, 14L));
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(fullStubSourceWithGaps("seq1", gaps));

        assertEquals(gaps, provider.get(new ValidationContext()).getGapRegions("seq1"));
    }

    @Test
    void getGapRegionsRangeDelegatesToSource() throws Exception {
        List<GapRegion> gaps = List.of(new GapRegion(5L, 14L));
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(fullStubSourceWithGaps("seq1", gaps));

        assertEquals(gaps, provider.get(new ValidationContext()).getGapRegions("seq1", 1L, 20L));
    }

    @Test
    void knownSeqIdsAggregatesAcrossSources() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(fullStubSource("seq1", 100L));
        provider.addSource(fullStubSource("seq2", 200L));

        assertEquals(Set.of("seq1", "seq2"), provider.get(new ValidationContext()).knownSeqIds());
    }

    @Test
    void getSequenceSliceReaderDelegatesToSource() throws Exception {
        Reader expected = new StringReader("ATGAAA");
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(fullStubSourceWithReader("seq1", expected));

        assertSame(expected, provider.get(new ValidationContext()).getSequenceSliceReader("seq1", 1L, 6L));
    }

    @Test
    void newMethodsThrowForUnknownSeqId() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));

        SequenceLookup lookup = provider.get(new ValidationContext());
        assertThrows(IllegalArgumentException.class, () -> lookup.getSequenceLength("unknown"));
        assertThrows(IllegalArgumentException.class, () -> lookup.getSequenceStats("unknown"));
        assertThrows(IllegalArgumentException.class, () -> lookup.getGapRegions("unknown"));
    }

    private SequenceSource stubSource(String seqId, String sequence) {
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String id) {
                return seqId.equals(id);
            }

            @Override
            public String getSequenceSlice(String id, long fromBase, long toBase) {
                return sequence;
            }
        };
    }

    private SequenceSource fullStubSource(String seqId, long length) {
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String id) { return seqId.equals(id); }
            @Override
            public String getSequenceSlice(String id, long f, long t) { return ""; }
            @Override
            public long getSequenceLength(String id) { return length; }
            @Override
            public SequenceStats getSequenceStats(String id) { return null; }
            @Override
            public Set<String> knownSeqIds() { return Set.of(seqId); }
        };
    }

    private SequenceSource fullStubSource(String seqId, SequenceStats stats) {
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String id) { return seqId.equals(id); }
            @Override
            public String getSequenceSlice(String id, long f, long t) { return ""; }
            @Override
            public long getSequenceLength(String id) { return stats.totalBases(); }
            @Override
            public SequenceStats getSequenceStats(String id) { return stats; }
            @Override
            public Set<String> knownSeqIds() { return Set.of(seqId); }
        };
    }

    private SequenceSource fullStubSourceWithGaps(String seqId, List<GapRegion> gaps) {
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String id) { return seqId.equals(id); }
            @Override
            public String getSequenceSlice(String id, long f, long t) { return ""; }
            @Override
            public List<GapRegion> getGapRegions(String id) { return gaps; }
            @Override
            public List<GapRegion> getGapRegions(String id, long f, long t) { return gaps; }
            @Override
            public Set<String> knownSeqIds() { return Set.of(seqId); }
        };
    }

    private SequenceSource fullStubSourceWithReader(String seqId, Reader reader) {
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String id) { return seqId.equals(id); }
            @Override
            public String getSequenceSlice(String id, long f, long t) { return ""; }
            @Override
            public Reader getSequenceSliceReader(String id, long f, long t) { return reader; }
            @Override
            public Set<String> knownSeqIds() { return Set.of(seqId); }
        };
    }
}
