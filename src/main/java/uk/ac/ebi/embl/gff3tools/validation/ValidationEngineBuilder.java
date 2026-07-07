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
import java.util.*;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;

public class ValidationEngineBuilder {

    private final ValidationConfig validationConfig;
    private boolean failFast = false;
    private final List<ContextProvider<?>> providerOverrides = new ArrayList<>();
    private final Set<Class<?>> excludedProviderTypes = new HashSet<>();
    private boolean providerClasspathScanningEnabled = true;
    private boolean validationClassPathScanningEnabled = true;
    private final List<Fix> fixOverrides = new ArrayList<>();
    private final List<Validation> validatorOverrides = new ArrayList<>();

    public ValidationEngineBuilder() {

        // Loads default severity rules from properties
        validationConfig = getValidationConfig();
    }

    /**
     * Assembles the configured registry and returns a ready-to-use {@link ValidationEngine}.
     * Applies provider overrides/exclusions, classpath-scanning toggles, explicit fixes and
     * validators, and the loaded severity configuration. Call once per engine.
     *
     * @return a new validation engine built from this builder's current state
     */
    public ValidationEngine build() {
        ValidationRegistry registry = ValidationRegistry.builder()
                .config(validationConfig)
                .providers(providerOverrides)
                .excludedProviderTypes(excludedProviderTypes)
                .contextProviderClassPathScanningEnabled(providerClasspathScanningEnabled)
                .validationClassPathScanningEnabled(validationClassPathScanningEnabled)
                .fixes(fixOverrides)
                .validators(validatorOverrides)
                .build();
        return new ValidationEngine(validationConfig, registry, registry.getContext(), failFast);
    }

    /**
     * Register a provider override. During build(), this provider replaces any
     * auto-discovered provider registered under the same class key.
     *
     * @param provider the provider instance to register as an override
     * @return this builder for chaining
     */
    public ValidationEngineBuilder withProvider(ContextProvider<?> provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        providerOverrides.add(provider);
        return this;
    }

    /**
     * Disable classpath scanning. When disabled, only explicitly registered
     * context providers participate in the engine.
     *
     * @return this builder for chaining
     */
    public ValidationEngineBuilder disableAutodetectContextProviders() {
        this.providerClasspathScanningEnabled = false;
        return this;
    }

    /**
     * Exclude a context provider type from the engine. The given type is never registered on the
     * context, even if it would otherwise be autodetected via classpath scanning or supplied via
     * {@link #withProvider(ContextProvider)}.
     *
     * <p>Exclusion always wins: the type is removed regardless of what a detected provider reports
     * via {@link ContextProvider#isActive()}, so an active provider is dropped just the same as an
     * inactive one.
     *
     * <p>As a result, {@code validationContext.contains(type)} returns {@code false}, so rules that guard on
     * the provider's presence become inert.
     *
     * @param providerType the provider value type to exclude (e.g. {@code StringProvider.class})
     * @return this builder for chaining
     */
    public ValidationEngineBuilder excludeProvider(Class<?> providerType) {
        Objects.requireNonNull(providerType, "providerType must not be null");
        excludedProviderTypes.add(providerType);
        return this;
    }

    /**
     * Disable classpath scanning. When disabled, only explicitly registered
     * fixes and validators (classes annotated with Gff3Validation/Gff3Fix) participate in the engine.
     *
     * @return this builder for chaining
     */
    public ValidationEngineBuilder disableAutodetectValidationsAndFixes() {
        this.validationClassPathScanningEnabled = false;
        return this;
    }

    /**
     * Register an explicit fix instance. The instance must be annotated with
     * {@code @Gff3Fix} and implement the {@link Fix} marker interface.
     *
     * @param instance the fix instance to register
     * @return this builder for chaining
     */
    public ValidationEngineBuilder withFix(Fix instance) {
        Objects.requireNonNull(instance, "fix instance must not be null");
        fixOverrides.add(instance);
        return this;
    }

    /**
     * Register an explicit validator instance. The instance must be annotated with
     * {@code @Gff3Validation} and implement the {@link Validation} marker interface.
     *
     * @param instance the validator instance to register
     * @return this builder for chaining
     */
    public ValidationEngineBuilder withValidator(Validation instance) {
        Objects.requireNonNull(instance, "validator instance must not be null");
        validatorOverrides.add(instance);
        return this;
    }

    /**
     * Sets the fail-fast policy for the built engine. When {@code true}, the engine stops at the
     * first error instead of collecting all of them before reporting.
     *
     * @param failFast whether to abort on the first error
     * @return this builder for chaining
     */
    public ValidationEngineBuilder failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    /**
     * Overrides the severity of individual validation rules. Keys are matched against the
     * {@code @ValidationMethod.rule()} of each validation method, so the override targets a single
     * method. Entries are merged over the defaults loaded from
     * {@code default-rule-severities.properties}, so only the supplied rules change.
     *
     * <p>This filters at execution: the validation is still registered, but {@link ValidationEngine}
     * consults the severity on every run, skipping the rule when it is {@code OFF} and otherwise
     * governing how a violation is reported.
     *
     * @param map {@code @ValidationMethod.rule()} to severity overrides
     * @return this builder for chaining
     */
    public ValidationEngineBuilder overrideMethodRules(Map<String, RuleSeverity> map) {
        this.validationConfig.getRuleOverrides().putAll(map);
        return this;
    }

    /**
     * Toggles individual fixes on or off. Keys are matched against the {@code @FixMethod.rule()} of
     * each fix method, so the override targets a single method. Entries are merged over the
     * defaults, so only the supplied fixes change.
     *
     * <p>This filters at execution: the fix is still registered, but {@link ValidationEngine}
     * skips it or includes it on every run.
     *
     * @param map {@code @FixMethod.rule()} to enabled flag overrides
     * @return this builder for chaining
     */
    public ValidationEngineBuilder overrideMethodFixs(Map<String, Boolean> map) {
        this.validationConfig.getFixOverrides().putAll(map);
        return this;
    }

    /**
     * Toggles whole validator/fix classes on or off. Keys are matched against the class-level
     * {@code @Gff3Validation.name()} / {@code @Gff3Fix.name()}, so a single key disables every
     * method that class declares. Entries are merged over the defaults, so only the supplied
     * classes change.
     *
     * <p>Note this is a different namespace from {@link #overrideMethodRules(Map)} and
     * {@link #overrideMethodFixs(Map)}, which key on per-method rules. For a single-method
     * validator the class name and its method rule are the same string by convention, but for a
     * multi-method class they differ.
     *
     * <p>This filters at registration: a disabled class is never built into a descriptor at all.
     *
     * @param map class-level {@code @Gff3Validation.name()} / {@code @Gff3Fix.name()} to enabled flag overrides
     * @return this builder for chaining
     */
    public ValidationEngineBuilder overrideClassRules(Map<String, Boolean> map) {
        this.validationConfig.getValidatorOverrides().putAll(map);
        return this;
    }

    /**
     * Loads the default {@link ValidationConfig} from {@code default-rule-severities.properties} on
     * the classpath. Keys are routed by prefix: {@code rule.*} to severities, {@code fix.*} to fix
     * toggles, and {@code class.*} to validator-class toggles.
     *
     * @return the default validation configuration
     */
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
}
