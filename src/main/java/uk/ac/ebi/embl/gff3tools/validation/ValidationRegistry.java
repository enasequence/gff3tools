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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.gff3tools.exception.DuplicateValidationRuleException;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

public class ValidationRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(ValidationRegistry.class);
    private final List<ValidatorDescriptor> cachedValidators;
    private final ValidationConfig validationConfig;
    private final ValidationContext context;

    private ValidationRegistry(Builder builder, List<ValidatorDescriptor> descriptors, ValidationContext context) {
        this.validationConfig = builder.config;
        this.cachedValidators = descriptors;
        this.context = context;
    }

    public ValidationContext getContext() {
        return context;
    }

    private List<ValidatorDescriptor> build(List<ClassInfo> validationList) {
        List<ValidatorDescriptor> descriptors = new ArrayList<>();

        for (ClassInfo classInfo : validationList) {
            Class<?> clazz = classInfo.loadClass();

            Annotation vmeta = getClassAnnotation(clazz);
            if (vmeta == null) {
                // no @Gff3Validation or @Gff3Fix
                continue;
            }

            // Checks validation is enabled
            boolean enabled = validationConfig.isValidatorEnabled(vmeta);
            if (!enabled) {
                continue;
            }

            try {
                // Instantiate once
                Object instance = clazz.getDeclaredConstructor().newInstance();

                // Set connection to all the validations and fixes

                for (Method method : clazz.getDeclaredMethods()) {
                    if (isMethodAnnotationPresent(method)) {
                        method.setAccessible(true);
                        descriptors.add(new ValidatorDescriptor(clazz, instance, method));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("Failed to initialize validator %s: %s", clazz.getName(), e.getMessage()));
            }
        }

        LOG.info("Initialized {} validator methods", descriptors.size());
        return descriptors;
    }

    /**
     * Discovers all concrete classes implementing ContextProvider via ClassGraph,
     * instantiates each via no-arg constructor, and returns them.
     */
    private List<ContextProvider<?>> discoverProviders() {
        List<ContextProvider<?>> providers = new ArrayList<>();
        try (ScanResult scan = new ClassGraph().enableClassInfo().scan()) {

            ClassInfoList providerClasses = scan.getClassesImplementing(ContextProvider.class.getName())
                    .filter(ci -> !ci.isAbstract()
                            && !ci.isInterface()
                            && !ci.getName().contains("$")
                            && !ci.isSynthetic());

            for (ClassInfo classInfo : providerClasses) {
                try {
                    Class<?> clazz = classInfo.loadClass();
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    providers.add((ContextProvider<?>) instance);
                    LOG.debug("Discovered context provider: {}", clazz.getName());
                } catch (Exception e) {
                    LOG.warn("Failed to instantiate context provider {}: {}", classInfo.getName(), e.getMessage());
                }
            }
        }
        LOG.info("Discovered {} context providers", providers.size());
        return providers;
    }

    private Annotation getClassAnnotation(Class<?> clazz) {
        for (Class<? extends Annotation> type : List.of(Gff3Validation.class, Gff3Fix.class)) {
            if (clazz.isAnnotationPresent(type)) {
                return clazz.getAnnotation(type);
            }
        }
        return null;
    }

    private boolean isMethodAnnotationPresent(Method method) {
        return method.isAnnotationPresent(ValidationMethod.class)
                || method.isAnnotationPresent(FixMethod.class)
                || method.isAnnotationPresent(ExitMethod.class);
    }

    private List<ClassInfo> getValidationList() {
        ClassInfoList validationList = new ClassInfoList();
        try (ScanResult scan =
                new ClassGraph().enableClassInfo().enableAnnotationInfo().scan()) {

            //  collect @ValidationClass annotated classes
            validationList.addAll(scan.getClassesWithAnnotation(Gff3Validation.class.getName())
                    .filter(ci -> !ci.getName().contains("$") && !ci.isSynthetic()));

            //  collect @FixClass annotated classes
            validationList.addAll(scan.getClassesWithAnnotation(Gff3Fix.class.getName())
                    .filter(ci -> !ci.getName().contains("$") && !ci.isSynthetic()));

            checkUniqueValidationRules(validationList);
            return validationList;
        }
    }

    /**
     * Returns only classes annotated with @ValidationClass.
     */
    public List<ValidatorDescriptor> getValidations() {
        return cachedValidators.stream()
                .filter(vd -> vd.clazz().isAnnotationPresent(Gff3Validation.class))
                .filter(vd -> vd.method().isAnnotationPresent(ValidationMethod.class))
                .collect(Collectors.toList());
    }

    /**
     * Returns only classes annotated with @FixClass.
     */
    public List<ValidatorDescriptor> getFixs() {
        return cachedValidators.stream()
                .filter(vd -> vd.clazz().isAnnotationPresent(Gff3Fix.class))
                .filter(vd -> vd.method().isAnnotationPresent(FixMethod.class))
                .collect(Collectors.toList());
    }

    public List<ValidatorDescriptor> getExits() {
        return cachedValidators.stream()
                .filter(vd -> vd.method().isAnnotationPresent(ExitMethod.class))
                .collect(Collectors.toList());
    }

    private void checkUniqueValidationRules(ClassInfoList validationList) {
        Set<String> ruleNames = new HashSet<>();
        Set<String> classNames = new HashSet<>();

        for (ClassInfo validator : validationList) {
            Class<?> clazz = validator.loadClass();
            for (Method method : clazz.getDeclaredMethods()) {
                Annotation vm;
                if ((vm = getMethodAnnotation(method)) != null) {
                    String rule = getRule(vm);

                    // Skip empty rule
                    if (rule.isEmpty()) continue;

                    // Enforce uniqueness
                    if (!ruleNames.add(rule)) {
                        throw new DuplicateValidationRuleException("Duplicate validation rule detected: " + rule
                                + " in class " + validator.getClass().getName());
                    }
                }
            }

            if (!classNames.add(clazz.getName())) {
                throw new DuplicateValidationRuleException("Duplicate validation/Fix name detected: " + clazz.getName()
                        + " in class " + validator.getClass().getName());
            }
        }
    }

    private Annotation getMethodAnnotation(Method method) {
        for (Class<? extends Annotation> type : List.of(ValidationMethod.class, FixMethod.class)) {
            if (method.isAnnotationPresent(type)) {
                return method.getAnnotation(type);
            }
        }
        return null;
    }

    private String getRule(Annotation method) {

        String rule = "";
        if (method instanceof ValidationMethod) {
            rule = ((ValidationMethod) method).rule();
        } else if (method instanceof FixMethod) {
            rule = ((FixMethod) method).rule();
        }

        return rule;
    }

    public static class Builder {
        private final ValidationConfig config;
        private Connection connection;
        private final List<ContextProvider<?>> providerOverrides = new ArrayList<>();

        public Builder(ValidationConfig config) {
            this.config = config;
        }

        public Builder connection(Connection connection) {
            this.connection = connection;
            return this;
        }

        public Builder withProvider(ContextProvider<?> provider) {
            providerOverrides.add(provider);
            return this;
        }

        public ValidationRegistry build() {
            // Use a temporary registry instance to access the private build() and getValidationList()
            // methods (static inner class may access private members of enclosing class instances)
            ValidationRegistry temp = new ValidationRegistry(this, null, null);
            List<ValidatorDescriptor> descriptors = temp.build(temp.getValidationList());

            ValidationContext context = buildContext(temp.discoverProviders());
            injectContext(descriptors, context);

            return new ValidationRegistry(this, descriptors, context);
        }

        @SuppressWarnings("unchecked")
        private ValidationContext buildContext(List<ContextProvider<?>> discovered) {
            ValidationContext context = new ValidationContext();

            for (ContextProvider<?> provider : discovered) {
                context.register(
                        (Class<? extends ContextProvider<Object>>) provider.getClass(),
                        (ContextProvider<Object>) provider);
            }

            for (ContextProvider<?> override : providerOverrides) {
                context.register(
                        (Class<? extends ContextProvider<Object>>) override.getClass(),
                        (ContextProvider<Object>) override);
            }

            return context;
        }

        private void injectContext(List<ValidatorDescriptor> descriptors, ValidationContext context) {
            Set<Object> injected = new HashSet<>();
            for (ValidatorDescriptor descriptor : descriptors) {
                Object instance = descriptor.instance();
                if (instance instanceof Validation validation && injected.add(instance)) {
                    validation.setContext(context);
                }
            }
        }
    }
}
