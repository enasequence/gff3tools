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
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;

class ValidationRegistryTest {

    private ValidationRegistry registry;
    private ValidationConfig validationConfig;

    @BeforeEach
    void setUp() {

        validationConfig = mock(ValidationConfig.class);
        registry = ValidationRegistry.getInstance(validationConfig, null);
    }

    @Test
    @DisplayName("Should return the same instance (singleton)")
    void testSingletonInstance() {
        ValidationRegistry another = ValidationRegistry.getInstance(validationConfig, null);
        assertSame(registry, another, "ValidationRegistry must be a singleton");
    }

    @Gff3Validation(name = "length", enabled = true)
    static class DummyValidation extends Validation {
        public DummyValidation() {}

        @ValidationMethod(rule = "RULE_1", type = ValidationType.FEATURE)
        public void validate() {}
    }

    @Gff3Fix(name = "fix_length", enabled = true)
    static class DummyFix extends Validation {
        public DummyFix() {}

        @FixMethod(rule = "FIX_RULE_1", type = ValidationType.FEATURE)
        public void fix() {}
    }

    @Test
    @DisplayName("Should initialize registry and cache validators correctly for Gff3Validation and Gff3Fix")
    void testInitRegistryBuildsValidators() throws Exception {
        // Define dummy classes to simulate both annotation types

        // Prepare mocks
        ClassInfo mockValidationInfo = mock(ClassInfo.class);
        ClassInfo mockFixInfo = mock(ClassInfo.class);
        doReturn(DummyValidation.class).when(mockValidationInfo).loadClass();
        doReturn(DummyFix.class).when(mockFixInfo).loadClass();

        List<ClassInfo> classInfos = List.of(mockValidationInfo, mockFixInfo);

        // Mock config to return true for both annotation types
        when(validationConfig.isValidatorEnabled(any(Annotation.class))).thenReturn(true);

        // Access private build() via reflection
        Method buildMethod = ValidationRegistry.class.getDeclaredMethod("build", List.class);
        buildMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ValidatorDescriptor> result = (List<ValidatorDescriptor>) buildMethod.invoke(registry, classInfos);

        // Assertions
        assertEquals(2, result.size(), "Should build validators for both Gff3Validation and Gff3Fix");

        Set<Class<?>> classes = result.stream().map(ValidatorDescriptor::clazz).collect(Collectors.toSet());
        assertTrue(classes.contains(DummyValidation.class), "Should include DummyValidation");
        assertTrue(classes.contains(DummyFix.class), "Should include DummyFix");
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

        List<ValidatorDescriptor> result = registry.getValidations();
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

        List<ValidatorDescriptor> result = registry.getFixs();
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

            @FixMethod(rule = "DUP_RULE", type = ValidationType.FEATURE)
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

    @Test
    @DisplayName("Should not throw when validation rules are unique")
    void testUniqueValidationRulesPass() throws Exception {

        @Gff3Validation(name = "unique")
        class UniqueRuleValidator {
            @ValidationMethod(rule = "RULE_1", type = ValidationType.FEATURE)
            public void validate1() {}

            @ValidationMethod(rule = "RULE_2", type = ValidationType.FEATURE)
            public void validate2() {}
        }

        ClassInfo mockClassInfo = mock(ClassInfo.class);
        doReturn(UniqueRuleValidator.class).when(mockClassInfo).loadClass();

        ClassInfoList validationList = new ClassInfoList();
        validationList.add(mockClassInfo);

        Method privateMethod =
                ValidationRegistry.class.getDeclaredMethod("checkUniqueValidationRules", ClassInfoList.class);
        privateMethod.setAccessible(true);

        // Should NOT throw since rules are unique
        assertDoesNotThrow(() -> privateMethod.invoke(registry, validationList));
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

    private void invokeCheckUniqueValidationRules(ClassInfoList list) {
        try {
            Method m = ValidationRegistry.class.getDeclaredMethod("checkUniqueValidationRules", ClassInfoList.class);
            m.setAccessible(true);
            m.invoke(registry, list);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
