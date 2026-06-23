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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class SequenceMappingValidationTest {

    private static final String SEQ_ID = "chr1";

    private SequenceMappingValidation check;

    @BeforeEach
    void setUp() {
        check = new SequenceMappingValidation();
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

    private void injectNoLookup() {
        TestUtils.injectContext(check);
    }

    @Test
    void noSequenceProviderSkipped() {
        injectNoLookup();
        GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of());
        assertDoesNotThrow(() -> check.validateSequenceMapping(feature, 1));
    }

    @Test
    void mappingResolvesSuccess() throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(SEQ_ID, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(1000L);
        injectLookup(mockLookup);

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of());
        assertDoesNotThrow(() -> check.validateSequenceMapping(feature, 1));
    }

    @Test
    void noMappingExplodes() throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(SEQ_ID, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenThrow(new RuntimeException("seqId not found"));
        injectLookup(mockLookup);

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of());
        ValidationException ex =
                assertThrows(ValidationException.class, () -> check.validateSequenceMapping(feature, 1));
        assertTrue(ex.getMessage().contains(SEQ_ID));
        assertTrue(ex.getMessage().contains("SEQUENCE_MAPPING"));
    }

    @Test
    void alreadyValidatedAccessionNotResolvedAgain() throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(SEQ_ID, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(1000L);
        injectLookup(mockLookup);

        GFF3Feature first = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of());
        GFF3Feature second = TestUtils.createGFF3Feature("mRNA", SEQ_ID, 1L, 500L, Map.of());

        assertDoesNotThrow(() -> check.validateSequenceMapping(first, 1));
        assertDoesNotThrow(() -> check.validateSequenceMapping(second, 2));

        // Same accession on both features: the provider is only queried once.
        verify(mockLookup, times(1)).getSequenceLength(SEQ_ID, SequenceRangeOption.WHOLE_SEQUENCE);
    }

    @Test
    void failedAccessionIsRetriedNotCachedAsValid() throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getSequenceLength(SEQ_ID, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenThrow(new RuntimeException("seqId not found"));
        injectLookup(mockLookup);

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", SEQ_ID, 1L, 500L, Map.of());

        assertThrows(ValidationException.class, () -> check.validateSequenceMapping(feature, 1));
        assertThrows(ValidationException.class, () -> check.validateSequenceMapping(feature, 2));

        // A failed accession must not be remembered as validated.
        verify(mockLookup, times(2)).getSequenceLength(SEQ_ID, SequenceRangeOption.WHOLE_SEQUENCE);
    }
}
