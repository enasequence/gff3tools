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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

/**
 * Computes, for a collection of {@link ParameterDescriptor}s, which ones are attached to a
 * rule/fix that is effectively {@code OFF} right now under the codebase's three distinct
 * disablement mechanisms:
 *
 * <ol>
 *   <li>Class-level {@code @Gff3Validation}/{@code @Gff3Fix} enablement, via {@link
 *       ValidationConfig#isValidatorEnabled(Annotation)}.
 *   <li>Method-level severity {@code RuleSeverity.OFF}, via {@link
 *       ValidationConfig#getSeverity(String, RuleSeverity)}.
 *   <li>Fix-enable, via {@link ValidationConfig#getFix(String, boolean)}.
 * </ol>
 *
 * <p>The supplied {@link ValidationConfig} must already reflect the caller's {@code --rules}/fix
 * overrides merged over the properties-file defaults (see {@link ValidationConfig#loadDefault()}
 * and {@link #mergedConfig(ValidationConfig, Map, Map, Map)}), so the check is against the same
 * effective state {@link ValidationEngineBuilder} would apply.
 */
public final class EffectiveRuleState {

    private EffectiveRuleState() {}

    /**
     * Merges the given override maps over a base {@link ValidationConfig}'s own maps (typically
     * loaded via {@link ValidationConfig#loadDefault()}), the same way {@link
     * ValidationEngineBuilder#overrideMethodRules(Map)}/{@link
     * ValidationEngineBuilder#overrideClassRules(Map)}/{@link
     * ValidationEngineBuilder#overrideMethodFixs(Map)} merge caller overrides over the loaded
     * defaults. Mutates and returns {@code base}.
     */
    public static ValidationConfig mergedConfig(
            ValidationConfig base,
            Map<String, RuleSeverity> ruleOverrides,
            Map<String, Boolean> classOverrides,
            Map<String, Boolean> fixOverrides) {
        base.getRuleOverrides().putAll(ruleOverrides);
        base.getValidatorOverrides().putAll(classOverrides);
        base.getFixOverrides().putAll(fixOverrides);
        return base;
    }

    /**
     * Returns the {@code RULE.PARAM} keys of every descriptor whose owning rule/fix is effectively
     * {@code OFF} under {@code effectiveConfig}.
     */
    public static Set<String> computeOffKeys(ValidationConfig effectiveConfig, List<ParameterDescriptor> descriptors) {
        Set<String> offKeys = new HashSet<>();
        for (ParameterDescriptor descriptor : descriptors) {
            if (isOff(effectiveConfig, descriptor)) {
                offKeys.add(descriptor.key());
            }
        }
        return offKeys;
    }

    private static boolean isOff(ValidationConfig effectiveConfig, ParameterDescriptor descriptor) {
        Annotation classAnnotation = descriptor.fix()
                ? descriptor.owningClass().getAnnotation(Gff3Fix.class)
                : descriptor.owningClass().getAnnotation(Gff3Validation.class);
        if (classAnnotation != null && !effectiveConfig.isValidatorEnabled(classAnnotation)) {
            return true;
        }

        if (descriptor.fix()) {
            return !effectiveConfig.getFix(descriptor.rule(), descriptor.defaultFixEnabled());
        }
        return effectiveConfig.getSeverity(descriptor.rule(), descriptor.defaultSeverity()) == RuleSeverity.OFF;
    }
}
