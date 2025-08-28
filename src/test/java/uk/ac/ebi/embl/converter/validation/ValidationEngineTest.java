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
package uk.ac.ebi.embl.converter.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.*;
import uk.ac.ebi.embl.converter.exception.DuplicateValidationRuleException;
import uk.ac.ebi.embl.converter.exception.UnregisteredValidationRuleException;
import uk.ac.ebi.embl.converter.exception.ValidationException;
import uk.ac.ebi.embl.converter.gff3.GFF3Annotation;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;

public class ValidationEngineTest {

    @Test
    public void testRegisterValidation() throws DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();
        Validation mockValidation = new Validation() {
            @Override
            public String getValidationRule() {
                return "MockValidationRule";
            }
        };
        validationEngineBuilder.registerValidation(mockValidation);
        ValidationEngine validationEngine = validationEngineBuilder.build();
        assertEquals(0, validationEngine.getFeatureValidations().size());
        assertEquals(0, validationEngine.getAnnotationValidations().size());
    }

    @Test
    public void testValidateFeature_successfulValidation()
            throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();
        final boolean[] validated = {false};
        FeatureValidation mockFeatureValidation = new FeatureValidation() {
            @Override
            public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
                validated[0] = true;
            }

            @Override
            public String getValidationRule() {
                return "MockFeatureValidationRule";
            }
        };
        validationEngineBuilder.registerValidation(mockFeatureValidation);
        ValidationEngine validationEngine = validationEngineBuilder.build();
        validationEngine.validateFeature(
                new GFF3Feature(
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        Optional.empty(),
                        "",
                        "",
                        0L,
                        1L,
                        "",
                        "",
                        "",
                        new HashMap<>()),
                1);
        assertTrue(validated[0]);
    }

    @Test
    public void testValidateFeature_noClassCastExceptionWithIncompatibleValidation()
            throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        AnnotationValidation mockAnnotationValidation = new AnnotationValidation() {
            @Override
            public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
                // This method won't be called by validateFeature
            }

            @Override
            public String getValidationRule() {
                return "MockAnnotationValidationRule";
            }
        };
        validationEngineBuilder.registerValidation(mockAnnotationValidation);
        ValidationEngine validationEngine = validationEngineBuilder.build();
        // This should not throw ClassCastException as validateFeature only iterates over FeatureValidations
        validationEngine.validateFeature(
                new GFF3Feature(
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        Optional.empty(),
                        "",
                        "",
                        0L,
                        1L,
                        "",
                        "",
                        "",
                        new HashMap<>()),
                1);
    }

    @Test
    public void testValidateAnnotation_successfulValidation()
            throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        final boolean[] validated = {false};
        AnnotationValidation mockAnnotationValidation = new AnnotationValidation() {
            @Override
            public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
                validated[0] = true;
            }

            @Override
            public String getValidationRule() {
                return "MockAnnotationValidationRule";
            }
        };
        validationEngineBuilder.registerValidation(mockAnnotationValidation);
        ValidationEngine validationEngine = validationEngineBuilder.build();
        validationEngine.validateAnnotation(new GFF3Annotation(), -1);
        assertTrue(validated[0]);
    }

    @Test
    public void testValidateAnnotation_noClassCastExceptionWithIncompatibleValidation()
            throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        FeatureValidation mockFeatureValidation = new FeatureValidation() {
            @Override
            public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
                // This method won't be called by validateAnnotation
            }

            @Override
            public String getValidationRule() {
                return "MockFeatureValidationRule";
            }
        };
        validationEngineBuilder.registerValidation(mockFeatureValidation);
        ValidationEngine validationEngine = validationEngineBuilder.build();
        // This should not throw ClassCastException as validateAnnotation only iterates over AnnotationValidations
        validationEngine.validateAnnotation(new GFF3Annotation(), -1);
    }

    @Test
    public void testRegisterValidation_throwsDuplicateValidationRuleException() {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();
        Validation mockValidation1 = new Validation() {
            @Override
            public String getValidationRule() {
                return "DuplicateRule";
            }
        };
        Validation mockValidation2 = new Validation() {
            @Override
            public String getValidationRule() {
                return "DuplicateRule";
            }
        };

        try {
            validationEngineBuilder.registerValidation(mockValidation1);
            assertThrows(
                    DuplicateValidationRuleException.class,
                    () -> validationEngineBuilder.registerValidation(mockValidation2));
        } catch (DuplicateValidationRuleException e) {
            fail("Should not throw DuplicateValidationRuleException on first registration");
        }
    }

    @Test
    public void testSetActiveValidations()
            throws ValidationException, DuplicateValidationRuleException, UnregisteredValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        FeatureValidation featureValidation1 = new FeatureValidation() {
            @Override
            public void validateFeature(GFF3Feature feature, int line) throws ValidationException {}

            @Override
            public String getValidationRule() {
                return "FeatureRule1";
            }
        };
        FeatureValidation featureValidation2 = new FeatureValidation() {
            @Override
            public void validateFeature(GFF3Feature feature, int line) throws ValidationException {}

            @Override
            public String getValidationRule() {
                return "FeatureRule2";
            }
        };
        AnnotationValidation annotationValidation1 = new AnnotationValidation() {
            @Override
            public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {}

            @Override
            public String getValidationRule() {
                return "AnnotationRule1";
            }
        };

        validationEngineBuilder.registerValidation(featureValidation1);
        validationEngineBuilder.registerValidation(featureValidation2);
        validationEngineBuilder.registerValidation(annotationValidation1);

        // Initially all registered validations should be active
        ValidationEngine validationEngine = validationEngineBuilder.build();
        assertEquals(2, validationEngine.getFeatureValidations().size());
        assertEquals(1, validationEngine.getAnnotationValidations().size());

        // Set active validations to include only FeatureRule1 and AnnotationRule1
        validationEngineBuilder.overrideRuleSeverities(
                Map.of("FeatureRule2", RuleSeverity.OFF, "AnnotationRule1", RuleSeverity.WARN));
        validationEngine = validationEngineBuilder.build();

        assertEquals(1, validationEngine.getFeatureValidations().size());
        assertTrue(validationEngine.getFeatureValidations().contains(featureValidation1));
        assertEquals(1, validationEngine.getAnnotationValidations().size());
        assertTrue(validationEngine.getAnnotationValidations().contains(annotationValidation1));

        // Test with empty set
        validationEngineBuilder.overrideRuleSeverities(
                Map.of("FeatureRule1", RuleSeverity.OFF, "AnnotationRule1", RuleSeverity.OFF));
        validationEngine = validationEngineBuilder.build();
        assertEquals(0, validationEngine.getFeatureValidations().size());
        assertEquals(0, validationEngine.getAnnotationValidations().size());
    }
}
