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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
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

    // NOTE: Document this method.
    // ???: Is this used by the validation engine? If so, does this work both for methods & classes?
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
