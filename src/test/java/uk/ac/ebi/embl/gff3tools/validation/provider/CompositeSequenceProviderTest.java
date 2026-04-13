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

import org.junit.jupiter.api.Test;
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
}
