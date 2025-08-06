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

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.embl.converter.exception.CLIException;
import uk.ac.ebi.embl.converter.exception.DuplicateValidationRuleException;

public class ValidationEngineBuilder<F, A> {

    private static Logger LOG = LoggerFactory.getLogger(ValidationEngineBuilder.class);

    private final List<Validation> allValidations;
    private final List<FeatureValidation<F>> activeFeatureValidations;
    private final List<AnnotationValidation<A>> activeAnnotationValidations;
    private final HashSet<String> registeredValidationRules;
    private Map<String, RuleSeverity> severityMap;

    public ValidationEngineBuilder() {
        this.allValidations = new ArrayList<>();
        this.activeFeatureValidations = new ArrayList<>();
        this.activeAnnotationValidations = new ArrayList<>();
        this.registeredValidationRules = new HashSet<>();
        this.severityMap = loadDefaultSeverities();
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

    public void registerValidations(Validation[] validations)
            throws ClassCastException, DuplicateValidationRuleException {
        for (Validation v : validations) {
            registerValidation(v);
        }
    }

    public ValidationEngine<F, A> build() {
        reevaluateActiveValidations();
        return new ValidationEngine<>(activeFeatureValidations, activeAnnotationValidations, severityMap);
    }

    public void overrideRuleSeverities(Map<String, RuleSeverity> map) throws CLIException {
        for (String rule : map.keySet()) {
            if (!this.registeredValidationRules.contains(rule)) {
                throw new CLIException("The rule %s has no validator assigned".formatted(rule));
            }
        }
        this.severityMap.putAll(map);
    }

    private Map<String, RuleSeverity> loadDefaultSeverities() {
        HashMap<String, RuleSeverity> severities = new HashMap<>();
        try (InputStream input =
                RuleSeverityState.class.getClassLoader().getResourceAsStream("default-rule-severities.properties")) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            prop.forEach((k, v) -> {
                String rule = (String) k;
                RuleSeverity severity = RuleSeverity.valueOf((String) v);
                severities.put(rule, severity);
            });

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return severities;
    }

    private void reevaluateActiveValidations() {
        activeFeatureValidations.clear();
        activeAnnotationValidations.clear();

        for (Validation validation : allValidations) {
            String validationRule = validation.getValidationRule();
            if (severityMap.getOrDefault(validationRule, RuleSeverity.ERROR) != RuleSeverity.OFF) {
                if (validation instanceof FeatureValidation) {
                    activeFeatureValidations.add((FeatureValidation<F>) validation);
                }
                if (validation instanceof AnnotationValidation) {
                    activeAnnotationValidations.add((AnnotationValidation<A>) validation);
                }
            }
        }
    }
}
