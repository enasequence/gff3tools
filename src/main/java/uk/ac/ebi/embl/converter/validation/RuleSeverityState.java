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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public enum RuleSeverityState {
    INSTANCE;

    private Map<ValidationRule, RuleSeverity> severityMap = new HashMap<>();

    private RuleSeverityState() {
        this.loadProps();
    }

    public void putAll(Map<ValidationRule, RuleSeverity> map) {
        this.severityMap.putAll(map);
    }

    public void set(ValidationRule rule, RuleSeverity severity) {
        this.severityMap.put(rule, severity);
    }

    public RuleSeverity getSeverity(ValidationRule rule) {
        return severityMap.get(rule);
    }

    private void loadProps() {

        try (InputStream input =
                RuleSeverityState.class.getClassLoader().getResourceAsStream("default-rule-severities.properties")) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            prop.forEach((k, v) -> {
                ValidationRule rule = ValidationRule.valueOf((String) k);
                RuleSeverity severity = RuleSeverity.valueOf((String) v);
                this.severityMap.put(rule, severity);
            });

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
