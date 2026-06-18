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
import uk.ac.ebi.embl.gff3tools.sequence.GapRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
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
        assertEquals("ATGAAA", lookup.getSequenceSlice("seq1", 1L, 6L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void chainFirstSourceMissesSecondSourceHits() throws Exception {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));
        provider.addSource(stubSource("seq2", "TTT"));

        SequenceLookup lookup = provider.get(new ValidationContext());
        assertEquals("TTT", lookup.getSequenceSlice("seq2", 1L, 3L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void throwsWhenNoSourceMatches() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));

        SequenceLookup lookup = provider.get(new ValidationContext());
        assertThrows(
                IllegalArgumentException.class,
                () -> lookup.getSequenceSlice("unknown", 1L, 3L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void closeClosesSources() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        boolean[] closed = {false, false};
        provider.addSource(new StubSource("any") {
            @Override
            public void close() {
                closed[0] = true;
            }
        });
        provider.addSource(new StubSource("any") {
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
        assertSame(provider.get(ctx), provider.get(ctx));
    }

    @Test
    void addSourceInvalidatesCache() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));

        ValidationContext ctx = new ValidationContext();
        SequenceLookup first = provider.get(ctx);

        provider.addSource(stubSource("seq2", "TTT"));
        assertNotSame(first, provider.get(ctx));
    }

    // --- delegation tests ---

    @Test
    void getSequenceLengthDelegatesToSource() throws Exception {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public long getSequenceLength(String id, SequenceRangeOption o) {
                return 300L;
            }
        });

        assertEquals(
                300L,
                provider.get(new ValidationContext()).getSequenceLength("seq1", SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getSequenceStatsDelegatesToSource() throws Exception {
        SequenceStats stats = new SequenceStats(100L, 90L, 0L, 0L, Map.of('N', 10L));
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public SequenceStats getSequenceStats(String id) {
                return stats;
            }
        });

        assertSame(stats, provider.get(new ValidationContext()).getSequenceStats("seq1"));
    }

    @Test
    void getGapRegionsWholeSequenceDelegatesToSource() throws Exception {
        List<GapRegion> gaps = List.of(new GapRegion(5L, 14L));
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public List<GapRegion> getGapRegions(String id, SequenceRangeOption o) {
                return gaps;
            }
        });

        assertEquals(
                gaps, provider.get(new ValidationContext()).getGapRegions("seq1", SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void getGapRegionsRangeDelegatesToSource() throws Exception {
        List<GapRegion> gaps = List.of(new GapRegion(5L, 14L));
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public List<GapRegion> getGapRegions(String id, long f, long t, SequenceRangeOption o) {
                return gaps;
            }
        });

        assertEquals(
                gaps,
                provider.get(new ValidationContext())
                        .getGapRegions("seq1", 1L, 20L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void knownSeqIdsAggregatesAcrossSources() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public Set<String> knownSeqIds() {
                return Set.of("seq1");
            }
        });
        provider.addSource(new StubSource("seq2") {
            @Override
            public Set<String> knownSeqIds() {
                return Set.of("seq2");
            }
        });

        assertEquals(
                Set.of("seq1", "seq2"), provider.get(new ValidationContext()).knownSeqIds());
    }

    @Test
    void getSequenceSliceReaderDelegatesToSource() throws Exception {
        Reader expected = new StringReader("ATGAAA");
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public Reader getSequenceSliceReader(String id, long f, long t, SequenceRangeOption o) {
                return expected;
            }
        });

        assertSame(
                expected,
                provider.get(new ValidationContext())
                        .getSequenceSliceReader("seq1", 1L, 6L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    // --- unknown seqId tests ---

    @Test
    void newMethodsThrowForUnknownSeqId() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(stubSource("seq1", "AAA"));

        SequenceLookup lookup = provider.get(new ValidationContext());
        assertThrows(
                IllegalArgumentException.class,
                () -> lookup.getSequenceLength("unknown", SequenceRangeOption.WHOLE_SEQUENCE));
        assertThrows(IllegalArgumentException.class, () -> lookup.getSequenceStats("unknown"));
        assertThrows(
                IllegalArgumentException.class,
                () -> lookup.getGapRegions("unknown", SequenceRangeOption.WHOLE_SEQUENCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> lookup.getGapRegions("unknown", 1L, 20L, SequenceRangeOption.WHOLE_SEQUENCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> lookup.getSequenceSliceReader("unknown", 1L, 6L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    // --- option forwarding tests ---

    @Test
    void getSequenceSliceForwardsOptionToSource() throws Exception {
        SequenceRangeOption[] captured = {null};
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public String getSequenceSlice(String id, long f, long t, SequenceRangeOption option) {
                captured[0] = option;
                return "";
            }
        });

        provider.get(new ValidationContext())
                .getSequenceSlice("seq1", 1L, 5L, SequenceRangeOption.WITHOUT_EDGE_N_BASES);

        assertEquals(SequenceRangeOption.WITHOUT_EDGE_N_BASES, captured[0]);
    }

    @Test
    void getSequenceLengthForwardsOptionToSource() throws Exception {
        SequenceRangeOption[] captured = {null};
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public long getSequenceLength(String id, SequenceRangeOption option) {
                captured[0] = option;
                return 0L;
            }
        });

        provider.get(new ValidationContext()).getSequenceLength("seq1", SequenceRangeOption.WITHOUT_EDGE_N_BASES);

        assertEquals(SequenceRangeOption.WITHOUT_EDGE_N_BASES, captured[0]);
    }

    @Test
    void getGapRegionsForwardsOptionToSource() throws Exception {
        SequenceRangeOption[] captured = {null};
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public List<GapRegion> getGapRegions(String id, SequenceRangeOption option) {
                captured[0] = option;
                return List.of();
            }
        });

        provider.get(new ValidationContext()).getGapRegions("seq1", SequenceRangeOption.WITHOUT_EDGE_N_BASES);

        assertEquals(SequenceRangeOption.WITHOUT_EDGE_N_BASES, captured[0]);
    }

    @Test
    void getGapRegionsRangeForwardsOptionToSource() throws Exception {
        SequenceRangeOption[] captured = {null};
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public List<GapRegion> getGapRegions(String id, long f, long t, SequenceRangeOption option) {
                captured[0] = option;
                return List.of();
            }
        });

        provider.get(new ValidationContext()).getGapRegions("seq1", 1L, 20L, SequenceRangeOption.WITHOUT_EDGE_N_BASES);

        assertEquals(SequenceRangeOption.WITHOUT_EDGE_N_BASES, captured[0]);
    }

    @Test
    void getSequenceSliceReaderForwardsOptionToSource() throws Exception {
        SequenceRangeOption[] captured = {null};
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new StubSource("seq1") {
            @Override
            public Reader getSequenceSliceReader(String id, long f, long t, SequenceRangeOption option) {
                captured[0] = option;
                return new StringReader("");
            }
        });

        provider.get(new ValidationContext())
                .getSequenceSliceReader("seq1", 1L, 6L, SequenceRangeOption.WITHOUT_EDGE_N_BASES);

        assertEquals(SequenceRangeOption.WITHOUT_EDGE_N_BASES, captured[0]);
    }

    // --- helpers ---

    private SequenceSource stubSource(String seqId, String sequence) {
        return new StubSource(seqId) {
            @Override
            public String getSequenceSlice(String id, long f, long t, SequenceRangeOption o) {
                return sequence;
            }
        };
    }

    /**
     * Base stub that routes hasSequence by seqId and throws UnsupportedOperationException
     * for every other method. Tests override only what they need.
     */
    private abstract static class StubSource implements SequenceSource {

        private final String seqId;

        StubSource(String seqId) {
            this.seqId = seqId;
        }

        @Override
        public boolean hasSequence(String id) {
            return seqId.equals(id);
        }

        @Override
        public String getSequenceSlice(String id, long f, long t, SequenceRangeOption o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getSequenceLength(String id, SequenceRangeOption o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SequenceStats getSequenceStats(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GapRegion> getGapRegions(String id, SequenceRangeOption o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<GapRegion> getGapRegions(String id, long f, long t, SequenceRangeOption o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> knownSeqIds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Reader getSequenceSliceReader(String id, long f, long t, SequenceRangeOption o) {
            throw new UnsupportedOperationException();
        }
    }
}
