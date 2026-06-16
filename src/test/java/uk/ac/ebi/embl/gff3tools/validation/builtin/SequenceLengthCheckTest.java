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
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class SequenceLengthCheckTest {

    private static final String SEQ_ID = "chr1";

    private SequenceLengthCheck check;

    @BeforeEach
    void setUp() {
        check = new SequenceLengthCheck();
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
        when(mockLookup.getSequenceLength(seqId)).thenReturn(len);
        injectLookup(mockLookup);
    }

    private void injectNoLookup() {
        TestUtils.injectContext(check);
    }

    private GFF3Annotation annotationWithSequenceRegion(long regionStart, long regionEnd) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(SEQ_ID, Optional.empty(), regionStart, regionEnd));
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
}
