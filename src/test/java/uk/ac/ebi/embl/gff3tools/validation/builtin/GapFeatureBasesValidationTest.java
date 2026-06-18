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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.GapRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class GapFeatureBasesValidationTest {

    private static final String SEQ_ID = TestUtils.DEFAULT_ACCESSION;

    private GapFeatureBasesValidation check;

    @BeforeEach
    void setUp() {
        check = new GapFeatureBasesValidation();
    }

    private void injectWithGapRegions(String seqId, long start, long end, List<GapRegion> regions) throws Exception {
        SequenceLookup mockLookup = mock(SequenceLookup.class);
        when(mockLookup.getGapRegions(seqId, start, end, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn(regions);

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
    public void testValidateGapBasesFullyCoveredSuccess() throws Exception {
        injectWithGapRegions(SEQ_ID, 1L, 5L, List.of(new GapRegion(1L, 5L)));
        GFF3Feature gap = TestUtils.createGFF3Feature("gap", SEQ_ID, 1L, 5L, Map.of());
        assertDoesNotThrow(() -> check.validateGapBases(gap, 1));
    }

    @Test
    public void testValidateGapBasesCoveringRegionLargerThanFeatureSuccess() throws Exception {
        injectWithGapRegions(SEQ_ID, 2L, 4L, List.of(new GapRegion(1L, 10L)));
        GFF3Feature gap = TestUtils.createGFF3Feature("gap", SEQ_ID, 2L, 4L, Map.of());
        assertDoesNotThrow(() -> check.validateGapBases(gap, 1));
    }

    @Test
    public void testValidateGapBasesNoGapRegionFailure() throws Exception {
        injectWithGapRegions(SEQ_ID, 1L, 5L, List.of());
        GFF3Feature gap = TestUtils.createGFF3Feature("gap", SEQ_ID, 1L, 5L, Map.of());
        ValidationException ex = assertThrows(ValidationException.class, () -> check.validateGapBases(gap, 1));
        assertTrue(ex.getMessage().contains("only \"n\""));
        assertTrue(ex.getMessage().contains("1"));
        assertTrue(ex.getMessage().contains("5"));
    }

    @Test
    public void testValidateGapBasesPartialCoverageFailure() throws Exception {
        // gap region only covers 1..3, feature spans 1..5
        injectWithGapRegions(SEQ_ID, 1L, 5L, List.of(new GapRegion(1L, 3L)));
        GFF3Feature gap = TestUtils.createGFF3Feature("gap", SEQ_ID, 1L, 5L, Map.of());
        assertThrows(ValidationException.class, () -> check.validateGapBases(gap, 1));
    }

    @Test
    public void testValidateGapBasesNonGapFeatureSkipped() throws Exception {
        injectWithGapRegions(SEQ_ID, 1L, 5L, List.of());
        GFF3Feature cds = TestUtils.createGFF3Feature("CDS", SEQ_ID, 1L, 5L, Map.of());
        assertDoesNotThrow(() -> check.validateGapBases(cds, 1));
    }

    @Test
    public void testValidateGapBasesNoSequenceLookupSkipped() {
        injectNoLookup();
        GFF3Feature gap = TestUtils.createGFF3Feature("gap", SEQ_ID, 1L, 5L, Map.of());
        assertDoesNotThrow(() -> check.validateGapBases(gap, 1));
    }
}
