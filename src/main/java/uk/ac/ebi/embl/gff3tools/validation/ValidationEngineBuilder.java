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
import java.sql.Connection;
import java.util.*;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidatorDescriptor;

public class ValidationEngineBuilder {

    private final ValidationConfig validationConfig;
    private ValidationRegistry validationRegistry;
    private Connection connection;
    private boolean failFast = false;
    private final List<ContextProvider<?>> providerOverrides = new ArrayList<>();

    public ValidationEngineBuilder() {

        // Loads default severity rules and validatorOverrides
        validationConfig = getValidationConfig();
    }

    public ValidationEngine build() {
        // Create a fresh registry for each build
        validationRegistry = new ValidationRegistry(validationConfig, connection);

        // Discover providers, apply overrides, create context, inject into instances
        ValidationContext context = buildContext(validationRegistry);
        injectContext(validationRegistry, context);

        return new ValidationEngine(validationConfig, validationRegistry, context, failFast);
    }

    /**
     * Register a provider override. During build(), this provider replaces any
     * auto-discovered provider registered under the same class key.
     *
     * @param provider the provider instance to register as an override
     * @return this builder for chaining
     */
    public ValidationEngineBuilder withProvider(ContextProvider<?> provider) {
        providerOverrides.add(provider);
        return this;
    }

    public ValidationEngineBuilder failFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public ValidationEngineBuilder overrideMethodRules(Map<String, RuleSeverity> map) {
        this.validationConfig.getRuleOverrides().putAll(map);
        return this;
    }

    public ValidationEngineBuilder overrideMethodFixs(Map<String, Boolean> map) {
        this.validationConfig.getFixOverrides().putAll(map);
        return this;
    }

    public ValidationEngineBuilder overrideClassRules(Map<String, Boolean> map) {
        this.validationConfig.getValidatorOverrides().putAll(map);
        return this;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("unchecked")
    private ValidationContext buildContext(ValidationRegistry registry) {
        ValidationContext context = new ValidationContext();

        // Step 1: Auto-discover and register providers
        List<ContextProvider<?>> discovered = registry.discoverProviders();
        for (ContextProvider<?> provider : discovered) {
            context.register(
                    (Class<? extends ContextProvider<Object>>) provider.getClass(), (ContextProvider<Object>) provider);
        }

        // Step 2: Apply overrides (replace auto-discovered providers of the same class)
        for (ContextProvider<?> override : providerOverrides) {
            context.register(
                    (Class<? extends ContextProvider<Object>>) override.getClass(), (ContextProvider<Object>) override);
        }

        return context;
    }

    /**
     * Injects the ValidationContext into all validator/fix instances that extend Validation.
     */
    private void injectContext(ValidationRegistry registry, ValidationContext context) {
        Set<Object> injected = new HashSet<>();

        List<ValidatorDescriptor> allDescriptors = new ArrayList<>();
        allDescriptors.addAll(registry.getValidations());
        allDescriptors.addAll(registry.getFixs());
        allDescriptors.addAll(registry.getExits());

        for (ValidatorDescriptor descriptor : allDescriptors) {
            Object instance = descriptor.instance();
            if (instance instanceof Validation validation && injected.add(instance)) {
                validation.setContext(context);
            }
        }
    }

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
