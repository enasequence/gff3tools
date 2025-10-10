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

import uk.ac.ebi.embl.gff3tools.validation.builtin.*;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.gff3tools.validation.builtin.DuplicateFeatureValidation;
import uk.ac.ebi.embl.gff3tools.validation.builtin.LengthValidation;
import uk.ac.ebi.embl.gff3tools.validation.builtin.LocationValidation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ValidationRegistry {
    private static final Validation[] VALIDATIONS = new Validation[] {
        // new DuplicateSeqIdValidation(),
        new OntologyValidation(), new LocationValidation(), new LengthValidation(), new DuplicateFeatureValidation(),
    };

    public static Validation[] getValidations() {
        return VALIDATIONS;
    }

    private static final Logger LOG = LoggerFactory.getLogger(ValidationRegistry.class);
    private static volatile List<ValidatorDescriptor> cache;

    private ValidationRegistry() {}

    public static List<ValidatorDescriptor> getValidators(ValidationConfig validationConfig) {

        if (cache == null) {
            synchronized (ValidationRegistry.class) {
                if (cache == null) {
                    cache = build(getValidationList(), validationConfig);
                }
            }
        }
        return cache;
    }

    public static void clearRegistry() {
        cache = null;
    }

    private static List<ClassInfo>  getValidationList(){
        ClassInfoList validationList;
        try (ScanResult scan = new ClassGraph().enableClassInfo().enableAllInfo()
                .whitelistPackages(ValidationRegistry.class.getPackage().getName())
                .scan()
        ) {

            validationList = scan.getClassesWithAnnotation(ValidationClass.class.getName());
            checkUniqueValidationRules(validationList);
            return validationList;
        }
    }


public static void checkUniqueValidationRules(ClassInfoList validationList) {
    Set<String> ruleNames = new HashSet<>();

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
                    throw new IllegalStateException("Duplicate validation rule detected: " + rule +
                            " in class " + validator.getClass().getName());
                }
            }
        }
    }
}

    private static List<ValidatorDescriptor> build(List<ClassInfo> validationList,
                                                   ValidationConfig validationConfig) {
        List<ValidatorDescriptor> descriptors = new ArrayList<>();

        for (ClassInfo classInfo : validationList) {
            Class<?> clazz = classInfo.loadClass();

            if (!clazz.isAnnotationPresent(ValidationClass.class)) continue;
            ValidationClass vmeta = clazz.getAnnotation(ValidationClass.class);
            boolean enabled = validationConfig.isValidatorEnabled(vmeta.name(), vmeta.enabled());
            if (!enabled) continue;

            try {
                // Instantiate once
                Object instance = clazz.getDeclaredConstructor().newInstance();

                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(ValidationMethod.class)) {
                        ValidationMethod vm = method.getAnnotation(ValidationMethod.class);
                        method.setAccessible(true);
                        descriptors.add(new ValidatorDescriptor(instance, method));
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to initialize validator {}: {}", clazz.getName(), e.getMessage());
            }
        }

        LOG.info("Initialized {} validator methods", descriptors.size());
        return descriptors;
    }
}
