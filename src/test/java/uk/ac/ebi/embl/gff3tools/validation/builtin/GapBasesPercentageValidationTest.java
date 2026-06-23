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

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

class GapBasesPercentageValidationTest {

    private static final String SEQ_ID = "chr1";

    private GapBasesPercentageValidation check;

    @BeforeEach
    void setUp() {
        check = new GapBasesPercentageValidation();
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

    /** Stats where the interior (non-edge) region is {@code lengthWithoutEdges} long with {@code interiorNs} Ns. */
    private SequenceStats stats(long lengthWithoutEdges, long interiorNs) {
        return new SequenceStats(lengthWithoutEdges, lengthWithoutEdges, 0L, 0L, Map.of('N', interiorNs));
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

    private FastaHeaderProvider chromosomeHeaderProvider() {
        FastaHeader header = new FastaHeader();
        header.setChromosomeName("1");
        return headerProviderFor(header);
    }

    private FastaHeaderProvider nonChromosomeHeaderProvider() {
        return headerProviderFor(new FastaHeader()); // all chromosome fields null
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

    // ---- ordinary (non-chromosome) sequence: 50% rule -----------------------

    @Nested
    class OrdinarySequence {

        @Test
        void belowFiftyPercentSuccess() throws Exception {
            inject(lookupReturning(stats(100, 40)), nonChromosomeHeaderProvider());
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void exactlyFiftyPercentSuccess() throws Exception {
            inject(lookupReturning(stats(100, 50)), nonChromosomeHeaderProvider());
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void aboveFiftyPercentFailure() throws Exception {
            inject(lookupReturning(stats(100, 60)), nonChromosomeHeaderProvider());
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> check.validateGapBasesPercentage(annotation(), 1));
            assertEquals("GAP_BASES_PERCENTAGE", ex.getValidationRule());
            assertTrue(ex.getMessage().contains("60.0%"));
        }

        @Test
        void providerWithoutHeaderForSeqIdUsesStrictRuleFailure() throws Exception {
            // Provider present but no header for this seqId -> strict 50% rule still applies.
            inject(lookupReturning(stats(100, 60)), emptyHeaderProvider());
            assertThrows(ValidationException.class, () -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void countsOnlyInteriorNs() throws Exception {
            // baseCount N = 100, edges = 30 + 20 -> interior Ns = 50 over an interior length of 100 -> 50% -> pass.
            SequenceStats stats = new SequenceStats(160, 100, 30L, 20L, Map.of('N', 100L));
            inject(lookupReturning(stats), nonChromosomeHeaderProvider());
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }
    }

    // ---- chromosome / no-provider: 100% rule --------------------------------

    @Nested
    class LooseRule {

        @Test
        void chromosomeAboveFiftyPercentSuccess() throws Exception {
            inject(lookupReturning(stats(100, 90)), chromosomeHeaderProvider());
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void chromosomeAllNsFailure() throws Exception {
            inject(lookupReturning(stats(100, 100)), chromosomeHeaderProvider());
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> check.validateGapBasesPercentage(annotation(), 1));
            assertEquals("GAP_BASES_PERCENTAGE", ex.getValidationRule());
            assertTrue(ex.getMessage().contains("entirely of N bases"));
        }

        @Test
        void noProviderAboveFiftyPercentSuccess() throws Exception {
            inject(lookupReturning(stats(100, 99)), null);
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void noProviderAllNsFailure() throws Exception {
            inject(lookupReturning(stats(100, 100)), null);
            assertThrows(ValidationException.class, () -> check.validateGapBasesPercentage(annotation(), 1));
        }
    }

    // ---- skip / error handling ----------------------------------------------

    @Nested
    class SkipAndErrors {

        @Test
        void noLookupSkipped() {
            inject(null, chromosomeHeaderProvider());
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void zeroInteriorLengthSkipped() throws Exception {
            inject(lookupReturning(stats(0, 0)), nonChromosomeHeaderProvider());
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void zeroInteriorNsSkipped() throws Exception {
            inject(lookupReturning(stats(100, 0)), nonChromosomeHeaderProvider());
            assertDoesNotThrow(() -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void nullStatsThrowsIllegalState() throws Exception {
            inject(lookupReturning(null), nonChromosomeHeaderProvider());
            assertThrows(IllegalStateException.class, () -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void lookupFailureThrowsIllegalState() throws Exception {
            SequenceLookup lookup = mock(SequenceLookup.class);
            when(lookup.getSequenceStats(SEQ_ID)).thenThrow(new RuntimeException("boom"));
            inject(lookup, nonChromosomeHeaderProvider());
            assertThrows(IllegalStateException.class, () -> check.validateGapBasesPercentage(annotation(), 1));
        }

        @Test
        void negativeCountsThrowIllegalState() throws Exception {
            // baseCount N (10) minus edges (30 + 0) -> negative interior N count.
            SequenceStats stats = new SequenceStats(100, 70, 30L, 0L, Map.of('N', 10L));
            inject(lookupReturning(stats), nonChromosomeHeaderProvider());
            assertThrows(IllegalStateException.class, () -> check.validateGapBasesPercentage(annotation(), 1));
        }
    }
}
