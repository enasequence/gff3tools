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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uk.ac.ebi.embl.converter.exception.DuplicateValidationRuleException;
import uk.ac.ebi.embl.converter.exception.ValidationException;

public class ValidationEngine<F, A> {
    private final List<Validation> allValidations;
    private final List<FeatureValidation<F>> activeFeatureValidations;
    private final List<AnnotationValidation<A>> activeAnnotationValidations;
    private final HashSet<String> registeredValidationRules;

    public ValidationEngine() {
        this.allValidations = new ArrayList<>();
        this.activeFeatureValidations = new ArrayList<>();
        this.activeAnnotationValidations = new ArrayList<>();
        this.registeredValidationRules = new HashSet<>();
    }

    // Due to Java's type erasure, we cannot directly check for FeatureValidation<A> and AnnotationValidation<A>
    // at runtime for a specific type A.
    // The 'instanceof' checks only verifies the raw type 'FeatureValidation' and `AnnotationValidation`.
    // This means the casts is unchecked and relies on convention
    // to prevent ClassCastException if an incompatible validation is registered.
    public void registerValidation(Validation validation) throws ClassCastException, DuplicateValidationRuleException {
        String validationRule = validation.getValidationRule();
        if (registeredValidationRules.contains(validationRule)) {
            throw new DuplicateValidationRuleException(
                    "Validation rule with name '" + validationRule + "' is already registered.");
        }

        if (validation instanceof FeatureValidation) {
            activeFeatureValidations.add((FeatureValidation<F>) validation);
        }
        if (validation instanceof AnnotationValidation) {
            activeAnnotationValidations.add((AnnotationValidation<A>) validation);
        }

        allValidations.add(validation);

        registeredValidationRules.add(validationRule);
    }

    public void setActiveValidations(Set<String> activeValidationsRules) {
        this.activeFeatureValidations.clear();
        this.activeAnnotationValidations.clear();

        for (Validation validation : allValidations) {
            if (activeValidationsRules.contains(validation.getValidationRule())) {
                if (validation instanceof FeatureValidation) {
                    activeFeatureValidations.add((FeatureValidation<F>) validation);
                }
                if (validation instanceof AnnotationValidation) {
                    activeAnnotationValidations.add((AnnotationValidation<A>) validation);
                }
            }
        }
    }

    // Getter for testing purposes
    public List<FeatureValidation<F>> getFeatureValidations() {
        return activeFeatureValidations;
    }

    public List<AnnotationValidation<A>> getAnnotationValidations() {
        return activeAnnotationValidations;
    }

    public void validateFeature(F feature) throws ValidationException {
        for (FeatureValidation<F> validation : activeFeatureValidations) {
            validation.validateFeature(feature);
        }
    }

    public void validateAnnotation(A annotation) throws ValidationException, ClassCastException {
        for (AnnotationValidation<A> validation : activeAnnotationValidations) {
            validation.validateAnnotation(annotation);
        }
    }
}
