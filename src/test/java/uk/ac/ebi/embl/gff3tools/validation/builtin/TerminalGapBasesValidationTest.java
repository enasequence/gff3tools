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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.ebi.embl.gff3tools.validation.builtin.TerminalGapBasesValidation.NO_TERMINAL_GAPS_RULE;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class TerminalGapBasesValidationTest {

    private static final String SEQ_ID = "chr1";

    private TerminalGapBasesValidation check;

    @BeforeEach
    void setUp() {
        check = new TerminalGapBasesValidation();
    }

    // ---- context wiring ------------------------------------------------------

    private void inject(SequenceLookup lookup, FastaHeaderProvider headerProvider) {
        ValidationContext context = TestUtils.createTestContext();
        if (lookup != null) {
            context.register(SequenceLookup.class, new ContextProvider<>() {
                @Override
                public SequenceLookup get(ValidationContext ctx) {
                    return lookup;
                }

                @Override
                public Class<SequenceLookup> type() {
                    return SequenceLookup.class;
                }
            });
        }
        if (headerProvider != null) {
            context.register(FastaHeaderProvider.class, headerProvider);
        }
        TestUtils.injectContext(check, context);
    }

    // ---- stat / lookup builders ---------------------------------------------

    /** Stats for a 100-base sequence with {@code leadingNs} Ns at the start and {@code trailingNs} at the end. */
    private SequenceStats stats(long leadingNs, long trailingNs) {
        long edgeNs = leadingNs + trailingNs;
        return new SequenceStats(100, 100 - edgeNs, leadingNs, trailingNs, Map.of('N', edgeNs));
    }

    private SequenceLookup lookupReturning(SequenceStats stats) throws Exception {
        SequenceLookup lookup = mock(SequenceLookup.class);
        when(lookup.getSequenceStats(SEQ_ID)).thenReturn(stats);
        return lookup;
    }

    // ---- fasta header builders ----------------------------------------------

    private FastaHeaderProvider headerProviderFor(FastaHeader header) {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(seqId -> SEQ_ID.equals(seqId) ? Optional.of(header) : Optional.empty());
        return provider;
    }

    private FastaHeaderProvider topologyHeaderProvider(String topology) {
        FastaHeader header = new FastaHeader();
        header.setTopology(topology);
        return headerProviderFor(header);
    }

    private FastaHeaderProvider emptyHeaderProvider() {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(seqId -> Optional.empty());
        return provider;
    }

    private GFF3Annotation annotation() {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(SEQ_ID, Optional.empty(), 1L, 100L));
        return annotation;
    }

    // ---- linear sequences: terminal Ns are reported -------------------------

    @Nested
    class LinearSequence {

        @Test
        void noTerminalNsSuccess() throws Exception {
            inject(lookupReturning(stats(0, 0)), topologyHeaderProvider("linear"));
            assertDoesNotThrow(() -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void interiorNsOnlySuccess() throws Exception {
            // 40 Ns in the middle of the sequence, none at either end.
            SequenceStats interiorNs = new SequenceStats(100, 60, 0L, 0L, Map.of('N', 40L));
            inject(lookupReturning(interiorNs), topologyHeaderProvider("linear"));
            assertDoesNotThrow(() -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void leadingNsFailure() throws Exception {
            inject(lookupReturning(stats(5, 0)), topologyHeaderProvider("linear"));
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 1));
            assertEquals(NO_TERMINAL_GAPS_RULE, ex.getValidationRule());
            assertTrue(ex.getMessage().contains(SEQ_ID));
            assertTrue(ex.getMessage().contains("5 leading, 0 trailing"));
        }

        @Test
        void trailingNsFailure() throws Exception {
            inject(lookupReturning(stats(0, 7)), topologyHeaderProvider("linear"));
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 1));
            assertEquals(NO_TERMINAL_GAPS_RULE, ex.getValidationRule());
            assertTrue(ex.getMessage().contains("0 leading, 7 trailing"));
        }

        @Test
        void leadingAndTrailingNsFailure() throws Exception {
            inject(lookupReturning(stats(3, 9)), topologyHeaderProvider("linear"));
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 1));
            assertTrue(ex.getMessage().contains("3 leading, 9 trailing"));
        }

        @Test
        void reportsTheGivenLineNumber() throws Exception {
            inject(lookupReturning(stats(1, 1)), topologyHeaderProvider("linear"));
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 42));
            assertEquals(42, ex.getLine());
        }
    }

    // ---- circular sequences: skipped ----------------------------------------

    @Nested
    class CircularSequence {

        @ParameterizedTest
        @ValueSource(strings = {"circular", "CIRCULAR", "Circular", "  circular  "})
        void terminalNsSkippedForCircularTopology(String topology) throws Exception {
            inject(lookupReturning(stats(5, 5)), topologyHeaderProvider(topology));
            assertDoesNotThrow(() -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void unrecognisedTopologyTreatedAsLinearFailure() throws Exception {
            inject(lookupReturning(stats(5, 5)), topologyHeaderProvider("round"));
            assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void nullTopologyTreatedAsLinearFailure() throws Exception {
            inject(lookupReturning(stats(5, 5)), topologyHeaderProvider(null));
            assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void noHeaderForSeqIdTreatedAsLinearFailure() throws Exception {
            inject(lookupReturning(stats(5, 5)), emptyHeaderProvider());
            assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void noHeaderProviderTreatedAsLinearFailure() throws Exception {
            inject(lookupReturning(stats(5, 5)), null);
            assertThrows(ValidationException.class, () -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void circularSequenceIsNotLookedUp() throws Exception {
            SequenceLookup lookup = lookupReturning(stats(5, 5));
            inject(lookup, topologyHeaderProvider("circular"));
            check.validateTerminalGapBases(annotation(), 1);
            verify(lookup, never()).getSequenceStats(anyString());
        }
    }

    // ---- skip / error handling ----------------------------------------------

    @Nested
    class SkipAndErrors {

        @Test
        void noLookupSkipped() {
            inject(null, topologyHeaderProvider("linear"));
            assertDoesNotThrow(() -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void nullStatsThrowsIllegalState() throws Exception {
            inject(lookupReturning(null), topologyHeaderProvider("linear"));
            assertThrows(IllegalStateException.class, () -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void lookupFailureThrowsIllegalState() throws Exception {
            SequenceLookup lookup = mock(SequenceLookup.class);
            when(lookup.getSequenceStats(SEQ_ID)).thenThrow(new RuntimeException("boom"));
            inject(lookup, topologyHeaderProvider("linear"));
            assertThrows(IllegalStateException.class, () -> check.validateTerminalGapBases(annotation(), 1));
        }

        @Test
        void negativeCountsThrowIllegalState() throws Exception {
            SequenceStats negativeEdges = new SequenceStats(100, 100, -1L, 0L, Map.of('N', 0L));
            inject(lookupReturning(negativeEdges), topologyHeaderProvider("linear"));
            assertThrows(IllegalStateException.class, () -> check.validateTerminalGapBases(annotation(), 1));
        }
    }
}
