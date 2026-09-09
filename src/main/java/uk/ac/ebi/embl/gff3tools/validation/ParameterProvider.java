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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A {@link ContextProvider} for {@link ResolvedParameters}, shaped the same way as {@code
 * CompositeSequenceProvider}: one class, one no-arg constructor auto-instantiated by {@code
 * ValidationRegistry}'s classpath scan (same as every other {@code ContextProvider}), mutated in
 * place by callers that need real caller-supplied values via {@link #configure}. {@code
 * ValidationEngineBuilder#withProvider} then overrides the auto-scanned instance by class key,
 * exactly as it does for {@code CompositeSequenceProvider}.
 *
 * <p>The no-arg constructor is trivial and never throws: it does not scan descriptors. A caller
 * that never calls {@link #configure} (e.g. a direct {@code ValidationRegistry}/{@code
 * ValidationEngineBuilder} user that bypasses {@code AbstractCommand}/the library helper) gets, on
 * first {@link #get}, a defaults-only snapshot built lazily and cached once — every declared
 * parameter resolves to its own default, and a mandatory parameter (having no default to fall
 * back on) is simply absent.
 *
 * <p>{@link #configure} is the explicit, direct call {@code AbstractCommand}/library callers make
 * before building the engine. It runs the five fail-fast checks from the spec's "Resolution and
 * fail-fast" section against the caller's raw {@code --params} map. Because it is called directly
 * — never through reflection — a malformed {@link Parameter} descriptor (bad/missing default,
 * duplicate key) propagates as an ordinary, uncaught {@link IllegalStateException}/{@code
 * DuplicateParameterException}: there is no reflective instantiation on this path for a {@code
 * catch (Exception e)} to silently swallow it behind.
 */
public class ParameterProvider implements ContextProvider<ResolvedParameters> {

    private final Supplier<List<ParameterDescriptor>> descriptorSupplier;
    private volatile ResolvedParameters resolved;
    private volatile boolean configured;

    public ParameterProvider() {
        this(ParameterDescriptors::scan);
    }

    /**
     * Package-private: lets tests substitute a fabricated descriptor list, exercising {@link
     * #configure} and the defaults-only fallback without depending on the shared classpath scan.
     */
    ParameterProvider(Supplier<List<ParameterDescriptor>> descriptorSupplier) {
        this.descriptorSupplier = descriptorSupplier;
    }

    /**
     * Returns {@code true} once {@link #configure} has been called on this instance. Unlike {@link
     * #resolved} being non-null, this is never set by {@link #get}'s lazy defaults-only fallback,
     * so it reflects only an explicit {@link #configure} call.
     */
    public boolean isConfigured() {
        return configured;
    }

    /**
     * Resolves the caller's raw {@code --params} map against every declared {@link Parameter},
     * running the five fail-fast checks, and stores the result on this instance. Callable exactly
     * once per instance: build a new instance rather than reconfiguring one already configured.
     *
     * @param rawParams the raw {@code key:value} map (upper-cased on comparison, values passed
     *     through verbatim); pass an empty map when {@code --params} was not supplied
     * @param effectiveConfig the effective {@link ValidationConfig}, reflecting the caller's
     *     {@code --rules}/fix overrides merged over the properties-file defaults, used to resolve
     *     OFF-rule detection
     * @throws ParameterResolutionException on any fail-fast violation (unknown key, key for an
     *     OFF rule/fix, missing mandatory, bad type)
     * @throws IllegalStateException if this instance was already configured
     */
    public synchronized void configure(Map<String, String> rawParams, ValidationConfig effectiveConfig)
            throws ParameterResolutionException {
        if (configured) {
            throw new IllegalStateException("ParameterProvider.configure() was already called on this instance; "
                    + "build a new instance instead of reconfiguring one already configured");
        }
        this.resolved = resolveExplicit(descriptorSupplier.get(), rawParams, effectiveConfig);
        this.configured = true;
    }

    @Override
    public synchronized ResolvedParameters get(ValidationContext context) {
        if (resolved == null) {
            resolved = resolveDefaultsOnly(descriptorSupplier.get());
        }
        return resolved;
    }

    @Override
    public Class<ResolvedParameters> type() {
        return ResolvedParameters.class;
    }

    /**
     * Builds a defaults-only snapshot from each descriptor's declared default. A mandatory
     * descriptor is never resolved here, regardless of whether it also declares a {@code
     * defaultValue} (a value is never mandatory-and-defaulted at once).
     */
    private static ResolvedParameters resolveDefaultsOnly(List<ParameterDescriptor> descriptors) {
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
        return new ResolvedParameters(values);
    }

    private static ResolvedParameters resolveExplicit(
            List<ParameterDescriptor> descriptors, Map<String, String> rawParams, ValidationConfig effectiveConfig)
            throws ParameterResolutionException {
        Map<String, ParameterDescriptor> byKey = new HashMap<>();
        for (ParameterDescriptor descriptor : descriptors) {
            byKey.put(descriptor.key(), descriptor);
        }

        Set<String> offRuleKeys = EffectiveRuleState.computeOffKeys(effectiveConfig, descriptors);

        Map<String, String> normalizedRaw = new HashMap<>();
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            normalizedRaw.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
        }

        Set<String> normalizedOffKeys = new HashSet<>();
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

        return new ResolvedParameters(values);
    }
}
