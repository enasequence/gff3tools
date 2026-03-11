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
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

class ValidationRegistryTest {

    private ValidationRegistry registry;
    private ValidationConfig validationConfig;

    @BeforeEach
    void setUp() {
        validationConfig = mock(ValidationConfig.class);
        when(validationConfig.isValidatorEnabled(any(Annotation.class))).thenReturn(true);
        registry = ValidationRegistry.builder().config(validationConfig).build();
    }

    @Gff3Validation(name = "LENGTH", enabled = true)
    static class DummyValidation {
        public DummyValidation() {}

        @ValidationMethod(rule = "RULE_1", type = ValidationType.FEATURE)
        public void validate() {}

        @ExitMethod
        public void onExit() {}
    }

    @Gff3Fix(name = "FIX_LENGTH", enabled = true)
    static class DummyFix {
        public DummyFix() {}

        @FixMethod(rule = "FIX_RULE_1", type = ValidationType.FEATURE)
        public void fix() {}

        @ExitMethod
        public void onExit() {}
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

        // Access private buildDescriptors() via reflection
        Method buildMethod = ValidationRegistry.class.getDeclaredMethod(
                "buildDescriptors", List.class, ValidationContext.class, ValidationConfig.class);
        buildMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ValidatorDescriptor> result = (List<ValidatorDescriptor>)
                buildMethod.invoke(null, classInfos, new ValidationContext(), validationConfig);

        // Assertions
        assertEquals(4, result.size(), "Should build validators for both Gff3Validation and Gff3Fix");

        Set<Class<?>> classes = result.stream().map(ValidatorDescriptor::clazz).collect(Collectors.toSet());
        assertTrue(classes.contains(DummyValidation.class), "Should include DummyValidation");
        assertTrue(classes.contains(DummyFix.class), "Should include DummyFix");

        Set<Method> methods = result.stream().map(ValidatorDescriptor::method).collect(Collectors.toSet());
        assertTrue(
                methods.contains(DummyValidation.class.getMethod("onExit")), "Should include DummyValidation.onExit");
        assertTrue(methods.contains(DummyFix.class.getMethod("onExit")), "Should include DummyFix.onExit");
    }

    @Test
    @DisplayName("Should filter validations correctly")
    void testGetValidationsFiltersOnlyValidationClasses() {
        @Gff3Validation(name = "VAL1")
        class ValClass {
            @ValidationMethod(rule = "R1", type = ValidationType.FEATURE)
            public void validate() {}

            @ExitMethod
            public void onExit() {}
        }

        @Gff3Fix(name = "FIX1")
        class FixClass {
            @FixMethod(rule = "F1", type = ValidationType.FEATURE)
            public void fix() {}

            @ExitMethod
            public void onExit() {}
        }

        List<ValidatorDescriptor> all = new ArrayList<>();
        all.add(new ValidatorDescriptor(
                ValClass.class, new ValClass(), getMethod(ValClass.class, "validate"), ValidationPriority.NORMAL));
        all.add(new ValidatorDescriptor(
                ValClass.class, new ValClass(), getMethod(ValClass.class, "onExit"), ValidationPriority.NORMAL));
        all.add(new ValidatorDescriptor(
                FixClass.class, new FixClass(), getMethod(FixClass.class, "fix"), ValidationPriority.NORMAL));
        all.add(new ValidatorDescriptor(
                FixClass.class, new FixClass(), getMethod(FixClass.class, "onExit"), ValidationPriority.NORMAL));

        // Inject cachedValidators
        setCachedValidators(all);

        List<ValidatorDescriptor> result = registry.getValidations();
        assertEquals(1, result.size());
        assertEquals(ValClass.class, result.get(0).clazz());
    }

    @Test
    @DisplayName("Should filter exits correctly")
    void testGetExitsFiltersOnly() {
        @Gff3Validation(name = "VAL1")
        class ValClass {
            @ValidationMethod(rule = "R1", type = ValidationType.FEATURE)
            public void validate() {}
        }

        @Gff3Validation(name = "VAL2")
        class Val2Class {
            @ExitMethod
            public void onExit() {}
        }

        @Gff3Fix(name = "FIX1")
        class FixClass {
            @ValidationMethod(rule = "F1", type = ValidationType.FEATURE)
            public void fix() {}
        }

        @Gff3Fix(name = "FIX2")
        class Fix2Class {
            @ExitMethod
            public void onExit() {}
        }

        List<ValidatorDescriptor> all = new ArrayList<>();
        all.add(new ValidatorDescriptor(
                ValClass.class, new ValClass(), getMethod(ValClass.class, "validate"), ValidationPriority.NORMAL));
        all.add(new ValidatorDescriptor(
                Val2Class.class, new Val2Class(), getMethod(Val2Class.class, "onExit"), ValidationPriority.NORMAL));
        all.add(new ValidatorDescriptor(
                FixClass.class, new FixClass(), getMethod(FixClass.class, "fix"), ValidationPriority.NORMAL));
        all.add(new ValidatorDescriptor(
                Fix2Class.class, new Fix2Class(), getMethod(Fix2Class.class, "onExit"), ValidationPriority.NORMAL));

        // Inject cachedValidators
        setCachedValidators(all);

        List<ValidatorDescriptor> result = registry.getExits();
        assertEquals(2, result.size());
        assertEquals(Val2Class.class, result.get(0).clazz());
        assertEquals(Fix2Class.class, result.get(1).clazz());
    }

    @Test
    @DisplayName("Should filter fix classes correctly")
    void testGetFixsFiltersOnlyFixClasses() {
        @Gff3Validation(name = "VAL1")
        class ValClass {
            @ValidationMethod(rule = "R1", type = ValidationType.FEATURE)
            public void validate() {}
        }

        @Gff3Fix(name = "FIX1")
        class FixClass {
            @FixMethod(rule = "F1", type = ValidationType.FEATURE)
            public void fix() {}
        }

        List<ValidatorDescriptor> all = new ArrayList<>();
        all.add(new ValidatorDescriptor(
                ValClass.class, new ValClass(), getMethod(ValClass.class, "validate"), ValidationPriority.NORMAL));
        all.add(new ValidatorDescriptor(
                FixClass.class, new FixClass(), getMethod(FixClass.class, "fix"), ValidationPriority.NORMAL));

        setCachedValidators(all);

        List<ValidatorDescriptor> result = registry.getFixs();
        assertEquals(1, result.size());
        assertEquals(FixClass.class, result.get(0).clazz());
    }

    @Test
    @DisplayName("Should throw exception for duplicate validation rule names")
    void testDuplicateValidationRulesThrowsException() {

        @Gff3Validation(name = "DUP")
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

        @Gff3Validation(name = "UNIQUE")
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
        assertDoesNotThrow(() -> privateMethod.invoke(null, validationList));
    }

    // Utility methods
    private static Method getMethod(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredMethod(name);
        } catch (Exception e) {
            throw new RuntimeException("Did not find method %s".formatted(name), e);
        }
    }

    private void setCachedValidators(List<ValidatorDescriptor> validators) {
        try {
            var field = ValidationRegistry.class.getDeclaredField("cachedValidators");
            field.setAccessible(true);
            field.set(registry, validators);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeCheckUniqueValidationRules(ClassInfoList list) {
        try {
            Method m = ValidationRegistry.class.getDeclaredMethod("checkUniqueValidationRules", ClassInfoList.class);
            m.setAccessible(true);
            m.invoke(null, list);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Gff3Validation(name = "STARTUP_VAL", enabled = true)
    static class StartupValidation {
        static boolean startupCalled = false;

        @StartupMethod
        public void startup() {
            startupCalled = true;
        }

        @ValidationMethod(rule = "STARTUP_RULE", type = ValidationType.FEATURE)
        public void validate() {}
    }

    @Gff3Validation(name = "STARTUP_CONTEXT_VAL", enabled = true)
    static class StartupWithContextValidation {
        static ValidationContext capturedContext = null;

        @InjectContext
        ValidationContext ctx;

        @StartupMethod
        public void startup() {
            capturedContext = ctx;
        }

        @ValidationMethod(rule = "STARTUP_CONTEXT_RULE", type = ValidationType.FEATURE)
        public void validate() {}
    }

    @Gff3Validation(name = "MULTI_STARTUP_VAL", enabled = true)
    static class MultiStartupValidation {
        static int startupCount = 0;

        @StartupMethod
        public void startupA() {
            startupCount++;
        }

        @StartupMethod
        public void startupB() {
            startupCount++;
        }

        @ValidationMethod(rule = "MULTI_STARTUP_RULE", type = ValidationType.FEATURE)
        public void validate() {}
    }

    @Test
    @DisplayName("@StartupMethod is invoked when registry builds descriptors")
    void testStartupMethodIsInvoked() throws Exception {
        StartupValidation.startupCalled = false;

        ClassInfo mockClassInfo = mock(ClassInfo.class);
        doReturn(StartupValidation.class).when(mockClassInfo).loadClass();

        Method buildMethod = ValidationRegistry.class.getDeclaredMethod(
                "buildDescriptors", List.class, ValidationContext.class, ValidationConfig.class);
        buildMethod.setAccessible(true);

        buildMethod.invoke(null, List.of(mockClassInfo), new ValidationContext(), validationConfig);

        assertTrue(StartupValidation.startupCalled, "@StartupMethod should have been called");
    }

    @Test
    @DisplayName("@InjectContext is populated before @StartupMethod is invoked")
    void testContextInjectedBeforeStartupMethod() throws Exception {
        StartupWithContextValidation.capturedContext = null;

        ClassInfo mockClassInfo = mock(ClassInfo.class);
        doReturn(StartupWithContextValidation.class).when(mockClassInfo).loadClass();

        Method buildMethod = ValidationRegistry.class.getDeclaredMethod(
                "buildDescriptors", List.class, ValidationContext.class, ValidationConfig.class);
        buildMethod.setAccessible(true);

        ValidationContext context = new ValidationContext();
        buildMethod.invoke(null, List.of(mockClassInfo), context, validationConfig);

        assertNotNull(StartupWithContextValidation.capturedContext, "Context should have been injected");
        assertSame(
                context, StartupWithContextValidation.capturedContext, "Injected context should be the same instance");
    }

    @Test
    @DisplayName("All @StartupMethod methods on a class are invoked")
    void testMultipleStartupMethodsAreAllInvoked() throws Exception {
        MultiStartupValidation.startupCount = 0;

        ClassInfo mockClassInfo = mock(ClassInfo.class);
        doReturn(MultiStartupValidation.class).when(mockClassInfo).loadClass();

        Method buildMethod = ValidationRegistry.class.getDeclaredMethod(
                "buildDescriptors", List.class, ValidationContext.class, ValidationConfig.class);
        buildMethod.setAccessible(true);

        buildMethod.invoke(null, List.of(mockClassInfo), new ValidationContext(), validationConfig);

        assertEquals(2, MultiStartupValidation.startupCount, "Both @StartupMethod methods should have been called");
    }

    @Test
    @DisplayName("@InjectContext field on plain class should be injected with context")
    void testInjectContextFieldInjection() throws Exception {
        class PlainFix {
            @InjectContext
            ValidationContext ctx;

            @FixMethod(rule = "X", type = ValidationType.FEATURE)
            public void fix() {}
        }

        PlainFix instance = new PlainFix();
        ValidatorDescriptor descriptor = new ValidatorDescriptor(
                PlainFix.class, instance, PlainFix.class.getDeclaredMethod("fix"), ValidationPriority.NORMAL);
        ValidationContext context = new ValidationContext();

        Method injectMethod =
                ValidationRegistry.class.getDeclaredMethod("injectContext", Object.class, ValidationContext.class);
        injectMethod.setAccessible(true);
        injectMethod.invoke(null, instance, context);

        assertSame(context, instance.ctx);
    }
}
