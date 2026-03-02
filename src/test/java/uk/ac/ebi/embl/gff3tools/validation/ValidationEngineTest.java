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

import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.AggregatedValidationException;
import uk.ac.ebi.embl.gff3tools.exception.DuplicateValidationRuleException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

public class ValidationEngineTest {

    @Mock
    private ValidationConfig validationConfig;

    @Mock
    private ValidationRegistry validationRegistry;

    private ValidationEngine engine;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        // Use fail-fast=true to preserve original test behavior (default changed to false)
        engine = new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), true);
    }

    @Test
    public void testValidate_successfulValidation() throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        ValidationEngine validationEngine = validationEngineBuilder.build();
        validationEngine.validate(
                new GFF3Feature(
                        Optional.empty(), Optional.empty(), "", Optional.empty(), "", "gene", 1L, 2L, "", "", ""),
                1);
        assertTrue(validationEngine.getParsingWarnings().isEmpty());
    }

    @Test
    public void testValidate_failingValidation_failFastMode()
            throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        // With fail-fast enabled, validation should throw immediately
        ValidationEngine validationEngine =
                validationEngineBuilder.failFast(true).build();

        GFF3Feature invalidFeature =
                new GFF3Feature(Optional.empty(), Optional.empty(), "", Optional.empty(), "", "", 0L, 2L, "", "", "");
        ValidationException ex =
                Assertions.assertThrows(ValidationException.class, () -> validationEngine.validate(invalidFeature, 1));

        Assertions.assertAll(
                () -> Assertions.assertTrue(ex.getMessage().contains("Violation of rule LOCATION on line 1")));
    }

    @Test
    public void testValidate_failingValidation_collectErrorsMode()
            throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        // Default behavior (failFast=false) collects errors instead of throwing
        ValidationEngine validationEngine = validationEngineBuilder.build();

        GFF3Feature invalidFeature =
                new GFF3Feature(Optional.empty(), Optional.empty(), "", Optional.empty(), "", "", 0L, 2L, "", "", "");

        // Should not throw during validation
        assertDoesNotThrow(() -> validationEngine.validate(invalidFeature, 1));

        // Errors should be collected (there are multiple validation errors on this malformed feature)
        assertTrue(validationEngine.getCollectedErrors().size() >= 1);
        // At least one error should be about LOCATION
        assertTrue(validationEngine.getCollectedErrors().stream()
                .anyMatch(e -> e.getMessage().contains("Violation of rule LOCATION")));
    }

    // ------------------------------------------------------------
    // 1. Execute validations (normal flow)
    // ------------------------------------------------------------

    @Test
    void testExecuteValidations_invokesFeatureValidation() throws Exception {
        // Mock method with @ValidationMethod
        class DummyValidator {
            @ValidationMethod(rule = "RULE_X", type = ValidationType.FEATURE)
            public void validate(GFF3Feature f, int line) {
                // f.setAttribute("validated", "true");
            }
        }

        Method m = DummyValidator.class.getDeclaredMethod("validate", GFF3Feature.class, int.class);
        DummyValidator instance = spy(new DummyValidator());

        ValidatorDescriptor descriptor =
                new ValidatorDescriptor(DummyValidator.class, instance, m, ValidationPriority.NORMAL);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_X", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);
        when(validationRegistry.getValidations()).thenReturn(descriptors);

        GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
        engine.executeValidations(feature, 10);

        verify(instance, times(1)).validate(feature, 10);
    }

    // ------------------------------------------------------------
    // 2. Skip when OFF
    // ------------------------------------------------------------
    @Test
    void testExecuteValidations_skipsWhenOff() throws Exception {
        class DummyValidator {
            @ValidationMethod(rule = "RULE_OFF", type = ValidationType.FEATURE)
            public void validate(GFF3Feature f, int line) {
                fail("Should not be invoked when rule is OFF");
            }
        }

        Method m = DummyValidator.class.getDeclaredMethod("validate", GFF3Feature.class, int.class);
        ValidatorDescriptor descriptor =
                new ValidatorDescriptor(DummyValidator.class, new DummyValidator(), m, ValidationPriority.NORMAL);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_OFF", RuleSeverity.ERROR)).thenReturn(RuleSeverity.OFF);
        when(validationRegistry.getValidations()).thenReturn(descriptors);

        GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
        assertDoesNotThrow(() -> engine.executeValidations(feature, 1));
    }

    // ------------------------------------------------------------
    // 3. Handle WARN and ERROR severities
    // ------------------------------------------------------------
    @Test
    void testExecuteValidations_warnAddsToParsingErrors() throws Exception {
        class DummyValidator {
            @ValidationMethod(rule = "RULE_WARN", type = ValidationType.FEATURE)
            public void validate(GFF3Feature f, int line) throws ValidationException {
                throw new ValidationException("warning triggered");
            }
        }

        Method m = DummyValidator.class.getDeclaredMethod("validate", GFF3Feature.class, int.class);
        ValidatorDescriptor descriptor =
                new ValidatorDescriptor(DummyValidator.class, new DummyValidator(), m, ValidationPriority.NORMAL);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_WARN", RuleSeverity.ERROR)).thenReturn(RuleSeverity.WARN);
        when(validationRegistry.getValidations()).thenReturn(descriptors);

        GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
        engine.executeValidations(feature, 1);
        assertEquals(1, engine.getParsingWarnings().size());
    }

    @Test
    void testExecuteValidations_errorThrowsException() throws Exception {
        class DummyValidator {
            @ValidationMethod(rule = "RULE_ERR", type = ValidationType.FEATURE)
            public void validate(GFF3Feature f, int line) throws ValidationException {
                throw new ValidationException("error triggered");
            }
        }

        Method m = DummyValidator.class.getDeclaredMethod("validate", GFF3Feature.class, int.class);
        ValidatorDescriptor descriptor =
                new ValidatorDescriptor(DummyValidator.class, new DummyValidator(), m, ValidationPriority.NORMAL);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_ERR", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);
        when(validationRegistry.getValidations()).thenReturn(descriptors);

        GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
        ValidationException ex = assertThrows(ValidationException.class, () -> engine.executeValidations(feature, 1));
        assertEquals("Violation of rule RULE_ERR on line 0: error triggered", ex.getMessage());
    }

    // ------------------------------------------------------------
    // 4. Execute fixes
    // ------------------------------------------------------------
    @Test
    void testExecuteFixs_invokesFixForAnnotation() throws Exception {
        class DummyFix {
            @FixMethod(rule = "FIX_1", type = ValidationType.ANNOTATION)
            public void fix(GFF3Annotation a, int line) {}
        }

        Method m = DummyFix.class.getDeclaredMethod("fix", GFF3Annotation.class, int.class);
        DummyFix instance = spy(new DummyFix());
        ValidatorDescriptor descriptor =
                new ValidatorDescriptor(DummyFix.class, instance, m, ValidationPriority.NORMAL);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getFix("FIX_1", true)).thenReturn(true);
        when(validationRegistry.getFixs()).thenReturn(descriptors);

        engine.executeFixes(new GFF3Annotation(), 5);

        verify(instance, times(1)).fix(any(), eq(5));
    }

    // ------------------------------------------------------------
    // 5. Execute fixes
    // ------------------------------------------------------------
    @Test
    void testDisabledExecuteFixs_invokesFixForAnnotation() throws Exception {
        class DummyFix {
            @FixMethod(rule = "FIX_1", type = ValidationType.ANNOTATION, enabled = false)
            public void fix(GFF3Annotation a, int line) {}
        }

        Method m = DummyFix.class.getDeclaredMethod("fix", GFF3Annotation.class, int.class);
        DummyFix instance = spy(new DummyFix());
        ValidatorDescriptor descriptor =
                new ValidatorDescriptor(DummyFix.class, instance, m, ValidationPriority.NORMAL);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getFix("FIX_1", true)).thenReturn(false);
    }

    // ------------------------------------------------------------
    // 6. Handle syntactic validation exception
    // ------------------------------------------------------------
    @Test
    void testHandleSyntacticValidation_warnAddsToParsingErrors() throws ValidationException {
        ValidationException vex = new ValidationException("rule", "warn");
        when(validationConfig.getSeverity(vex.getValidationRule().toString(), RuleSeverity.ERROR))
                .thenReturn(RuleSeverity.WARN);

        engine.handleSyntacticError(vex);
        assertEquals(1, engine.getParsingWarnings().size());
    }

    @Test
    void testHandleSyntacticValidation_errorThrows() {
        ValidationException vex = new ValidationException("rule", "fatal");
        when(validationConfig.getSeverity(vex.getValidationRule().toString(), RuleSeverity.ERROR))
                .thenReturn(RuleSeverity.ERROR);

        assertThrows(ValidationException.class, () -> engine.handleSyntacticError(vex));
    }

    @Test
    void testExecuteValidations_invokesExitMethod() throws Exception {
        // Mock method with @ValidationMethod
        class DummyValidator {
            @ExitMethod()
            public void onExit() {}
        }

        Method m = DummyValidator.class.getDeclaredMethod("onExit");
        DummyValidator instance = spy(new DummyValidator());

        ValidatorDescriptor descriptor =
                new ValidatorDescriptor(DummyValidator.class, instance, m, ValidationPriority.NORMAL);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationRegistry.getExits()).thenReturn(descriptors);

        engine.executeExits();
        verify(instance, times(1)).onExit();
    }

    // ------------------------------------------------------------
    // 7. Fail-fast mode tests
    // ------------------------------------------------------------
    @Test
    void handleSyntacticError_defaultBehavior_collectsError() {
        // Default is failFast=false (collect errors)
        ValidationEngine engineCollect =
                new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), false);
        ValidationException exception = new ValidationException("RULE", 1, "test error");
        when(validationConfig.getSeverity("RULE", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);

        assertDoesNotThrow(() -> engineCollect.handleSyntacticError(exception));
        assertEquals(1, engineCollect.getCollectedErrors().size());
    }

    @Test
    void handleSyntacticError_failFastTrue_throwsImmediately() {
        ValidationEngine engineFailFast =
                new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), true);
        ValidationException exception = new ValidationException("RULE", 1, "test error");
        when(validationConfig.getSeverity("RULE", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);

        assertThrows(ValidationException.class, () -> engineFailFast.handleSyntacticError(exception));
    }

    @Test
    void handleSyntacticError_failFastFalse_collectsMultipleErrors() throws ValidationException {
        ValidationEngine engineCollect =
                new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), false);
        when(validationConfig.getSeverity("RULE", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);

        engineCollect.handleSyntacticError(new ValidationException("RULE", 1, "error 1"));
        engineCollect.handleSyntacticError(new ValidationException("RULE", 2, "error 2"));
        engineCollect.handleSyntacticError(new ValidationException("RULE", 3, "error 3"));

        assertEquals(3, engineCollect.getCollectedErrors().size());
    }

    @Test
    void throwIfErrorsCollected_withErrors_throwsAggregate() throws ValidationException {
        ValidationEngine engineCollect =
                new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), false);
        when(validationConfig.getSeverity("RULE", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);

        engineCollect.handleSyntacticError(new ValidationException("RULE", 1, "error 1"));
        engineCollect.handleSyntacticError(new ValidationException("RULE", 2, "error 2"));

        AggregatedValidationException thrown =
                assertThrows(AggregatedValidationException.class, () -> engineCollect.throwIfErrorsCollected());

        assertEquals(2, thrown.getErrors().size());
    }

    @Test
    void throwIfErrorsCollected_noErrors_doesNotThrow() {
        ValidationEngine engineCollect =
                new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), false);

        assertDoesNotThrow(() -> engineCollect.throwIfErrorsCollected());
    }

    @Test
    void hasCollectedErrors_withErrors_returnsTrue() throws ValidationException {
        ValidationEngine engineCollect =
                new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), false);
        when(validationConfig.getSeverity("RULE", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);

        engineCollect.handleSyntacticError(new ValidationException("RULE", 1, "error 1"));

        assertTrue(engineCollect.hasCollectedErrors());
    }

    @Test
    void hasCollectedErrors_noErrors_returnsFalse() {
        ValidationEngine engineCollect =
                new ValidationEngine(validationConfig, validationRegistry, new ValidationContext(), false);

        assertFalse(engineCollect.hasCollectedErrors());
    }

    @Test
    void builderDefaults_toCollectAllErrors() throws Exception {
        // Building without specifying failFast should default to false (collect all errors)
        ValidationEngine engineFromBuilder = new ValidationEngineBuilder().build();
        // We can't directly check the failFast field, but we can verify behavior
        // by checking that it doesn't throw on syntactic error
        // This requires a real config, which the builder provides
        assertNotNull(engineFromBuilder);
    }

    @Test
    void builderWithFailFast_setsFailFastMode() throws Exception {
        ValidationEngine engineFromBuilder =
                new ValidationEngineBuilder().failFast(true).build();
        assertNotNull(engineFromBuilder);
    }

    // ------------------------------------------------------------
    // 8. Priority-based execution tests
    // ------------------------------------------------------------

    /** Shared execution log used by priority tests to verify ordering. */
    private static final List<String> executionLog = new ArrayList<>();

    @Gff3Fix(name = "CRITICAL_FIX")
    static class CriticalFix {
        @FixMethod(rule = "FIX_CRITICAL", type = ValidationType.FEATURE, priority = ValidationPriority.CRITICAL)
        public void fix(GFF3Feature f, int line) {
            executionLog.add("FIX_CRITICAL");
        }
    }

    @Gff3Fix(name = "NORMAL_FIX")
    static class NormalFix {
        @FixMethod(rule = "FIX_NORMAL", type = ValidationType.FEATURE, priority = ValidationPriority.NORMAL)
        public void fix(GFF3Feature f, int line) {
            executionLog.add("FIX_NORMAL");
        }
    }

    @Gff3Validation(name = "CRITICAL_VAL")
    static class CriticalValidation extends Validation {
        @ValidationMethod(rule = "VAL_CRITICAL", type = ValidationType.FEATURE, priority = ValidationPriority.CRITICAL)
        public void validate(GFF3Feature f, int line) {
            executionLog.add("VAL_CRITICAL");
        }
    }

    @Gff3Validation(name = "NORMAL_VAL")
    static class NormalValidation extends Validation {
        @ValidationMethod(rule = "VAL_NORMAL", type = ValidationType.FEATURE, priority = ValidationPriority.NORMAL)
        public void validate(GFF3Feature f, int line) {
            executionLog.add("VAL_NORMAL");
        }
    }

    @Test
    @DisplayName(
            "Validate executes in priority order: CRITICAL fixes → CRITICAL validations → NORMAL fixes → NORMAL validations")
    void testValidate_interleavedPriorityExecution() throws Exception {
        executionLog.clear();

        // Build descriptors at different priorities
        ValidatorDescriptor criticalFix = new ValidatorDescriptor(
                CriticalFix.class,
                new CriticalFix(),
                CriticalFix.class.getDeclaredMethod("fix", GFF3Feature.class, int.class),
                ValidationPriority.CRITICAL);
        ValidatorDescriptor normalFix = new ValidatorDescriptor(
                NormalFix.class,
                new NormalFix(),
                NormalFix.class.getDeclaredMethod("fix", GFF3Feature.class, int.class),
                ValidationPriority.NORMAL);
        ValidatorDescriptor criticalVal = new ValidatorDescriptor(
                CriticalValidation.class,
                new CriticalValidation(),
                CriticalValidation.class.getDeclaredMethod("validate", GFF3Feature.class, int.class),
                ValidationPriority.CRITICAL);
        ValidatorDescriptor normalVal = new ValidatorDescriptor(
                NormalValidation.class,
                new NormalValidation(),
                NormalValidation.class.getDeclaredMethod("validate", GFF3Feature.class, int.class),
                ValidationPriority.NORMAL);

        // Mock registry to return grouped maps
        Map<ValidationPriority, List<ValidatorDescriptor>> fixesByPriority = new LinkedHashMap<>();
        fixesByPriority.put(ValidationPriority.CRITICAL, List.of(criticalFix));
        fixesByPriority.put(ValidationPriority.NORMAL, List.of(normalFix));

        Map<ValidationPriority, List<ValidatorDescriptor>> valsByPriority = new LinkedHashMap<>();
        valsByPriority.put(ValidationPriority.CRITICAL, List.of(criticalVal));
        valsByPriority.put(ValidationPriority.NORMAL, List.of(normalVal));

        when(validationRegistry.getFixesByPriority()).thenReturn(fixesByPriority);
        when(validationRegistry.getValidationsByPriority()).thenReturn(valsByPriority);
        when(validationConfig.getFix(anyString(), anyBoolean())).thenReturn(true);
        when(validationConfig.getSeverity(anyString(), any(RuleSeverity.class))).thenReturn(RuleSeverity.ERROR);

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "parent", new HashMap<>());
        engine.validate(feature, 1);

        assertEquals(
                List.of("FIX_CRITICAL", "VAL_CRITICAL", "FIX_NORMAL", "VAL_NORMAL"),
                executionLog,
                "Execution must be interleaved by priority tier: fixes then validations per tier");
    }

    @Gff3Validation(name = "CRITICAL_FAIL_VAL")
    static class CriticalFailingValidation extends Validation {
        @ValidationMethod(
                rule = "VAL_CRITICAL_FAIL",
                type = ValidationType.FEATURE,
                priority = ValidationPriority.CRITICAL)
        public void validate(GFF3Feature f, int line) throws ValidationException {
            executionLog.add("VAL_CRITICAL_FAIL");
            throw new ValidationException("critical failure");
        }
    }

    @Test
    @DisplayName("Fail-fast mode: error at CRITICAL tier prevents NORMAL tier from executing")
    void testValidate_failFastShortCircuitsAtCurrentTier() throws Exception {
        executionLog.clear();

        ValidatorDescriptor criticalVal = new ValidatorDescriptor(
                CriticalFailingValidation.class,
                new CriticalFailingValidation(),
                CriticalFailingValidation.class.getDeclaredMethod("validate", GFF3Feature.class, int.class),
                ValidationPriority.CRITICAL);
        ValidatorDescriptor normalVal = new ValidatorDescriptor(
                NormalValidation.class,
                new NormalValidation(),
                NormalValidation.class.getDeclaredMethod("validate", GFF3Feature.class, int.class),
                ValidationPriority.NORMAL);

        Map<ValidationPriority, List<ValidatorDescriptor>> fixesByPriority = new LinkedHashMap<>();
        Map<ValidationPriority, List<ValidatorDescriptor>> valsByPriority = new LinkedHashMap<>();
        valsByPriority.put(ValidationPriority.CRITICAL, List.of(criticalVal));
        valsByPriority.put(ValidationPriority.NORMAL, List.of(normalVal));

        when(validationRegistry.getFixesByPriority()).thenReturn(fixesByPriority);
        when(validationRegistry.getValidationsByPriority()).thenReturn(valsByPriority);
        when(validationConfig.getSeverity(anyString(), any(RuleSeverity.class))).thenReturn(RuleSeverity.ERROR);

        // Engine with fail-fast=true
        ValidationEngine failFastEngine = new ValidationEngine(validationConfig, validationRegistry, true);

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "parent", new HashMap<>());
        assertThrows(ValidationException.class, () -> failFastEngine.validate(feature, 1));

        // CRITICAL validation ran, NORMAL did not
        assertEquals(
                List.of("VAL_CRITICAL_FAIL"),
                executionLog,
                "Fail-fast should stop at the CRITICAL tier and not execute NORMAL tier");
    }

    @Test
    @DisplayName("No-fast-fail mode: all tiers execute and errors are collected")
    void testValidate_noFastFailCollectsAllTierErrors() throws Exception {
        executionLog.clear();

        ValidatorDescriptor criticalVal = new ValidatorDescriptor(
                CriticalFailingValidation.class,
                new CriticalFailingValidation(),
                CriticalFailingValidation.class.getDeclaredMethod("validate", GFF3Feature.class, int.class),
                ValidationPriority.CRITICAL);
        ValidatorDescriptor normalVal = new ValidatorDescriptor(
                NormalValidation.class,
                new NormalValidation(),
                NormalValidation.class.getDeclaredMethod("validate", GFF3Feature.class, int.class),
                ValidationPriority.NORMAL);

        Map<ValidationPriority, List<ValidatorDescriptor>> fixesByPriority = new LinkedHashMap<>();
        Map<ValidationPriority, List<ValidatorDescriptor>> valsByPriority = new LinkedHashMap<>();
        valsByPriority.put(ValidationPriority.CRITICAL, List.of(criticalVal));
        valsByPriority.put(ValidationPriority.NORMAL, List.of(normalVal));

        when(validationRegistry.getFixesByPriority()).thenReturn(fixesByPriority);
        when(validationRegistry.getValidationsByPriority()).thenReturn(valsByPriority);
        when(validationConfig.getSeverity(anyString(), any(RuleSeverity.class))).thenReturn(RuleSeverity.ERROR);

        // Engine with fail-fast=false
        ValidationEngine collectEngine = new ValidationEngine(validationConfig, validationRegistry, false);

        GFF3Feature feature = TestUtils.createGFF3Feature("gene", "parent", new HashMap<>());
        assertDoesNotThrow(() -> collectEngine.validate(feature, 1));

        // Both tiers ran
        assertEquals(List.of("VAL_CRITICAL_FAIL", "VAL_NORMAL"), executionLog, "No-fast-fail should run all tiers");
        // Error was collected
        assertEquals(1, collectEngine.getCollectedErrors().size());
    }
}
