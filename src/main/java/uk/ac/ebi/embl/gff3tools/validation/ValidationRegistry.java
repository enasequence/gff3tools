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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Singular;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.gff3tools.exception.DuplicateValidationRuleException;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

public class ValidationRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(ValidationRegistry.class);

    private final List<ValidatorDescriptor> cachedValidators;
    private final Map<ValidationPriority, List<ValidatorDescriptor>> cachedValidationsByPriority;
    private final Map<ValidationPriority, List<ValidatorDescriptor>> cachedFixesByPriority;
    private final ValidationConfig validationConfig;
    private final ValidationContext context;

    private static final List<ClassInfo> cachedValidationList;
    private static final List<Class<? extends ContextProvider<?>>> cachedProviderClasses;

    static {
        LOG.info("Performing one-time classpath scan for validators and context providers");

        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .scan()) {

            ClassInfoList validationList = new ClassInfoList();
            validationList.addAll(scan.getClassesWithAnnotation(Gff3Validation.class.getName())
                    .filter(ci -> !ci.getName().contains("$") && !ci.isSynthetic()));
            validationList.addAll(scan.getClassesWithAnnotation(Gff3Fix.class.getName())
                    .filter(ci -> !ci.getName().contains("$") && !ci.isSynthetic()));

            checkUniqueValidationRules(validationList);
            cachedValidationList = List.copyOf(validationList);

            List<Class<? extends ContextProvider<?>>> providerClasses = new ArrayList<>();

            ClassInfoList providerInfos = scan.getClassesImplementing(ContextProvider.class.getName())
                    .filter(ci -> !ci.isAbstract()
                            && !ci.isInterface()
                            && !ci.getName().contains("$")
                            && !ci.isSynthetic());

            for (ClassInfo classInfo : providerInfos) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<? extends ContextProvider<?>> clazz =
                            (Class<? extends ContextProvider<?>>) classInfo.loadClass();
                    providerClasses.add(clazz);
                } catch (Exception e) {
                    LOG.warn("Failed to load context provider class {}: {}", classInfo.getName(), e.getMessage());
                }
            }

            cachedProviderClasses = List.copyOf(providerClasses);

            invokeStartupMethods(scan);
        }

        LOG.info(
                "Classpath scan complete: {} validator/fix classes, {} context provider classes",
                cachedValidationList.size(),
                cachedProviderClasses.size());
    }

    /** Invokes all the @StartupMethod before executing the validations. The @StartupMethod will be defined in gff3-validations*/
    private static void invokeStartupMethods(ScanResult scan) {
        scan.getClassesWithMethodAnnotation(StartupMethod.class.getName()).forEach(classInfo -> {
            try {
                Class<?> clazz = classInfo.loadClass();
                for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(StartupMethod.class)) {
                        method.setAccessible(true);
                        Object receiver = java.lang.reflect.Modifier.isStatic(method.getModifiers())
                                ? null
                                : clazz.getDeclaredConstructor().newInstance();
                        method.invoke(receiver);
                        LOG.info("Startup method invoked: {}.{}", clazz.getName(), method.getName());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke startup method in " + classInfo.getName(), e);
            }
        });
    }

    @Builder
    @SuppressWarnings("unchecked")
    private ValidationRegistry(ValidationConfig config, @Singular List<ContextProvider<?>> providers) {
        this.validationConfig = config;

        ValidationContext ctx = new ValidationContext();
        for (ContextProvider<?> provider : instantiateProviders()) {
            ctx.register((Class<Object>) provider.type(), (ContextProvider<Object>) provider);
        }
        for (ContextProvider<?> provider : providers) {
            ctx.register((Class<Object>) provider.type(), (ContextProvider<Object>) provider);
        }

        this.context = ctx;
        this.cachedValidators = buildDescriptors(cachedValidationList, ctx, config);
        this.cachedValidationsByPriority = cachedValidators.stream()
                .filter(vd -> vd.clazz().isAnnotationPresent(Gff3Validation.class))
                .filter(vd -> vd.method().isAnnotationPresent(ValidationMethod.class))
                .collect(Collectors.groupingBy(ValidatorDescriptor::priority, Collectors.toUnmodifiableList()));
        this.cachedFixesByPriority = cachedValidators.stream()
                .filter(vd -> vd.clazz().isAnnotationPresent(Gff3Fix.class))
                .filter(vd -> vd.method().isAnnotationPresent(FixMethod.class))
                .collect(Collectors.groupingBy(ValidatorDescriptor::priority, Collectors.toUnmodifiableList()));
    }

    public ValidationContext getContext() {
        return context;
    }

    private static List<ValidatorDescriptor> buildDescriptors(
            List<ClassInfo> validationList, ValidationContext context, ValidationConfig config) {
        List<ValidatorDescriptor> descriptors = new ArrayList<>();

        for (ClassInfo classInfo : validationList) {
            Class<?> clazz = classInfo.loadClass();

            Annotation vmeta = getClassAnnotation(clazz);
            if (vmeta == null) {
                continue;
            }

            if (!config.isValidatorEnabled(vmeta)) {
                continue;
            }

            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                injectContext(instance, context);

                for (Method method : clazz.getDeclaredMethods()) {
                    if (isMethodAnnotationPresent(method)) {
                        method.setAccessible(true);
                        ValidationPriority priority = extractPriority(method);
                        descriptors.add(new ValidatorDescriptor(clazz, instance, method, priority));
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

    public static void injectContext(Object instance, ValidationContext context) {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(InjectContext.class)) {
                    field.setAccessible(true);
                    try {
                        field.set(instance, context);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(
                                "Failed to inject context into "
                                        + instance.getClass().getName(),
                                e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static List<ContextProvider<?>> instantiateProviders() {
        List<ContextProvider<?>> providers = new ArrayList<>();
        for (Class<? extends ContextProvider<?>> clazz : cachedProviderClasses) {
            try {
                providers.add(clazz.getDeclaredConstructor().newInstance());
                LOG.debug("Instantiated context provider: {}", clazz.getName());
            } catch (Exception e) {
                LOG.warn("Failed to instantiate context provider {}: {}", clazz.getName(), e.getMessage());
            }
        }
        LOG.info("Instantiated {} context providers", providers.size());
        return providers;
    }

    private static Annotation getClassAnnotation(Class<?> clazz) {
        for (Class<? extends Annotation> type : List.of(Gff3Validation.class, Gff3Fix.class)) {
            if (clazz.isAnnotationPresent(type)) {
                return clazz.getAnnotation(type);
            }
        }
        return null;
    }

    private static boolean isMethodAnnotationPresent(Method method) {
        return method.isAnnotationPresent(ValidationMethod.class)
                || method.isAnnotationPresent(FixMethod.class)
                || method.isAnnotationPresent(ExitMethod.class);
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

    /**
     * Returns validations grouped by priority.
     */
    public Map<ValidationPriority, List<ValidatorDescriptor>> getValidationsByPriority() {
        return cachedValidationsByPriority;
    }

    /**
     * Returns fixes grouped by priority.
     */
    public Map<ValidationPriority, List<ValidatorDescriptor>> getFixesByPriority() {
        return cachedFixesByPriority;
    }

    public List<ValidatorDescriptor> getExits() {
        return cachedValidators.stream()
                .filter(vd -> vd.method().isAnnotationPresent(ExitMethod.class))
                .collect(Collectors.toList());
    }

    private static void checkUniqueValidationRules(ClassInfoList validationList) {
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

    private static Annotation getMethodAnnotation(Method method) {
        for (Class<? extends Annotation> type : List.of(ValidationMethod.class, FixMethod.class)) {
            if (method.isAnnotationPresent(type)) {
                return method.getAnnotation(type);
            }
        }
        return null;
    }

    private static String getRule(Annotation method) {

        String rule = "";
        if (method instanceof ValidationMethod) {
            rule = ((ValidationMethod) method).rule();
        } else if (method instanceof FixMethod) {
            rule = ((FixMethod) method).rule();
        }

        return rule;
    }

    private static ValidationPriority extractPriority(Method method) {
        if (method.isAnnotationPresent(ValidationMethod.class)) {
            return method.getAnnotation(ValidationMethod.class).priority();
        } else if (method.isAnnotationPresent(FixMethod.class)) {
            return method.getAnnotation(FixMethod.class).priority();
        }
        return ValidationPriority.NORMAL;
    }
}
