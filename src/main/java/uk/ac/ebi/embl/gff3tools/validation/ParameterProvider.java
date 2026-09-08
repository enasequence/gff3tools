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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dual-mode {@link ContextProvider} for {@link ResolvedParameters}.
 *
 * <p>The no-arg constructor builds a defaults-only instance, seeded purely from each declared
 * descriptor's default, and never throws — it is the instance {@code
 * ValidationRegistry.instantiateProviders()} auto-scans on any build that leaves classpath
 * provider scanning enabled, and is the fallback for direct {@code ValidationRegistry}/{@code
 * ValidationEngineBuilder} use that bypasses caller-side {@code --params} resolution.
 *
 * <p>The raw-map constructor is the explicit instance callers (CLI/library) always build before
 * the engine, running the five fail-fast checks from the spec's "Resolution and fail-fast"
 * section.
 */
public class ParameterProvider implements ContextProvider<ResolvedParameters> {

    private final ResolvedParameters resolved;

    /**
     * Builds a defaults-only instance from each descriptor's declared default. Never lets a
     * malformed descriptor propagate as an ordinary {@link Exception}: a scan-time defect (bad or
     * missing default on a non-STRING optional parameter, or a duplicate parameter key) is a
     * programmer error, not a recoverable runtime condition, so it is rethrown as a {@link
     * ParameterDescriptorDefectError}. This guarantees {@code
     * ValidationRegistry.instantiateProviders()}'s {@code catch (Exception e)} cannot silently
     * swallow it. A mandatory descriptor is never resolved here, regardless of whether it also
     * declares a {@code defaultValue} (a value is never mandatory-and-defaulted at once) — the
     * auto-instance is defaults-only and has nothing for a mandatory descriptor.
     */
    public ParameterProvider() {
        this(ParameterDescriptors::scan);
    }

    ParameterProvider(Supplier<List<ParameterDescriptor>> descriptorSupplier) {
        List<ParameterDescriptor> descriptors;
        try {
            descriptors = descriptorSupplier.get();
        } catch (RuntimeException e) {
            throw new ParameterDescriptorDefectError(
                    "Malformed @Parameter descriptor detected while building the defaults-only ParameterProvider", e);
        }

        Map<String, Object> values = new HashMap<>();
        for (ParameterDescriptor descriptor : descriptors) {
            if (descriptor.mandatory()) {
                continue;
            }
            if (descriptor.defaultValue().isEmpty()) {
                if (descriptor.type() == ParameterType.STRING) {
                    values.put(descriptor.key(), "");
                }
                continue;
            }
            values.put(descriptor.key(), descriptor.type().coerce(descriptor.defaultValue()));
        }
        this.resolved = new ResolvedParameters(values);
    }

    /**
     * Builds the explicit instance from the caller's raw {@code --params} map, running the five
     * fail-fast checks: unknown key, key for an OFF-toggled rule/fix, missing mandatory, bad
     * type, and optional-unsupplied-uses-default.
     *
     * @param rawParams the raw {@code key:value} map (upper-cased on comparison, values passed
     *     through verbatim); pass an empty map when {@code --params} was not supplied
     * @param offRuleKeys the set of {@code RULE.PARAM} keys whose owning rule/fix is effectively
     *     OFF; a minimal, explicit stand-in for phase 1 — phase 2 replaces the caller-supplied
     *     set with a real effective-config computation without changing the checks below
     */
    public ParameterProvider(Map<String, String> rawParams, Set<String> offRuleKeys)
            throws ParameterResolutionException {
        List<ParameterDescriptor> descriptors = ParameterDescriptors.scan();
        Map<String, ParameterDescriptor> byKey = new HashMap<>();
        for (ParameterDescriptor descriptor : descriptors) {
            byKey.put(descriptor.key(), descriptor);
        }

        Map<String, String> normalizedRaw = new HashMap<>();
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            normalizedRaw.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
        }

        Set<String> normalizedOffKeys = new java.util.HashSet<>();
        for (String key : offRuleKeys) {
            normalizedOffKeys.add(key.toUpperCase(Locale.ROOT));
        }

        // Check 1: unknown key.
        for (String key : normalizedRaw.keySet()) {
            if (!byKey.containsKey(key)) {
                throw new ParameterResolutionException("Unknown --params key: " + key);
            }
        }

        // Check 2: key for a toggled-OFF rule/fix.
        for (String key : normalizedRaw.keySet()) {
            if (normalizedOffKeys.contains(key)) {
                throw new ParameterResolutionException(
                        "--params key " + key + " belongs to a rule/fix that is toggled OFF");
            }
        }

        Map<String, Object> values = new HashMap<>();
        for (ParameterDescriptor descriptor : descriptors) {
            // An OFF rule needs no value for a parameter it will never read.
            if (normalizedOffKeys.contains(descriptor.key())) {
                continue;
            }

            String suppliedValue = normalizedRaw.get(descriptor.key());
            boolean supplied = suppliedValue != null && !suppliedValue.isEmpty();

            // Check 3: missing mandatory. A supplied but empty value is treated as missing.
            if (descriptor.mandatory() && !supplied) {
                throw new ParameterResolutionException("Missing mandatory --params value for " + descriptor.key());
            }

            String valueToCoerce = supplied ? suppliedValue : descriptor.defaultValue();

            // Check 4: bad type.
            try {
                values.put(descriptor.key(), descriptor.type().coerce(valueToCoerce));
            } catch (RuntimeException e) {
                throw new ParameterResolutionException("--params value for " + descriptor.key() + " does not coerce to "
                        + descriptor.type() + ": " + valueToCoerce);
            }
            // Check 5 (optional, unsupplied uses default) falls out of valueToCoerce above.
        }

        this.resolved = new ResolvedParameters(values);
    }

    @Override
    public ResolvedParameters get(ValidationContext context) {
        return resolved;
    }

    @Override
    public Class<ResolvedParameters> type() {
        return ResolvedParameters.class;
    }
}
