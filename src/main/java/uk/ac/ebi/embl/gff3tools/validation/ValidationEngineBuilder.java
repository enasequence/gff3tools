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
import java.sql.Connection;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

public class ValidationEngineBuilder {

    private final ValidationConfig validationConfig;
    public ValidationRegistry validationRegistry;
    private Connection connection;

    public ValidationEngineBuilder() {

        // Loads default severity rules and validatorOverrides
        validationConfig = getValidationConfig();

        // Init validation validationRegistry
        validationRegistry = ValidationRegistry.getInstance(validationConfig, connection);
    }

    public ValidationEngine build() {
        return new ValidationEngine(validationConfig, validationRegistry);
    }

    public void overrideMethodRules(Map<String, RuleSeverity> map) {
        this.validationConfig.getRuleOverrides().putAll(map);
    }

    public void overrideMethodFixs(Map<String, Boolean> map) {
        this.validationConfig.getFixOverrides().putAll(map);
    }

    public void overrideClassRules(Map<String, Boolean> map) {
        this.validationConfig.getValidatorOverrides().putAll(map);
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    private ValidationConfig getValidationConfig() {
        Map<String, RuleSeverity> severityOverrides = new HashMap<>();
        Map<String, Boolean> validatorOverrides = new HashMap<>();
        Map<String, Boolean> fixOverrides = new HashMap<>();
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
                } else if (k.startsWith("uk/ac/ebi/embl/gff3tools/validation/fix")) {
                    String rule = k.replace("fix.", "");
                    boolean fix = v.equalsIgnoreCase("ON");
                    fixOverrides.put(rule, fix);
                } else if (k.startsWith("class")) {
                    String validationClass = k.replace("class.", "");
                    boolean validationOn = v.equalsIgnoreCase("on");
                    validatorOverrides.put(validationClass, validationOn);
                }
            });
            return new ValidationConfig(severityOverrides, validatorOverrides, fixOverrides);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
