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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ParameterProviderTest {

    @Test
    void noArgConstructorNeverThrowsAndUsesDeclaredDefaults() {
        ParameterProvider provider = new ParameterProvider();
        ResolvedParameters resolved = provider.get(null);

        assertEquals(25L, resolved.getLong("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
        assertEquals("", resolved.getString("PARAM_FIXTURE_STRING.LABEL"));
        // Mandatory descriptor with no default is simply not resolved by the no-arg constructor.
        assertThrows(
                IllegalArgumentException.class, () -> resolved.getString("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL"));

        assertEquals(ResolvedParameters.class, provider.type());
    }

    @Test
    void noArgConstructorIgnoresDefaultValueWhenMandatory() {
        ParameterDescriptor mandatoryWithDefault = new ParameterDescriptor(
                "MANDATORY_WITH_DEFAULT.LABEL",
                "MANDATORY_WITH_DEFAULT",
                "LABEL",
                ParameterType.STRING,
                "desc",
                true,
                "some-default");
        ParameterProvider provider = new ParameterProvider(() -> List.of(mandatoryWithDefault));
        ResolvedParameters resolved = provider.get(null);

        assertThrows(IllegalArgumentException.class, () -> resolved.getString("MANDATORY_WITH_DEFAULT.LABEL"));
    }

    @Test
    void noArgConstructorWrapsMalformedDescriptorScanAsError() throws Exception {
        Method badMethod = BadFixture.class.getDeclaredMethod("badDefault");
        Parameter badParam = badMethod.getAnnotation(Parameter.class);

        assertThrows(
                ParameterDescriptorDefectError.class,
                () -> new ParameterProvider(() -> {
                    ParameterDescriptors.buildDescriptor("BAD_RULE", badParam, BadFixture.class, badMethod);
                    return List.of();
                }));
    }

    private static final class BadFixture {
        @Parameter(name = "BAD_DEFAULT", type = ParameterType.LONG, defaultValue = "not-a-number")
        void badDefault() {}
    }

    @Test
    void rawMapConstructorMissingMandatoryFailsFast() {
        assertThrows(ParameterResolutionException.class, () -> new ParameterProvider(Map.of(), Set.of()));
    }

    @Test
    void rawMapConstructorUnknownKeyFailsFast() {
        Map<String, String> raw = Map.of(
                "PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL", "value",
                "NOT_A_REAL_RULE.NOT_A_REAL_PARAM", "x");
        assertThrows(ParameterResolutionException.class, () -> new ParameterProvider(raw, Set.of()));
    }

    @Test
    void rawMapConstructorBadTypeCoercionFailsFast() {
        Map<String, String> raw = Map.of(
                "PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL", "value",
                "PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS", "not-a-number");
        assertThrows(ParameterResolutionException.class, () -> new ParameterProvider(raw, Set.of()));
    }

    @Test
    void rawMapConstructorKeyForOffToggledRuleFailsFast() {
        Map<String, String> raw = Map.of(
                "PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL", "value",
                "PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS", "30");
        Set<String> offKeys = Set.of("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS");
        assertThrows(ParameterResolutionException.class, () -> new ParameterProvider(raw, offKeys));
    }

    @Test
    void rawMapConstructorOptionalFallsBackToDefault() throws ParameterResolutionException {
        Map<String, String> raw = Map.of("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL", "value");
        ParameterProvider provider = new ParameterProvider(raw, Set.of());
        ResolvedParameters resolved = provider.get(null);

        assertEquals(25L, resolved.getLong("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
        assertEquals("value", resolved.getString("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL"));
    }

    @Test
    void rawMapConstructorKeysAreUpperCasedOnComparisonButValuesArePassedVerbatim()
            throws ParameterResolutionException {
        Map<String, String> raw = Map.of(
                "param_fixture_mandatory.mandatory_label", "MixedCaseValue",
                "PARAM_FIXTURE_STRING.LABEL", "a:b:c");
        ParameterProvider provider = new ParameterProvider(raw, Set.of());
        ResolvedParameters resolved = provider.get(null);

        assertEquals("MixedCaseValue", resolved.getString("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL"));
        assertEquals("a:b:c", resolved.getString("PARAM_FIXTURE_STRING.LABEL"));
    }
}
