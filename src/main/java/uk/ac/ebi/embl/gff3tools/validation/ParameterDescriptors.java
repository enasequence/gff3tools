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

import io.github.classgraph.ClassInfo;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.DuplicateParameterException;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;

/**
 * Statically scans {@link ValidationRegistry}'s existing one-time classpath scan (via {@link
 * ValidationRegistry#getScannedValidationClasses()}) for {@link Parameter}/{@link Parameters}
 * annotations on methods also annotated {@code @ValidationMethod}/{@code @FixMethod}. Purely
 * annotation inspection, no instantiation, and no second classpath scan.
 */
public final class ParameterDescriptors {

    private ParameterDescriptors() {}

    public static List<ParameterDescriptor> scan() {
        List<ParameterDescriptor> descriptors = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (ClassInfo classInfo : ValidationRegistry.getScannedValidationClasses()) {
            Class<?> clazz = classInfo.loadClass();
            for (Method method : clazz.getDeclaredMethods()) {
                String rule = extractRule(method);
                if (rule == null) {
                    continue;
                }
                for (Parameter parameter : method.getAnnotationsByType(Parameter.class)) {
                    ParameterDescriptor descriptor = buildDescriptor(rule, parameter, clazz, method);
                    if (!seenKeys.add(descriptor.key())) {
                        throw new DuplicateParameterException("Duplicate parameter key: " + descriptor.key());
                    }
                    descriptors.add(descriptor);
                }
            }
        }

        return List.copyOf(descriptors);
    }

    private static String extractRule(Method method) {
        ValidationMethod validationMethod = method.getAnnotation(ValidationMethod.class);
        if (validationMethod != null) {
            return validationMethod.rule();
        }
        FixMethod fixMethod = method.getAnnotation(FixMethod.class);
        if (fixMethod != null) {
            return fixMethod.rule();
        }
        return null;
    }

    // Package-private (not private) so tests can exercise the build-time defect check
    // directly, without registering a broken fixture class on the shared classpath scan.
    static ParameterDescriptor buildDescriptor(String rule, Parameter parameter, Class<?> clazz, Method method) {
        String key = (rule + "." + parameter.name()).toUpperCase(Locale.ROOT);

        if (!parameter.mandatory() && parameter.type() != ParameterType.STRING) {
            if (parameter.defaultValue().isEmpty()) {
                throw new IllegalStateException(String.format(
                        "@Parameter %s on %s.%s is optional, non-STRING, but declares no defaultValue",
                        parameter.name(), clazz.getName(), method.getName()));
            }
            try {
                parameter.type().coerce(parameter.defaultValue());
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        String.format(
                                "@Parameter %s on %s.%s declares defaultValue \"%s\" that does not coerce to %s",
                                parameter.name(),
                                clazz.getName(),
                                method.getName(),
                                parameter.defaultValue(),
                                parameter.type()),
                        e);
            }
        }

        return new ParameterDescriptor(
                key,
                rule,
                parameter.name(),
                parameter.type(),
                parameter.description(),
                parameter.mandatory(),
                parameter.defaultValue());
    }
}
