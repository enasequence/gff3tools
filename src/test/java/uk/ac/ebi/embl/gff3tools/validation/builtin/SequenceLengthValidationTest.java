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
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

public class SequenceLengthValidationTest {

    private static final String SEQ_ID = "chr1";

    private SequenceLengthValidation check;

    @BeforeEach
    void setUp() {
        check = new SequenceLengthValidation();
    }

    private void injectLookup(SequenceLookup mockLookup) {
        ValidationContext context = TestUtils.createTestContext();
        context.register(SequenceLookup.class, new ContextProvider<>() {
            @Override
            public SequenceLookup get(ValidationContext ctx) {
                return mockLookup;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
        TestUtils.injectContext(check, context);
    }

    private void injectLookupReturning(String seqId, long len) throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(seqId, SequenceRangeOption.WITHOUT_EDGE_N_BASES))
                .thenReturn(len);
        injectLookup(mockLookup);
    }

    private void injectLookupReturning(String seqId, long len, AnalysisType analysisType) throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(seqId, SequenceRangeOption.WITHOUT_EDGE_N_BASES))
                .thenReturn(len);
        ValidationContext context = TestUtils.createTestContext();
        context.register(SequenceLookup.class, new ContextProvider<>() {
            @Override
            public SequenceLookup get(ValidationContext ctx) {
                return mockLookup;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
        context.register(AnalysisContext.class, new ContextProvider<>() {
            @Override
            public AnalysisContext get(ValidationContext ctx) {
                return new AnalysisContext(analysisType, 10);
            }

            @Override
            public Class<AnalysisContext> type() {
                return AnalysisContext.class;
            }
        });
        TestUtils.injectContext(check, context);
    }

    private void injectNoLookup() {
        TestUtils.injectContext(check);
    }

    private GFF3Annotation annotationWithSequenceRegion(long regionStart, long regionEnd) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(SEQ_ID, Optional.empty(), regionStart, regionEnd));
        return annotation;
    }

    private GFF3Annotation annotationWithFeature(String featureName) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(SEQ_ID, Optional.empty(), 1L, 50L));
        annotation.addFeature(TestUtils.createGFF3Feature(featureName, SEQ_ID, 1L, 50L, Map.of()));
        return annotation;
    }

    @Nested
    class ValidateSequenceRegionAgainstSequence {

        @Test
        void boundsSuccess() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen);
            assertDoesNotThrow(
                    () -> check.validateSequenceRegionAgainstSequence(annotationWithSequenceRegion(1L, seqLen), 1));
        }

        @Test
        void startBelowOneFailure() throws Exception {
            injectLookupReturning(SEQ_ID, 1000L);
            ValidationException ex = assertThrows(
                    ValidationException.class,
                    () -> check.validateSequenceRegionAgainstSequence(annotationWithSequenceRegion(0L, 500L), 1));
            assertTrue(ex.getMessage().contains("start position"));
        }

        @Test
        void startAboveOneFailure() throws Exception {
            injectLookupReturning(SEQ_ID, 1000L);
            ValidationException ex = assertThrows(
                    ValidationException.class,
                    () -> check.validateSequenceRegionAgainstSequence(annotationWithSequenceRegion(2L, 1000L), 1));
            assertTrue(ex.getMessage().contains("start position"));
        }

        @Test
        void endExceedsSequenceLengthFailure() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen);
            ValidationException ex = assertThrows(
                    ValidationException.class,
                    () -> check.validateSequenceRegionAgainstSequence(annotationWithSequenceRegion(1L, seqLen + 1), 1));
            assertTrue(ex.getMessage().contains("end position"));
        }

        @Test
        void endBelowSequenceLengthFailure() throws Exception {
            long seqLen = 1000L;
            injectLookupReturning(SEQ_ID, seqLen);
            ValidationException ex = assertThrows(
                    ValidationException.class,
                    () -> check.validateSequenceRegionAgainstSequence(annotationWithSequenceRegion(1L, seqLen - 1), 1));
            assertTrue(ex.getMessage().contains("end position"));
        }

        @Test
        void noSequenceRegionSkipped() throws Exception {
            injectLookupReturning(SEQ_ID, 1000L);
            GFF3Annotation annotation = new GFF3Annotation();
            annotation.addFeature(TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of()));
            assertDoesNotThrow(() -> check.validateSequenceRegionAgainstSequence(annotation, 1));
        }

        @Test
        void noLookupSkipped() throws Exception {
            injectNoLookup();
            assertDoesNotThrow(
                    () -> check.validateSequenceRegionAgainstSequence(annotationWithSequenceRegion(0L, 99999L), 1));
        }
    }

    @Nested
    class ValidateMinimumLength {

        @Test
        void lengthAbove100Success() throws Exception {
            injectLookupReturning(SEQ_ID, 150L);
            assertDoesNotThrow(() -> check.validateMinimumLength(annotationWithSequenceRegion(1L, 150L), 1));
        }

        @Test
        void lengthBelow100NoExceptionFailure() throws Exception {
            injectLookupReturning(SEQ_ID, 50L);
            ValidationException ex = assertThrows(
                    ValidationException.class,
                    () -> check.validateMinimumLength(annotationWithSequenceRegion(1L, 50L), 1));
            assertTrue(ex.getMessage().contains("SEQUENCE_TOO_SHORT"));
        }

        @Test
        void lengthBelow100WithNcRnaGeneSuccess() throws Exception {
            injectLookupReturning(SEQ_ID, 50L);
            assertDoesNotThrow(() -> check.validateMinimumLength(annotationWithFeature("ncRNA_gene"), 1));
        }

        @Test
        void lengthBelow100WithNcRnaSuccess() throws Exception {
            injectLookupReturning(SEQ_ID, 50L);
            assertDoesNotThrow(() -> check.validateMinimumLength(annotationWithFeature("ncRNA"), 1));
        }

        @Test
        void lengthBelow100WithMicrosatelliteSuccess() throws Exception {
            injectLookupReturning(SEQ_ID, 50L);
            assertDoesNotThrow(() -> check.validateMinimumLength(annotationWithFeature("microsatellite"), 1));
        }

        @Test
        void noLookupSkipped() throws Exception {
            injectNoLookup();
            assertDoesNotThrow(() -> check.validateMinimumLength(annotationWithSequenceRegion(1L, 50L), 1));
        }
    }

    @Nested
    class ValidateAssemblyMinimumLength {

        @Test
        void assemblyLengthAbove1000Success() throws Exception {
            injectLookupReturning(SEQ_ID, 1500L, AnalysisType.SEQUENCE_ASSEMBLY);
            assertDoesNotThrow(() -> check.validateWGSMinimumLength(annotationWithSequenceRegion(1L, 1500L), 1));
        }

        @Test
        void assemblyLengthBelow1000Failure() throws Exception {
            injectLookupReturning(SEQ_ID, 500L, AnalysisType.SEQUENCE_ASSEMBLY);
            ValidationException ex = assertThrows(
                    ValidationException.class,
                    () -> check.validateWGSMinimumLength(annotationWithSequenceRegion(1L, 500L), 1));
            assertTrue(ex.getMessage().contains("ASSEMBLY_SEQUENCE_TOO_SHORT"));
        }

        @Test
        void nonAssemblyLengthBelow1000Skipped() throws Exception {
            injectLookupReturning(SEQ_ID, 500L, AnalysisType.SEQUENCE_FLATFILE);
            assertDoesNotThrow(() -> check.validateWGSMinimumLength(annotationWithSequenceRegion(1L, 500L), 1));
        }

        @Test
        void noAnalysisContextSkipped() throws Exception {
            injectLookupReturning(SEQ_ID, 500L);
            assertDoesNotThrow(() -> check.validateWGSMinimumLength(annotationWithSequenceRegion(1L, 500L), 1));
        }

        @Test
        void noLookupSkipped() throws Exception {
            injectNoLookup();
            assertDoesNotThrow(() -> check.validateWGSMinimumLength(annotationWithSequenceRegion(1L, 500L), 1));
        }
    }

    @Nested
    class ValidateLncRnaLength {

        @Test
        void lncRnaFeatureLengthAbove200Success() throws Exception {
            injectLookupReturning(SEQ_ID, 250L);
            assertDoesNotThrow(() -> check.validateLncRnaLength(annotationWithFeature("lncRNA"), 1));
        }

        @Test
        void lncRnaFeatureLengthBelow200Failure() throws Exception {
            injectLookupReturning(SEQ_ID, 150L);
            ValidationException ex = assertThrows(
                    ValidationException.class, () -> check.validateLncRnaLength(annotationWithFeature("lncRNA"), 1));
            assertTrue(ex.getMessage().contains("LNCRNA_TOO_SHORT"));
        }

        @Test
        void lncRnaDescendantFeatureLengthBelow200Failure() throws Exception {
            injectLookupReturning(SEQ_ID, 150L);
            ValidationException ex = assertThrows(
                    ValidationException.class,
                    () -> check.validateLncRnaLength(annotationWithFeature("antisense_lncRNA"), 1));
            assertTrue(ex.getMessage().contains("LNCRNA_TOO_SHORT"));
        }

        @Test
        void noLncRnaFeatureSkipped() throws Exception {
            injectLookupReturning(SEQ_ID, 150L);
            assertDoesNotThrow(() -> check.validateLncRnaLength(annotationWithFeature("gene"), 1));
        }

        @Test
        void noLookupSkipped() throws Exception {
            injectNoLookup();
            assertDoesNotThrow(() -> check.validateLncRnaLength(annotationWithFeature("lncRNA"), 1));
        }
    }
}
