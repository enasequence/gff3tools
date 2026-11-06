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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.ebi.embl.gff3tools.validation.builtin.ArtificialLocationValidation.ARTIFICIAL_LOCATION_ANALYSIS_TYPE_RULE;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

class ArtificialLocationValidationTest {

    private static final int LINE = 42;

    private ArtificialLocationValidation validation;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        validation = new ArtificialLocationValidation();
        context = TestUtils.createTestContext();
        TestUtils.injectContext(validation, context);
    }

    // ---tests for ARTIFICIAL_LOCATION_ANALYSIS_TYPE validation

    @ParameterizedTest
    @EnumSource(
            value = AnalysisType.class,
            names = {"SEQUENCE_FLATFILE", "TRANSCRIPTOME_ASSEMBLY"})
    void failsWhenArtificialLocationIsUsedOutsideAssembly(AnalysisType analysisType) {
        withAnalysisType(analysisType);

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> validation.validateArtificialLocationAnalysisType(artificialLocationFeature(), LINE));

        assertEquals(ARTIFICIAL_LOCATION_ANALYSIS_TYPE_RULE, ex.getValidationRule());
        assertEquals(LINE, ex.getLine());
        assertTrue(ex.getMessage().contains(analysisType.name()), ex.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = AnalysisType.class,
            names = {"SEQUENCE_ASSEMBLY", "UNKNOWN"})
    void passesForAssemblyAndUnknownAnalysisType(AnalysisType analysisType) {
        withAnalysisType(analysisType);

        assertDoesNotThrow(() -> validation.validateArtificialLocationAnalysisType(artificialLocationFeature(), LINE));
    }

    @Test
    void passesWhenNoAnalysisContextIsRegistered() {
        assertDoesNotThrow(() -> validation.validateArtificialLocationAnalysisType(artificialLocationFeature(), LINE));
    }

    @ParameterizedTest
    @EnumSource(AnalysisType.class)
    void passesWhenFeatureHasNoArtificialLocation(AnalysisType analysisType) {
        withAnalysisType(analysisType);
        GFF3Feature feature = TestUtils.createGFF3Feature("gene", Map.of());

        assertDoesNotThrow(() -> validation.validateArtificialLocationAnalysisType(feature, LINE));
    }

    private void withAnalysisType(AnalysisType analysisType) {
        TestUtils.registerProvider(context, AnalysisContext.class, new AnalysisContext(analysisType, 10));
    }

    private GFF3Feature artificialLocationFeature() {
        return TestUtils.createGFF3Feature(
                "gene", Map.of(GFF3Attributes.ARTIFICIAL_LOCATION, List.of("low-quality sequence region")));
    }
}
