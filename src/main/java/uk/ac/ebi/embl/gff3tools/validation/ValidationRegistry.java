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

public class ValidationRegistry {
    private static final ValidationRegistry INSTANCE = new ValidationRegistry();
    private static final Logger LOG = LoggerFactory.getLogger(ValidationRegistry.class);
    private static volatile List<ValidatorDescriptor> cachedValidators;

    private Connection connection;

    private ValidationRegistry() {}

    public static ValidationRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Creates list of validations and fixes
     */
    public void initRegistry(ValidationConfig validationConfig) {
        synchronized (ValidationRegistry.class) {
            cachedValidators = build(getValidationList(), validationConfig);
        }
    }

    private List<ValidatorDescriptor> build(List<ClassInfo> validationList, ValidationConfig validationConfig) {
        List<ValidatorDescriptor> descriptors = new ArrayList<>();

        for (ClassInfo classInfo : validationList) {
            Class<?> clazz = classInfo.loadClass();

            Annotation vmeta = getValidationAnnotation(clazz);
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
                ((Validation) instance).setConnection(connection);

                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(ValidationMethod.class)
                            || method.isAnnotationPresent(FixMethod.class)) {
                        method.setAccessible(true);
                        descriptors.add(new ValidatorDescriptor(clazz, instance, method));
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("Failed to initialize validator {}: {}", clazz.getName(), e.getMessage()));
            }
        }

        LOG.info("Initialized {} validator methods", descriptors.size());
        return descriptors;
    }

    private Annotation getValidationAnnotation(Class<?> clazz) {
        for (Class<? extends Annotation> type : List.of(Gff3Validation.class, Gff3Fix.class)) {
            if (clazz.isAnnotationPresent(type)) {
                return clazz.getAnnotation(type);
            }
        }
        return null;
    }

    private List<ClassInfo> getValidationList() {
        ClassInfoList validationList = new ClassInfoList();
        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .enableAllInfo()
                .whitelistPackages(ValidationRegistry.class.getPackage().getName())
                // .rejectClasses("*$*")
                .scan()) {

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
    public static List<ValidatorDescriptor> getValidations(ValidationConfig config) {
        return cachedValidators.stream()
                .filter(vd -> vd.clazz().isAnnotationPresent(Gff3Validation.class))
                .collect(Collectors.toList());
    }

    /**
     * Returns only classes annotated with @FixClass.
     */
    public static List<ValidatorDescriptor> getFixs(ValidationConfig config) {
        return cachedValidators.stream()
                .filter(vd -> vd.clazz().isAnnotationPresent(Gff3Fix.class))
                .collect(Collectors.toList());
    }

    private void checkUniqueValidationRules(ClassInfoList validationList) {
        Set<String> ruleNames = new HashSet<>();
        Set<String> classNames = new HashSet<>();

        for (ClassInfo validator : validationList) {
            Class<?> clazz = validator.loadClass();
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(ValidationMethod.class)) {
                    ValidationMethod vm = method.getAnnotation(ValidationMethod.class);
                    String rule = vm.rule().trim();

                    // Skip empty rule
                    if (rule.isEmpty()) continue;

                    // Enforce uniqueness
                    if (!ruleNames.add(rule)) {
                        throw new RuntimeException("Duplicate validation rule detected: " + rule + " in class "
                                + validator.getClass().getName());
                    }
                }
            }

            if (!classNames.add(clazz.getName())) {
                throw new RuntimeException("Duplicate validation/Fix name detected: " + clazz.getName() + " in class "
                        + validator.getClass().getName());
            }
        }
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }
}
