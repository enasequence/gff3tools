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

import org.junit.jupiter.api.*;
import uk.ac.ebi.embl.converter.exception.ValidationException;

public class ValidationEngineTest {

    @Test
    public void testRegisterValidation() {
        ValidationEngine<String, String> validationEngine = new ValidationEngine();
        Validation mockValidation = new Validation() {};
        validationEngine.registerValidation(mockValidation);
        assertEquals(1, validationEngine.getValidations().size());
        assertTrue(validationEngine.getValidations().contains(mockValidation));
    }

    @Test
    public void testValidateFeature_successfulValidation() throws ValidationException {
        ValidationEngine<String, String> validationEngine = new ValidationEngine();
        final boolean[] validated = {false};
        FeatureValidation<String> mockFeatureValidation = new FeatureValidation<String>() {
            @Override
            public void validateFeature(String feature) throws ValidationException {
                validated[0] = true;
            }
        };
        validationEngine.registerValidation(mockFeatureValidation);
        validationEngine.validateFeature("testFeature");
        assertTrue(validated[0]);
    }

    @Test
    public void testValidateFeature_noClassCastExceptionWithIncompatibleValidation() {
        ValidationEngine<String, String> validationEngine = new ValidationEngine();

        AnnotationValidation<String> mockAnnotationValidation = new AnnotationValidation<String>() {
            @Override
            public void validateAnnotation(String annotation) throws ValidationException {
                // This method won't be called by validateFeature
            }
        };
        validationEngine.registerValidation(mockAnnotationValidation);
        try {
            validationEngine.validateFeature("testFeature");
        } catch (ClassCastException e) {
            fail("ClassCastException should not be thrown for incompatible validation.");
        } catch (ValidationException e) {
            // Expected if a validation rule throws it, but not for ClassCastException test
        }
    }

    @Test
    public void testValidateAnnotation_successfulValidation() throws ValidationException {
        ValidationEngine<String, String> validationEngine = new ValidationEngine();

        final boolean[] validated = {false};
        AnnotationValidation<String> mockAnnotationValidation = new AnnotationValidation<String>() {
            @Override
            public void validateAnnotation(String annotation) throws ValidationException {
                validated[0] = true;
            }
        };
        validationEngine.registerValidation(mockAnnotationValidation);
        validationEngine.validateAnnotation("testAnnotation");
        assertTrue(validated[0]);
    }

    @Test
    public void testValidateAnnotation_noClassCastExceptionWithIncompatibleValidation() {
        ValidationEngine<String, String> validationEngine = new ValidationEngine();

        FeatureValidation<String> mockFeatureValidation = new FeatureValidation<String>() {
            @Override
            public void validateFeature(String feature) throws ValidationException {
                // This method won't be called by validateAnnotation
            }
        };
        validationEngine.registerValidation(mockFeatureValidation);
        try {
            validationEngine.validateAnnotation("testAnnotation");
        } catch (ClassCastException e) {
            fail("ClassCastException should not be thrown for incompatible validation.");
        } catch (ValidationException e) {
            // Expected if a validation rule throws it, but not for ClassCastException test
        }
    }

    @Test
    public void testValidateFeature_throwsClassCastExceptionWithIncompatibleType() {
        ValidationEngine<String, String> validationEngine = new ValidationEngine();

        // Register a FeatureValidation that is incompatible with the ValidationEngine's type T (String)
        // For example, FeatureValidation<Integer> instead of FeatureValidation<String>
        // Due to type erasure, this will compile but should throw ClassCastException at runtime
        // when validateFeature is called and attempts an unchecked cast.
        Validation incompatibleValidation = new FeatureValidation<Integer>() {
            @Override
            public void validateFeature(Integer feature) throws ValidationException {
                // This method will not be reached as ClassCastException is expected earlier
            }
        };
        validationEngine.registerValidation(incompatibleValidation);

        assertThrows(ClassCastException.class, () -> {
            validationEngine.validateFeature("testFeature");
        });
    }
}
