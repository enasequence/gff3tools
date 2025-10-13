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
import static org.mockito.Mockito.*;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.*;

class ValidationRegistryTest {

    private ValidationRegistry registry;
    private ValidationConfig validationConfig;

    @BeforeEach
    void setUp() {
        registry = ValidationRegistry.getInstance();
        validationConfig = mock(ValidationConfig.class);
    }

    @Test
    @DisplayName("Should return the same instance (singleton)")
    void testSingletonInstance() {
        ValidationRegistry another = ValidationRegistry.getInstance();
        assertSame(registry, another, "ValidationRegistry must be a singleton");
    }

    // Mock a fake validator class annotated with @Gff3Validation
    @Gff3Validation(name = "length", enabled = true)
    static class DummyValidator {
        public DummyValidator() {}

        @ValidationMethod(rule = "RULE_1", type = ValidationType.FEATURE)
        public void validate() {}
    }

    @Test
    @DisplayName("Should initialize registry and cache validators correctly")
    void testInitRegistryBuildsValidators() throws Exception {

        // Mock ClassInfo and ScanResult
        ClassInfo mockClassInfo = mock(ClassInfo.class);
        doReturn(DummyValidator.class).when(mockClassInfo).loadClass();

        List<ClassInfo> classInfos = List.of(mockClassInfo);
        when(validationConfig.isValidatorEnabled("length", true)).thenReturn(true);

        // Call private build() via reflection
        Method buildMethod = ValidationRegistry.class.getDeclaredMethod("build", List.class, ValidationConfig.class);
        buildMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ValidatorDescriptor> result =
                (List<ValidatorDescriptor>) buildMethod.invoke(registry, classInfos, validationConfig);

        assertEquals(1, result.size(), "Should create one validator descriptor");
        assertEquals(DummyValidator.class, result.get(0).clazz());
    }

    @Test
    @DisplayName("Should filter validations correctly")
    void testGetValidationsFiltersOnlyValidationClasses() {
        @Gff3Validation(name = "val1")
        class ValClass {
            @ValidationMethod(rule = "R1", type = ValidationType.FEATURE)
            public void validate() {}
        }

        @Gff3Fix(name = "fix1")
        class FixClass {
            @ValidationMethod(rule = "F1", type = ValidationType.FEATURE)
            public void fix() {}
        }

        List<ValidatorDescriptor> all = new ArrayList<>();
        all.add(new ValidatorDescriptor(ValClass.class, new ValClass(), getMethod(ValClass.class, "validate")));
        all.add(new ValidatorDescriptor(FixClass.class, new FixClass(), getMethod(FixClass.class, "fix")));

        // Inject cachedValidators
        setCachedValidators(all);

        List<ValidatorDescriptor> result = ValidationRegistry.getValidations(validationConfig);
        assertEquals(1, result.size());
        assertEquals(ValClass.class, result.get(0).clazz());
    }

    @Test
    @DisplayName("Should filter fix classes correctly")
    void testGetFixsFiltersOnlyFixClasses() {
        @Gff3Validation(name = "val1")
        class ValClass {
            @ValidationMethod(rule = "R1", type = ValidationType.FEATURE)
            public void validate() {}
        }

        @Gff3Fix(name = "fix1")
        class FixClass {
            @ValidationMethod(rule = "F1", type = ValidationType.FEATURE)
            public void fix() {}
        }

        List<ValidatorDescriptor> all = new ArrayList<>();
        all.add(new ValidatorDescriptor(ValClass.class, new ValClass(), getMethod(ValClass.class, "validate")));
        all.add(new ValidatorDescriptor(FixClass.class, new FixClass(), getMethod(FixClass.class, "fix")));

        setCachedValidators(all);

        List<ValidatorDescriptor> result = registry.getFixs(validationConfig);
        assertEquals(1, result.size());
        assertEquals(FixClass.class, result.get(0).clazz());
    }

    @Test
    @DisplayName("Should throw exception for duplicate validation rule names")
    void testDuplicateValidationRulesThrowsException() {

        @Gff3Validation(name = "dup")
        class DuplicateRuleValidator {
            @ValidationMethod(rule = "DUP_RULE", type = ValidationType.FEATURE)
            private void validate1() {}

            @ValidationMethod(rule = "DUP_RULE", type = ValidationType.FEATURE)
            private void validate2() {}
        }

        ClassInfo mockClassInfo = mock(ClassInfo.class);
        doReturn(DuplicateRuleValidator.class).when(mockClassInfo).loadClass();

        ClassInfoList list = new ClassInfoList();
        list.add(mockClassInfo);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            invokeCheckUniqueValidationRules(list);
        });

        assertTrue(ex.getCause().getCause().getLocalizedMessage().contains("Duplicate validation rule detected"));

        doReturn(null).when(mockClassInfo).loadClass();
    }

    // Utility methods
    private static Method getMethod(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredMethod(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setCachedValidators(List<ValidatorDescriptor> validators) {
        try {
            var field = ValidationRegistry.class.getDeclaredField("cachedValidators");
            field.setAccessible(true);
            field.set(null, validators);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void invokeCheckUniqueValidationRules(ClassInfoList list) {
        try {
            Method m = ValidationRegistry.class.getDeclaredMethod("checkUniqueValidationRules", ClassInfoList.class);
            m.setAccessible(true);
            m.invoke(ValidationRegistry.getInstance(), list);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
