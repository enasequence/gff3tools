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
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.DuplicateParameterException;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

class ParameterProviderTest {

    private static ValidationConfig defaultConfig() {
        return new ValidationConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private static ParameterDescriptor descriptor(
            String rule, String name, ParameterType type, boolean mandatory, String defaultValue) {
        return new ParameterDescriptor(
                rule + "." + name,
                rule,
                name,
                type,
                "desc",
                mandatory,
                defaultValue,
                ParameterProviderTest.class,
                false,
                RuleSeverity.ERROR,
                true);
    }

    // ── construction is trivial and never throws ────────────────────────────────────────

    @Test
    void noArgConstructorNeverThrows() {
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) ParameterProvider::new);
    }

    @Test
    void freshInstanceIsNotConfigured() {
        assertFalse(new ParameterProvider().isConfigured());
    }

    // ── get() without configure() falls back to declared defaults, lazily ───────────────

    @Test
    void getWithoutConfigureFallsBackToDeclaredDefaults() {
        ParameterDescriptor optional =
                descriptor("PARAM_FIXTURE_LONG", "MIN_AMINO_ACIDS", ParameterType.LONG, false, "25");
        ParameterDescriptor stringParam = descriptor("PARAM_FIXTURE_STRING", "LABEL", ParameterType.STRING, false, "");
        ParameterDescriptor mandatory =
                descriptor("PARAM_FIXTURE_MANDATORY", "MANDATORY_LABEL", ParameterType.STRING, true, "");
        ParameterProvider provider = new ParameterProvider(() -> List.of(optional, stringParam, mandatory));

        ResolvedParameters resolved = provider.get(null);

        assertEquals(25L, resolved.getLong("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
        assertEquals("", resolved.getString("PARAM_FIXTURE_STRING.LABEL"));
        // Mandatory descriptor with no default is simply not resolved by the defaults-only fallback.
        assertThrows(
                IllegalArgumentException.class, () -> resolved.getString("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL"));
        assertFalse(provider.isConfigured());
        assertEquals(ResolvedParameters.class, provider.type());
    }

    @Test
    void defaultsOnlyFallbackIgnoresDefaultValueWhenMandatory() {
        ParameterDescriptor mandatoryWithDefault =
                descriptor("MANDATORY_WITH_DEFAULT", "LABEL", ParameterType.STRING, true, "some-default");
        ParameterProvider provider = new ParameterProvider(() -> List.of(mandatoryWithDefault));

        ResolvedParameters resolved = provider.get(null);

        assertThrows(IllegalArgumentException.class, () -> resolved.getString("MANDATORY_WITH_DEFAULT.LABEL"));
    }

    @Test
    void getIsLazyAndCachedAcrossRepeatedCallsWithoutConfigure() {
        int[] scanCount = {0};
        ParameterProvider provider = new ParameterProvider(() -> {
            scanCount[0]++;
            return List.of();
        });

        provider.get(null);
        provider.get(null);

        assertEquals(1, scanCount[0]);
    }

    // ── configure() runs the five fail-fast checks directly ─────────────────────────────

    @Test
    void configureMissingMandatoryFailsFast() {
        ParameterDescriptor mandatory = descriptor("FIXTURE_MANDATORY", "LABEL", ParameterType.STRING, true, "");
        ParameterProvider provider = new ParameterProvider(() -> List.of(mandatory));

        assertThrows(ParameterResolutionException.class, () -> provider.configure(Map.of(), defaultConfig()));
    }

    @Test
    void configureUnknownKeyFailsFast() {
        ParameterDescriptor known = descriptor("FIXTURE_KNOWN", "LABEL", ParameterType.STRING, false, "x");
        ParameterProvider provider = new ParameterProvider(() -> List.of(known));

        Map<String, String> raw = Map.of("NOT_A_REAL_RULE.NOT_A_REAL_PARAM", "x");
        assertThrows(ParameterResolutionException.class, () -> provider.configure(raw, defaultConfig()));
    }

    @Test
    void configureBadTypeCoercionFailsFast() {
        ParameterDescriptor longParam = descriptor("FIXTURE_LONG", "MIN", ParameterType.LONG, false, "25");
        ParameterProvider provider = new ParameterProvider(() -> List.of(longParam));

        Map<String, String> raw = Map.of("FIXTURE_LONG.MIN", "not-a-number");
        assertThrows(ParameterResolutionException.class, () -> provider.configure(raw, defaultConfig()));
    }

    @Test
    void configureKeyForOffToggledRuleFailsFast() {
        ParameterDescriptor longParam = descriptor("FIXTURE_LONG", "MIN", ParameterType.LONG, false, "25");
        ParameterProvider provider = new ParameterProvider(() -> List.of(longParam));

        ValidationConfig config = new ValidationConfig(
                new HashMap<>(Map.of("FIXTURE_LONG", RuleSeverity.OFF)), new HashMap<>(), new HashMap<>());
        Map<String, String> raw = Map.of("FIXTURE_LONG.MIN", "30");
        assertThrows(ParameterResolutionException.class, () -> provider.configure(raw, config));
    }

    @Test
    void configureOptionalUnsuppliedUsesDefault() throws ParameterResolutionException {
        ParameterDescriptor longParam = descriptor("FIXTURE_LONG", "MIN", ParameterType.LONG, false, "25");
        ParameterProvider provider = new ParameterProvider(() -> List.of(longParam));

        provider.configure(Map.of(), defaultConfig());

        assertTrue(provider.isConfigured());
        assertEquals(25L, provider.get(null).getLong("FIXTURE_LONG.MIN"));
    }

    @Test
    void configureKeysAreUpperCasedOnComparisonButValuesArePassedVerbatim() throws ParameterResolutionException {
        ParameterDescriptor mandatory = descriptor("FIXTURE_MANDATORY", "LABEL", ParameterType.STRING, true, "");
        ParameterDescriptor stringParam = descriptor("FIXTURE_STRING", "LABEL", ParameterType.STRING, false, "");
        ParameterProvider provider = new ParameterProvider(() -> List.of(mandatory, stringParam));

        Map<String, String> raw = Map.of("fixture_mandatory.label", "MixedCaseValue", "FIXTURE_STRING.LABEL", "a:b:c");
        provider.configure(raw, defaultConfig());

        ResolvedParameters resolved = provider.get(null);
        assertEquals("MixedCaseValue", resolved.getString("FIXTURE_MANDATORY.LABEL"));
        assertEquals("a:b:c", resolved.getString("FIXTURE_STRING.LABEL"));
    }

    @Test
    void configureTreatsMandatoryParamOnOffRuleAsNotRequired() throws ParameterResolutionException {
        ParameterDescriptor mandatory = descriptor("FIXTURE_MANDATORY", "LABEL", ParameterType.STRING, true, "");
        ParameterProvider provider = new ParameterProvider(() -> List.of(mandatory));

        ValidationConfig config = new ValidationConfig(
                new HashMap<>(Map.of("FIXTURE_MANDATORY", RuleSeverity.OFF)), new HashMap<>(), new HashMap<>());
        provider.configure(Map.of(), config);

        assertTrue(provider.isConfigured());
    }

    // ── configure() is callable exactly once per instance ────────────────────────────────

    @Test
    void configureCalledTwiceThrows() throws ParameterResolutionException {
        ParameterProvider provider = new ParameterProvider(List::of);
        provider.configure(Map.of(), defaultConfig());

        assertThrows(IllegalStateException.class, () -> provider.configure(Map.of(), defaultConfig()));
    }

    // ── malformed descriptor propagates through configure(), uncaught, no Error subclass ──

    @Test
    void configurePropagatesMalformedDescriptorScanDefectAsIllegalStateException() {
        ParameterProvider provider = new ParameterProvider(() -> {
            throw new IllegalStateException("malformed descriptor");
        });

        assertThrows(IllegalStateException.class, () -> provider.configure(Map.of(), defaultConfig()));
        assertFalse(provider.isConfigured());
    }

    @Test
    void configurePropagatesDuplicateParameterKeyDefect() {
        ParameterProvider provider = new ParameterProvider(() -> {
            throw new DuplicateParameterException("duplicate key");
        });

        assertThrows(DuplicateParameterException.class, () -> provider.configure(Map.of(), defaultConfig()));
    }

    // ── real classpath scan smoke test: proves the whole pipe end-to-end ─────────────────

    @Test
    void realScanEndToEndThroughConfigure() throws ParameterResolutionException {
        ParameterProvider provider = new ParameterProvider();
        // PARAM_FIXTURE_MANDATORY is OFF by default; explicitly turn it back on to prove the real
        // classpath-scanned mandatory descriptor is enforced end-to-end through configure().
        ValidationConfig config = new ValidationConfig(
                new HashMap<>(Map.of("PARAM_FIXTURE_MANDATORY", RuleSeverity.ERROR)), new HashMap<>(), new HashMap<>());
        Map<String, String> raw = Map.of("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL", "value");
        provider.configure(raw, config);

        ResolvedParameters resolved = provider.get(null);
        assertEquals(25L, resolved.getLong("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
        assertEquals("value", resolved.getString("PARAM_FIXTURE_MANDATORY.MANDATORY_LABEL"));
    }
}
