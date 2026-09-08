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
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.Getter;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

@Getter
public class ValidationConfig {
    private final Map<String, RuleSeverity> ruleOverrides;
    private final Map<String, Boolean> validatorOverrides;
    private final Map<String, Boolean> fixOverrides;

    public ValidationConfig(
            Map<String, RuleSeverity> ruleOverrides,
            Map<String, Boolean> validatorOverrides,
            Map<String, Boolean> fixOverrides) {
        this.ruleOverrides = ruleOverrides != null ? ruleOverrides : Map.of();
        this.validatorOverrides = validatorOverrides != null ? validatorOverrides : Map.of();
        this.fixOverrides = fixOverrides != null ? fixOverrides : Map.of();
    }

    public RuleSeverity getSeverity(String rule, RuleSeverity defaultAction) {
        return ruleOverrides.getOrDefault(rule, defaultAction);
    }

    public boolean getFix(String rule, boolean defaultEnabled) {
        return fixOverrides.getOrDefault(rule, defaultEnabled);
    }

    /**
     * Loads the default {@link ValidationConfig} from {@code default-rule-severities.properties} on
     * the classpath. Keys are routed by prefix: {@code rule.*} to severities, {@code fix.*} to fix
     * toggles, and {@code class.*} to validator-class toggles.
     *
     * @return the default validation configuration
     */
    public static ValidationConfig loadDefault() {
        Map<String, RuleSeverity> severityOverrides = new HashMap<>();
        Map<String, Boolean> validatorOverrides = new HashMap<>();
        Map<String, Boolean> fixOverrides = new HashMap<>();
        try (InputStream input =
                ValidationConfig.class.getClassLoader().getResourceAsStream("default-rule-severities.properties")) {

            Properties prop = new Properties();
            prop.load(input);

            prop.forEach((key, value) -> {
                String k = (String) key;
                String v = (String) value;

                if (k.startsWith("rule")) {
                    String rule = k.replace("rule.", "");
                    RuleSeverity severity = RuleSeverity.valueOf(v);
                    severityOverrides.put(rule, severity);
                } else if (k.startsWith("fix")) {
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

    /**
     *  Checks if class level annotation is enabled.
     */
    public boolean isValidatorEnabled(Annotation annotation) {
        try {
            Method nameMethod = annotation.annotationType().getMethod("name");
            Method enabledMethod = annotation.annotationType().getMethod("enabled");

            String name = (String) nameMethod.invoke(annotation);
            boolean enabled = (Boolean) enabledMethod.invoke(annotation);

            return validatorOverrides.getOrDefault(name, enabled);
        } catch (Exception e) {
            throw new RuntimeException("Invalid validator annotation: " + annotation.annotationType(), e);
        }
    }
}
