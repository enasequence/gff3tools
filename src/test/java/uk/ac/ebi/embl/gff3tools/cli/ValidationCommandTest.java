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
package uk.ac.ebi.embl.gff3tools.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationConfig;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.builtin.AttributesValueValidation;
import uk.ac.ebi.embl.gff3tools.validation.fix.EcNumberValueFix;
import uk.ac.ebi.embl.gff3tools.validation.fix.ProteinIdRemoval;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidatorDescriptor;

public class ValidationCommandTest {

    @TempDir
    Path tempDir;

    private ValidationCommand validationCommand;

    @BeforeEach
    public void setUp() {
        validationCommand = new ValidationCommand();
    }

    @Test
    public void testSuccessfulValidation() throws IOException {
        // Simulate a successful validation scenario
        Path tempFile = Files.createTempFile("testFile", ".gff3");
        Files.writeString(tempFile, "# comment\n##gff-version 3\n"); // Add valid GFF3 content here

        String[] args = new String[] {"%s".formatted(tempFile.toString())};

        CommandLine commandLine =
                new CommandLine(validationCommand).registerConverter(CliRulesOption.class, new RuleConverter());
        assertDoesNotThrow(() -> commandLine.parseArgs(args));

        assertDoesNotThrow(() -> validationCommand.run());

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testHandleParsingErrors() throws IOException {
        // Create a file with parsing errors
        Path tempFile = Files.createTempFile("invalidTestFile", ".gff3");
        Files.writeString(tempFile, "invalid content\n"); // Invalid GFF3 content

        String[] args = new String[] {"%s".formatted(tempFile.toString())};
        CommandLine commandLine =
                new CommandLine(validationCommand).registerConverter(CliRulesOption.class, new RuleConverter());
        assertDoesNotThrow(() -> commandLine.parseArgs(args));

        assertThrows(RuntimeException.class, () -> validationCommand.run());

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testNonExistingFile() {
        CommandLine commandLine =
                new CommandLine(validationCommand).registerConverter(CliRulesOption.class, new RuleConverter());
        assertDoesNotThrow(() -> commandLine.parseArgs(new String[] {"non_existent_file.gff3 "}));
        assertThrows(RuntimeException.class, () -> validationCommand.run());
    }

    @Test
    void validationWithSequence_succeeds() throws Exception {
        Path fasta = tempDir.resolve("sequence.fasta");
        Files.writeString(
                fasta,
                ">seq1 | {\"description\":\"test\", \"molecule_type\":\"dna\", \"topology\":\"linear\"}\n"
                        + "ATGTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTAATTTTTTT\n");

        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seq1 1 100
                seq1\t.\tCDS\t1\t93\t.\t+\t0\tID=cds1
                """);

        int exitCode = executeValidation("validation", "--sequence", fasta.toString(), gff3.toString());
        assertEquals(0, exitCode, "Validation with --sequence should succeed");
    }

    @Test
    void validationWithSequence_noSequence_stillSucceeds() throws Exception {
        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seq1 1 93
                seq1\t.\tCDS\t1\t93\t.\t+\t0\tID=cds1
                """);

        // Without --sequence, validation should still work (translation is skipped)
        int exitCode = executeValidation("validation", gff3.toString());
        assertEquals(0, exitCode, "Validation without --sequence should succeed");
    }

    private int executeValidation(String... args) {
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    @Test
    void fixesOverride_structuralOverrideWinsOverCliFixesOption() throws Exception {
        // Simulates "--fixes GAP_GENERATION:ON": the command's own structural override
        // (this command discards the fixed annotation, see run()) must still win.
        validationCommand.fixes = new CliFixesOption(Map.of("GAP_GENERATION", true));

        try (ValidationEngine engine =
                validationCommand.initValidationEngine(Map.of(), Map.of("GAP_GENERATION", false))) {
            ValidationConfig config = getPrivateField(engine, "validationConfig");
            assertFalse(
                    config.getFix("GAP_GENERATION", true),
                    "Command's structural fixOverrides must win over a CLI --fixes toggle");
        }
    }

    @Test
    void fixesOverride_cliValueAppliedWhenNoStructuralOverride() throws Exception {
        // Simulates "--fixes LOCUS_TAG_TO_UPPERCASE:OFF" with no command-level override for it.
        validationCommand.fixes = new CliFixesOption(Map.of("LOCUS_TAG_TO_UPPERCASE", false));

        try (ValidationEngine engine = validationCommand.initValidationEngine(Map.of(), Map.of())) {
            ValidationConfig config = getPrivateField(engine, "validationConfig");
            assertFalse(config.getFix("LOCUS_TAG_TO_UPPERCASE", true), "CLI --fixes toggle should apply");
        }
    }

    @Test
    void fixesOverride_cliOnReenablesClassDisabledFix() throws Exception {
        // PROTEIN_ID_REMOVE is @Gff3Fix(enabled = false): a class-disabled fix is filtered out
        // entirely at registration, so only "--fixes PROTEIN_ID_REMOVE:ON" re-enabling the class
        // (not just the method-level fixOverrides map) makes it run at all.
        validationCommand.fixes = new CliFixesOption(Map.of("PROTEIN_ID_REMOVE", true));

        try (ValidationEngine engine = validationCommand.initValidationEngine(Map.of(), Map.of())) {
            Object registry = getPrivateField(engine, "validationRegistry");
            List<ValidatorDescriptor> fixes = (List<ValidatorDescriptor>)
                    registry.getClass().getMethod("getFixs").invoke(registry);
            assertTrue(
                    fixes.stream().anyMatch(d -> d.clazz().equals(ProteinIdRemoval.class)),
                    "ProteinIdRemoval must be registered once --fixes PROTEIN_ID_REMOVE:ON is set");
        }
    }

    @Test
    void fixesOverride_classDisabledFixNotRegisteredByDefault() throws Exception {
        try (ValidationEngine engine = validationCommand.initValidationEngine(Map.of(), Map.of())) {
            Object registry = getPrivateField(engine, "validationRegistry");
            List<ValidatorDescriptor> fixes = (List<ValidatorDescriptor>)
                    registry.getClass().getMethod("getFixs").invoke(registry);
            assertTrue(
                    fixes.stream().noneMatch(d -> d.clazz().equals(ProteinIdRemoval.class)),
                    "ProteinIdRemoval is class-disabled by default and must not be registered without --fixes");
        }
    }

    @Test
    void fixesOverride_doesNotDisableSameNamedValidation() throws Exception {
        // ATTRIBUTES_VALUE is both a fix class name (AttributeValueFix) and a validation class name
        // (AttributesValueValidation). "--fixes ATTRIBUTES_VALUE:OFF" must not reach
        // overrideClassRules and disable the unrelated validation class.
        validationCommand.fixes = new CliFixesOption(Map.of("ATTRIBUTES_VALUE", false));

        try (ValidationEngine engine = validationCommand.initValidationEngine(Map.of(), Map.of())) {
            Object registry = getPrivateField(engine, "validationRegistry");
            List<ValidatorDescriptor> validations = (List<ValidatorDescriptor>)
                    registry.getClass().getMethod("getValidations").invoke(registry);
            assertTrue(
                    validations.stream().anyMatch(d -> d.clazz().equals(AttributesValueValidation.class)),
                    "AttributesValueValidation must remain registered; --fixes must not bleed into the validation namespace");
        }
    }

    @Test
    void fixesOverride_doesNotDisableSiblingFixMethodInSameClass() throws Exception {
        // EcNumberValueFix (class name EC_NUMBER) declares two @FixMethod rules: EC_NUMBER and
        // PRODUCT_WITH_EC_NUMBER. "--fixes EC_NUMBER:OFF" must only toggle the EC_NUMBER method
        // (via method-level fixOverrides), not disable the whole class and take
        // PRODUCT_WITH_EC_NUMBER down with it.
        validationCommand.fixes = new CliFixesOption(Map.of("EC_NUMBER", false));

        try (ValidationEngine engine = validationCommand.initValidationEngine(Map.of(), Map.of())) {
            Object registry = getPrivateField(engine, "validationRegistry");
            List<ValidatorDescriptor> fixes = (List<ValidatorDescriptor>)
                    registry.getClass().getMethod("getFixs").invoke(registry);
            assertTrue(
                    fixes.stream().anyMatch(d -> d.clazz().equals(EcNumberValueFix.class)),
                    "EcNumberValueFix must remain registered; --fixes EC_NUMBER:OFF must not disable the whole class");

            ValidationConfig config = getPrivateField(engine, "validationConfig");
            assertFalse(
                    config.getFix("EC_NUMBER", true),
                    "EC_NUMBER method itself should still be toggled off via method-level fixOverrides");
            assertTrue(
                    config.getFix("PRODUCT_WITH_EC_NUMBER", true),
                    "Sibling method PRODUCT_WITH_EC_NUMBER must not be affected by the EC_NUMBER class-name collision");
        }
    }
}
