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
package uk.ac.ebi.embl.gff3tools.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.UnregisteredValidationRuleException;

public class ValidationEngineBuilder {

    private Map<String, RuleSeverity> severityOverrides;
    private Map<String, Boolean> validatorOverrides;

    private final ValidationConfig validationConfig;
    public ValidationRegistry validationRegistry;

    public ValidationEngineBuilder() {

        // Loads severityOverrides and validatorOverrides
        loadDefaultSeverities();

        // ValidationConfig with default conf.
        validationConfig = new ValidationConfig(severityOverrides, validatorOverrides);

        initValidationRegistry();
    }

    public ValidationEngine build() {
        return new ValidationEngine(validationConfig, validationRegistry);
    }

    public void overrideRuleSeverities(Map<String, RuleSeverity> map) throws UnregisteredValidationRuleException {
        this.severityOverrides.putAll(map);
    }

    public void overrideValidatorSeverities(Map<String, Boolean> map) throws UnregisteredValidationRuleException {
        this.validatorOverrides.putAll(map);
    }

    public void initValidationRegistry() {
        validationRegistry = ValidationRegistry.getInstance();
        validationRegistry.initRegistry(validationConfig);
    }

    private void loadDefaultSeverities() {
        severityOverrides = new HashMap<>();
        validatorOverrides = new HashMap<>();
        try (InputStream input = ValidationEngineBuilder.class
                .getClassLoader()
                .getResourceAsStream("default-rule-severities.properties")) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            prop.forEach((key, value) -> {
                String k = (String) key;
                String v = (String) value;

                if (k.startsWith("rule")) {
                    String rule = k.replace("rule.", "");
                    RuleSeverity severity = RuleSeverity.valueOf(v);
                    severityOverrides.put(rule, severity);
                } else if (k.startsWith("class")) {
                    String validationClass = k.replace("class.", "");
                    boolean validationOn = v.equalsIgnoreCase("on");
                    validatorOverrides.put(validationClass, validationOn);
                }
            });

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /* private void reevaluateActiveValidations() {
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
    }*/
}
