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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.DuplicateValidationRuleException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

class ValidationEngineBuilderTest {

    // -- Test stub classes --

    @Gff3Fix(name = "TEST_FIX_A", description = "A test fix")
    static class TestFixA {
        @InjectContext
        ValidationContext ctx;

        @FixMethod(rule = "TEST_FIX_A_RULE", type = ValidationType.FEATURE)
        public void fix(GFF3Feature feature, int line) {}
    }

    @Gff3Fix(name = "TEST_FIX_B", description = "Another test fix")
    static class TestFixB {
        @FixMethod(rule = "TEST_FIX_B_RULE", type = ValidationType.FEATURE)
        public void fix(GFF3Feature feature, int line) {}
    }

    @Gff3Fix(name = "TEST_FIX_A", description = "Duplicate name fix")
    static class TestFixDuplicateA {
        @FixMethod(rule = "TEST_FIX_A_DUP_RULE", type = ValidationType.FEATURE)
        public void fix(GFF3Feature feature, int line) {}
    }

    @Gff3Validation(name = "TEST_VALIDATION_A", description = "A test validation")
    static class TestValidationA {
        @InjectContext
        ValidationContext ctx;

        @ValidationMethod(rule = "TEST_VALIDATION_A_RULE", type = ValidationType.FEATURE)
        public void validate(GFF3Feature feature, int line) {}
    }

    @Gff3Fix(name = "LOCUS_TAG_TO_UPPERCASE", description = "Override for scanned LocusTagFix")
    static class OverrideLocusTagFix {
        @FixMethod(rule = "LOCUS_TAG_TO_UPPERCASE", type = ValidationType.FEATURE)
        public void fixFeature(GFF3Feature feature, int line) {
            // custom implementation
        }
    }

    @Gff3Fix(name = "CRITICAL_FIX", description = "A critical priority fix")
    static class CriticalPriorityFix {
        @FixMethod(rule = "CRITICAL_FIX_RULE", type = ValidationType.FEATURE, priority = ValidationPriority.CRITICAL)
        public void fix(GFF3Feature feature, int line) {}
    }

    @Gff3Fix(name = "LOW_FIX", description = "A low priority fix")
    static class LowPriorityFix {
        @FixMethod(rule = "LOW_FIX_RULE", type = ValidationType.FEATURE, priority = ValidationPriority.LOW)
        public void fix(GFF3Feature feature, int line) {}
    }

    static class UnannotatedFix {
        public void fix(GFF3Feature feature, int line) {}
    }

    static class StringProvider implements ContextProvider<String> {
        private final String value;

        StringProvider(String value) {
            this.value = value;
        }

        @Override
        public String get(ValidationContext context) {
            return value;
        }

        @Override
        public Class<String> type() {
            return String.class;
        }
    }

    static class IntegerProvider implements ContextProvider<Integer> {
        private final int value;

        IntegerProvider(int value) {
            this.value = value;
        }

        @Override
        public Integer get(ValidationContext context) {
            return value;
        }

        @Override
        public Class<Integer> type() {
            return Integer.class;
        }
    }

    @Test
    @DisplayName("build() with classpath scanning disabled produces empty registry")
    void build_withClasspathScanningDisabled_producesEmptyRegistry() {
        ValidationEngine engine =
                new ValidationEngineBuilder().disableClasspathScanning().build();

        assertNotNull(engine);
        assertNotNull(engine.getContext());

        // Access the registry via reflection to verify it's empty
        ValidationRegistry registry = getRegistry(engine);
        assertTrue(registry.getFixs().isEmpty(), "Fixes should be empty");
        assertTrue(registry.getValidations().isEmpty(), "Validations should be empty");
        assertTrue(registry.getExits().isEmpty(), "Exits should be empty");
    }

    @Test
    @DisplayName("build() with explicit fix registers fix in registry")
    void build_withExplicitFix_registersFixInRegistry() {
        TestFixA fixInstance = new TestFixA();

        ValidationEngine engine = new ValidationEngineBuilder()
                .disableClasspathScanning()
                .withFix(fixInstance)
                .build();

        ValidationRegistry registry = getRegistry(engine);
        assertEquals(1, registry.getFixs().size(), "Should have exactly one fix");
        assertSame(fixInstance, registry.getFixs().get(0).instance(), "Should be the same fix instance");
    }

    @Test
    @DisplayName("build() with explicit validator registers validator in registry")
    void build_withExplicitValidator_registersValidatorInRegistry() {
        TestValidationA validationInstance = new TestValidationA();

        ValidationEngine engine = new ValidationEngineBuilder()
                .disableClasspathScanning()
                .withValidator(validationInstance)
                .build();

        ValidationRegistry registry = getRegistry(engine);
        assertEquals(1, registry.getValidations().size(), "Should have exactly one validation");
        assertSame(
                validationInstance,
                registry.getValidations().get(0).instance(),
                "Should be the same validation instance");
    }

    @Test
    @DisplayName("build() with explicit fix overriding scanned replaces scanned fix")
    void build_withExplicitFixOverridingScanned_replacesScannedFix() {
        OverrideLocusTagFix overrideInstance = new OverrideLocusTagFix();

        ValidationEngine engine =
                new ValidationEngineBuilder().withFix(overrideInstance).build();

        ValidationRegistry registry = getRegistry(engine);

        // Find all fix descriptors with the LOCUS_TAG_TO_UPPERCASE class-level name
        long locusTagFixCount = registry.getFixs().stream()
                .filter(vd -> {
                    Gff3Fix annotation = vd.clazz().getAnnotation(Gff3Fix.class);
                    return annotation != null && "LOCUS_TAG_TO_UPPERCASE".equals(annotation.name());
                })
                .count();

        assertEquals(1, locusTagFixCount, "Should have exactly one LOCUS_TAG_TO_UPPERCASE fix");

        // Verify it's the override instance
        ValidatorDescriptor locusTagDescriptor = registry.getFixs().stream()
                .filter(vd -> {
                    Gff3Fix annotation = vd.clazz().getAnnotation(Gff3Fix.class);
                    return annotation != null && "LOCUS_TAG_TO_UPPERCASE".equals(annotation.name());
                })
                .findFirst()
                .orElseThrow();

        assertSame(overrideInstance, locusTagDescriptor.instance(), "Should be the override instance");
    }

    @Test
    @DisplayName("build() with duplicate explicit names throws DuplicateValidationRuleException")
    void build_withDuplicateExplicitNames_throwsDuplicateValidationRuleException() {
        TestFixA fixA = new TestFixA();
        TestFixDuplicateA fixDupA = new TestFixDuplicateA();

        assertThrows(DuplicateValidationRuleException.class, () -> {
            new ValidationEngineBuilder()
                    .disableClasspathScanning()
                    .withFix(fixA)
                    .withFix(fixDupA)
                    .build();
        });
    }

    @Test
    @DisplayName("build() with explicit fix injects context")
    void build_withExplicitFix_injectsContext() {
        TestFixA fixInstance = new TestFixA();

        ValidationEngine engine = new ValidationEngineBuilder()
                .disableClasspathScanning()
                .withFix(fixInstance)
                .build();

        assertNotNull(fixInstance.ctx, "@InjectContext field should be injected");
        assertSame(engine.getContext(), fixInstance.ctx, "Injected context should match engine context");
    }

    @Test
    @DisplayName("build() with multiple providers registers all providers")
    void build_withMultipleProviders_registersAllProviders() {
        StringProvider stringProvider = new StringProvider("hello");
        IntegerProvider integerProvider = new IntegerProvider(42);

        ValidationEngine engine = new ValidationEngineBuilder()
                .disableClasspathScanning()
                .withProvider(stringProvider)
                .withProvider(integerProvider)
                .build();

        ValidationContext ctx = engine.getContext();
        assertEquals("hello", ctx.get(String.class), "String provider should be resolvable");
        assertEquals(42, ctx.get(Integer.class), "Integer provider should be resolvable");
    }

    @Test
    @DisplayName("build() with explicit fixes groups by priority correctly")
    void build_withExplicitFixes_groupsByPriorityCorrectly() {
        CriticalPriorityFix criticalFix = new CriticalPriorityFix();
        LowPriorityFix lowFix = new LowPriorityFix();

        ValidationEngine engine = new ValidationEngineBuilder()
                .disableClasspathScanning()
                .withFix(criticalFix)
                .withFix(lowFix)
                .build();

        ValidationRegistry registry = getRegistry(engine);
        Map<ValidationPriority, List<ValidatorDescriptor>> byPriority = registry.getFixesByPriority();

        assertEquals(
                1,
                byPriority.getOrDefault(ValidationPriority.CRITICAL, List.of()).size(),
                "Should have one CRITICAL fix");
        assertEquals(
                1, byPriority.getOrDefault(ValidationPriority.LOW, List.of()).size(), "Should have one LOW fix");
        assertTrue(
                byPriority.getOrDefault(ValidationPriority.NORMAL, List.of()).isEmpty(), "Should have no NORMAL fixes");
    }

    @Test
    @DisplayName("build() with unannotated fix throws IllegalArgumentException")
    void build_withUnannotatedFix_throwsIllegalArgumentException() {
        UnannotatedFix unannotated = new UnannotatedFix();

        assertThrows(IllegalArgumentException.class, () -> {
            new ValidationEngineBuilder()
                    .disableClasspathScanning()
                    .withFix(unannotated)
                    .build();
        });
    }

    // -- Utility --

    private static ValidationRegistry getRegistry(ValidationEngine engine) {
        try {
            var field = ValidationEngine.class.getDeclaredField("validationRegistry");
            field.setAccessible(true);
            return (ValidationRegistry) field.get(engine);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
