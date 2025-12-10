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
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import uk.ac.ebi.embl.gff3tools.TestUtils;
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
        engine = new ValidationEngine(validationConfig, validationRegistry);
    }

    @Test
    public void testValidate_successfulValidation() throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        ValidationEngine validationEngine = validationEngineBuilder.build();
        validationEngine.validate(
                new GFF3Feature(
                        Optional.empty(),
                        Optional.empty(),
                        "",
                        Optional.empty(),
                        "",
                        "gene",
                        1L,
                        2L,
                        "",
                        "",
                        "",
                        new HashMap<>()),
                1);
        assertTrue(validationEngine.getParsingWarnings().isEmpty());
    }

    @Test
    public void testValidate_failingValidation() throws ValidationException, DuplicateValidationRuleException {
        ValidationEngineBuilder validationEngineBuilder = new ValidationEngineBuilder();

        ValidationEngine validationEngine = validationEngineBuilder.build();

        GFF3Feature invalidFeature = new GFF3Feature(
                Optional.empty(), Optional.empty(), "", Optional.empty(), "", "", 0L, 2L, "", "", "", new HashMap<>());
        ValidationException ex =
                Assertions.assertThrows(ValidationException.class, () -> validationEngine.validate(invalidFeature, 1));

        Assertions.assertAll(
                () -> Assertions.assertTrue(ex.getMessage().contains("Violation of rule LOCATION on line 1")));
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

        ValidatorDescriptor descriptor = new ValidatorDescriptor(DummyValidator.class, instance, m);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_X", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);
        try (MockedStatic<ValidationRegistry> mocked = mockStatic(ValidationRegistry.class)) {

            mocked.when(validationRegistry::getValidations).thenReturn(descriptors);

            GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
            engine.executeValidations(feature, 10);

            verify(instance, times(1)).validate(feature, 10);
        }
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
        ValidatorDescriptor descriptor = new ValidatorDescriptor(DummyValidator.class, new DummyValidator(), m);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_OFF", RuleSeverity.ERROR)).thenReturn(RuleSeverity.OFF);

        try (MockedStatic<ValidationRegistry> mocked = mockStatic(ValidationRegistry.class)) {
            mocked.when(validationRegistry::getValidations).thenReturn(descriptors);
            GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
            assertDoesNotThrow(() -> engine.executeValidations(feature, 1));
        }
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
        ValidatorDescriptor descriptor = new ValidatorDescriptor(DummyValidator.class, new DummyValidator(), m);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_WARN", RuleSeverity.ERROR)).thenReturn(RuleSeverity.WARN);
        try (MockedStatic<ValidationRegistry> mocked = mockStatic(ValidationRegistry.class)) {
            mocked.when(validationRegistry::getValidations).thenReturn(descriptors);

            GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
            engine.executeValidations(feature, 1);
            assertEquals(1, engine.getParsingWarnings().size());
        }
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
        ValidatorDescriptor descriptor = new ValidatorDescriptor(DummyValidator.class, new DummyValidator(), m);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getSeverity("RULE_ERR", RuleSeverity.ERROR)).thenReturn(RuleSeverity.ERROR);
        try (MockedStatic<ValidationRegistry> mocked = mockStatic(ValidationRegistry.class)) {
            mocked.when(validationRegistry::getValidations).thenReturn(descriptors);

            GFF3Feature feature = TestUtils.createGFF3Feature("featureName", "parentName", new HashMap<>());
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> engine.executeValidations(feature, 1));
            assertEquals("Violation of rule RULE_ERR on line 0: error triggered", ex.getMessage());
        }
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
        ValidatorDescriptor descriptor = new ValidatorDescriptor(DummyFix.class, instance, m);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        when(validationConfig.getFix("FIX_1", true)).thenReturn(true);
        try (MockedStatic<ValidationRegistry> mocked = mockStatic(ValidationRegistry.class)) {
            mocked.when(() -> validationRegistry.getFixs()).thenReturn(descriptors);

            engine.executeFixs(new GFF3Annotation(), 5);

            verify(instance, times(1)).fix(any(), eq(5));
        }
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
        ValidatorDescriptor descriptor = new ValidatorDescriptor(DummyFix.class, instance, m);
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

        ValidatorDescriptor descriptor = new ValidatorDescriptor(DummyValidator.class, instance, m);
        List<ValidatorDescriptor> descriptors = List.of(descriptor);

        try (MockedStatic<ValidationRegistry> mocked = mockStatic(ValidationRegistry.class)) {

            mocked.when(validationRegistry::getExits).thenReturn(descriptors);

            engine.executeExits();
            verify(instance, times(1)).onExit();
        }
    }
}
