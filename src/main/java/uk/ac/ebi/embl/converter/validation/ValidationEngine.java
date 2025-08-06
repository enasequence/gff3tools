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

import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.converter.exception.ValidationException;

public class ValidationEngine<T, A> {
    private final List<Validation> validations;

    public ValidationEngine() {
        this.validations = new ArrayList<>();
    }

    public void registerValidation(Validation validation) {
        validations.add(validation);
    }

    // Getter for testing purposes
    public List<Validation> getValidations() {
        return validations;
    }

    public void validateFeature(T feature) throws ValidationException, ClassCastException {
        // Due to Java's type erasure, we cannot directly check for FeatureValidation<T> at runtime for a specific type
        // T.
        // The 'instanceof' check only verifies the raw type 'FeatureValidation'.
        // This means the cast '((FeatureValidation<T>) validation)' is unchecked and relies on convention
        // to prevent ClassCastException if an incompatible validation is registered.
        for (Validation validation : validations) {
            if (validation instanceof FeatureValidation) {
                ((FeatureValidation<T>) validation).validateFeature(feature);
            }
        }
    }

    public void validateAnnotation(A annotation) throws ValidationException, ClassCastException {
        // Due to Java's type erasure, we cannot directly check for AnnotationValidation<A> at runtime for a specific
        // type A.
        // The 'instanceof' check only verifies the raw type 'AnnotationValidation'.
        // This means the cast '((AnnotationValidation<A>) validation)' is unchecked and relies on convention
        // to prevent ClassCastException if an incompatible validation is registered.
        for (Validation validation : validations) {
            if (validation instanceof AnnotationValidation) {
                ((AnnotationValidation<A>) validation).validateAnnotation(annotation);
            }
        }
    }
}
