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

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

class EmptyAnnotationValidationTest {

    private static final String ACCESSION = "SEQ1.1";
    private static final int LINE = 7;

    private EmptyAnnotationValidation validation;

    @BeforeEach
    void setUp() {
        validation = new EmptyAnnotationValidation();
    }

    @Test
    void validate_withEmptyAnnotation_shouldThrowException() {
        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validate(emptyAnnotation(), LINE));

        assertEquals(EmptyAnnotationValidation.VALIDATION_RULE, exception.getValidationRule());
        assertEquals(LINE, exception.getLine());
        assertTrue(exception.getMessage().contains(ACCESSION));
    }

    @Test
    void validate_withFeatures_shouldPass() {
        assertDoesNotThrow(() -> validation.validate(annotationWithFeature(), LINE));
    }

    @Test
    void engine_withDefaultSeverity_shouldSkipEmptyAnnotation() throws ValidationException {
        ValidationEngine engine = engineBuilder().build();

        engine.validate(emptyAnnotation(), LINE);

        assertTrue(engine.getCollectedErrors().isEmpty());
        assertTrue(engine.getParsingWarnings().isEmpty());
    }

    @Test
    void engine_withRuleSetToError_shouldReportEmptyAnnotation() throws ValidationException {
        ValidationEngine engine = engineBuilder()
                .overrideMethodRules(Map.of(EmptyAnnotationValidation.VALIDATION_RULE, RuleSeverity.ERROR))
                .build();

        engine.validate(emptyAnnotation(), LINE);

        assertEquals(1, engine.getCollectedErrors().size());
        assertEquals(
                EmptyAnnotationValidation.VALIDATION_RULE,
                engine.getCollectedErrors().get(0).getValidationRule());
    }

    @Test
    void engine_withRuleSetToWarn_shouldWarnOnEmptyAnnotation() throws ValidationException {
        ValidationEngine engine = engineBuilder()
                .overrideMethodRules(Map.of(EmptyAnnotationValidation.VALIDATION_RULE, RuleSeverity.WARN))
                .build();

        engine.validate(emptyAnnotation(), LINE);

        assertTrue(engine.getCollectedErrors().isEmpty());
        assertEquals(1, engine.getParsingWarnings().size());
    }

    @Test
    void engine_withRuleSetToError_shouldPassAnnotationWithFeatures() throws ValidationException {
        ValidationEngine engine = engineBuilder()
                .overrideMethodRules(Map.of(EmptyAnnotationValidation.VALIDATION_RULE, RuleSeverity.ERROR))
                .build();

        engine.validate(annotationWithFeature(), LINE);

        assertTrue(engine.getCollectedErrors().isEmpty());
    }

    private static ValidationEngineBuilder engineBuilder() {
        return new ValidationEngineBuilder()
                .disableAutodetectContextProviders()
                .disableAutodetectValidationsAndFixes()
                .withValidator(new EmptyAnnotationValidation())
                .failFast(false);
    }

    private static GFF3Annotation emptyAnnotation() {
        return TestUtils.createGFF3Annotation(ACCESSION, 1, 100);
    }

    private static GFF3Annotation annotationWithFeature() {
        return TestUtils.createGFF3Annotation(
                ACCESSION, 1, 100, TestUtils.createGFF3Feature("gene1", null, "gene", ACCESSION, 1, 50));
    }
}
