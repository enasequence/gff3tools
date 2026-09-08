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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

class EffectiveRuleStateTest {

    private static ValidationConfig emptyConfig() {
        return new ValidationConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    @Test
    void classLevelDisabledByAnnotationAloneIsOff() {
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(emptyConfig(), ParameterDescriptors.scan());
        assertTrue(offKeys.contains("PARAM_FIXTURE_DISABLED_CLASS_RULE.THRESHOLD"));
    }

    @Test
    void classLevelDisabledByOverrideMapIsOff() {
        ValidationConfig config =
                new ValidationConfig(new HashMap<>(), new HashMap<>(Map.of("PARAM_FIXTURE", false)), new HashMap<>());
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(config, ParameterDescriptors.scan());
        assertTrue(offKeys.contains("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
    }

    @Test
    void methodSeverityOffViaOverrideMapIsOff() {
        ValidationConfig config = new ValidationConfig(
                new HashMap<>(Map.of("PARAM_FIXTURE_LONG", RuleSeverity.OFF)), new HashMap<>(), new HashMap<>());
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(config, ParameterDescriptors.scan());
        assertTrue(offKeys.contains("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
    }

    @Test
    void methodSeverityOffViaPropertiesAloneWithNoOverrideIsOff() {
        // Simulates a rule already resolved to OFF via default-rule-severities.properties, with
        // no --rules override merged on top: the config's own map already carries the OFF entry.
        ValidationConfig config = new ValidationConfig(
                new HashMap<>(Map.of("PARAM_FIXTURE_LONG", RuleSeverity.OFF)), new HashMap<>(), new HashMap<>());
        ValidationConfig merged = EffectiveRuleState.mergedConfig(config, Map.of(), Map.of(), Map.of());
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(merged, ParameterDescriptors.scan());
        assertTrue(offKeys.contains("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
    }

    @Test
    void fixEnableOffViaOverrideMapIsOff() {
        ValidationConfig config = new ValidationConfig(
                new HashMap<>(), new HashMap<>(), new HashMap<>(Map.of("PARAM_FIXTURE_FIX_RULE", false)));
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(config, ParameterDescriptors.scan());
        assertTrue(offKeys.contains("PARAM_FIXTURE_FIX_RULE.THRESHOLD"));
    }

    @Test
    void fixEnableOffViaPropertiesAloneWithNoOverrideIsOff() {
        // Simulates fix.* properties-file entries disabling the fix, with no override merged on
        // top: the config's own map already carries the disabled entry.
        ValidationConfig config = new ValidationConfig(
                new HashMap<>(), new HashMap<>(), new HashMap<>(Map.of("PARAM_FIXTURE_FIX_RULE", false)));
        ValidationConfig merged = EffectiveRuleState.mergedConfig(config, Map.of(), Map.of(), Map.of());
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(merged, ParameterDescriptors.scan());
        assertTrue(offKeys.contains("PARAM_FIXTURE_FIX_RULE.THRESHOLD"));
    }

    @Test
    void enabledRulesAreNotReportedAsOff() {
        List<ParameterDescriptor> descriptors = ParameterDescriptors.scan();
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(emptyConfig(), descriptors);
        assertFalse(offKeys.contains("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
        assertFalse(offKeys.contains("PARAM_FIXTURE_FIX_RULE.THRESHOLD"));
    }
}
