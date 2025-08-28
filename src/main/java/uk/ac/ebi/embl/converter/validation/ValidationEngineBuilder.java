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
import uk.ac.ebi.embl.converter.exception.DuplicateValidationRuleException;
import uk.ac.ebi.embl.converter.exception.UnregisteredValidationRuleException;

public class ValidationEngineBuilder {

    private final List<Validation> allValidations;
    private final List<FeatureValidation> activeFeatureValidations;
    private final List<AnnotationValidation> activeAnnotationValidations;
    private final HashSet<String> registeredValidationRules;
    private Map<String, RuleSeverity> severityMap;

    public ValidationEngineBuilder() {
        this.allValidations = new ArrayList<>();
        this.activeFeatureValidations = new ArrayList<>();
        this.activeAnnotationValidations = new ArrayList<>();
        this.registeredValidationRules = new HashSet<>();
        this.severityMap = loadDefaultSeverities();
    }

    public void registerValidation(Validation validation) throws DuplicateValidationRuleException {
        String validationRule = validation.getValidationRule();
        if (registeredValidationRules.contains(validationRule)) {
            throw new DuplicateValidationRuleException(
                    "Validation rule with name '" + validationRule + "' is already registered.");
        }

        if (validation instanceof FeatureValidation) {
            activeFeatureValidations.add((FeatureValidation) validation);
        }
        if (validation instanceof AnnotationValidation) {
            activeAnnotationValidations.add((AnnotationValidation) validation);
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

    public ValidationEngine build() {
        reevaluateActiveValidations();
        return new ValidationEngine(activeFeatureValidations, activeAnnotationValidations, severityMap);
    }

    public void overrideRuleSeverities(Map<String, RuleSeverity> map) throws UnregisteredValidationRuleException {
        this.severityMap.putAll(map);
    }

    private Map<String, RuleSeverity> loadDefaultSeverities() {
        HashMap<String, RuleSeverity> severities = new HashMap<>();
        try (InputStream input = ValidationEngineBuilder.class
                .getClassLoader()
                .getResourceAsStream("default-rule-severities.properties")) {

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
                    activeFeatureValidations.add((FeatureValidation) validation);
                }
                if (validation instanceof AnnotationValidation) {
                    activeAnnotationValidations.add((AnnotationValidation) validation);
                }
            }
        }
    }
}
