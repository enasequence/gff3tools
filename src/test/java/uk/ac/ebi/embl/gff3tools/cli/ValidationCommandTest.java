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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

public class ValidationCommandTest {

    @TempDir
    Path tempDir;

    private ValidationCommand validationCommand;

    @BeforeEach
    public void setUp() {
        validationCommand = new ValidationCommand();
    }

    private CommandLine commandLineFor(ValidationCommand command) {
        return new CommandLine(command)
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .registerConverter(CliParamsOption.class, new ParamsConverter());
    }

    @Test
    public void testSuccessfulValidation() throws IOException {
        // Simulate a successful validation scenario
        Path tempFile = Files.createTempFile("testFile", ".gff3");
        Files.writeString(tempFile, "# comment\n##gff-version 3\n"); // Add valid GFF3 content here

        String[] args = new String[] {tempFile.toString()};

        CommandLine commandLine = commandLineFor(validationCommand);
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

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = commandLineFor(validationCommand);
        assertDoesNotThrow(() -> commandLine.parseArgs(args));

        assertThrows(RuntimeException.class, () -> validationCommand.run());

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testNonExistingFile() {
        CommandLine commandLine = commandLineFor(validationCommand);
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

    // ── --params CLI parsing ─────────────────────────────────────────────

    @Test
    void paramsOptionParsesNamespacedKeyAndColonBearingValue() {
        String[] args = new String[] {
            "--params=PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS:30,PARAM_FIXTURE_STRING.LABEL:a:b:c", "input.gff3"
        };
        CommandLine commandLine = commandLineFor(validationCommand);
        commandLine.parseArgs(args);

        assertEquals("30", validationCommand.getParamOverrides().get("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
        assertEquals("a:b:c", validationCommand.getParamOverrides().get("PARAM_FIXTURE_STRING.LABEL"));
    }

    @Test
    void paramsOptionUpperCasesKeysOnly() {
        String[] args = new String[] {"--params=param_fixture_string.label:MixedCaseValue", "input.gff3"};
        CommandLine commandLine = commandLineFor(validationCommand);
        commandLine.parseArgs(args);

        assertEquals("MixedCaseValue", validationCommand.getParamOverrides().get("PARAM_FIXTURE_STRING.LABEL"));
    }

    @Test
    void noParamsOptionYieldsEmptyMap() {
        String[] args = new String[] {"input.gff3"};
        CommandLine commandLine = commandLineFor(validationCommand);
        commandLine.parseArgs(args);

        assertTrue(validationCommand.getParamOverrides().isEmpty());
    }

    // ── empty-map fail-fast: explicit provider always built, even with no --params ──────────

    @Test
    void noParamsStillSucceeds_usingDeclaredDefaults() throws IOException {
        Path tempFile = Files.createTempFile("testFile", ".gff3");
        Files.writeString(tempFile, "# comment\n##gff-version 3\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = commandLineFor(validationCommand);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> validationCommand.run());

        Files.deleteIfExists(tempFile);
    }

    @Test
    void mandatoryParamWithoutOverride_failsBuildAsUsage() throws IOException {
        // PARAM_FIXTURE_MANDATORY is OFF by default (see ParamFixtureValidation), so every other
        // test in this class is unaffected by its mandatory parameter; turning it back on here
        // proves the always-built explicit ParameterProvider still enforces a real mandatory
        // parameter against the real empty --params map, closing the mandatory-parameter gap for
        // the CLI.
        Path tempFile = Files.createTempFile("testFile", ".gff3");
        Files.writeString(tempFile, "# comment\n##gff-version 3\n");

        int exitCode = executeValidation("validation", "--rules=PARAM_FIXTURE_MANDATORY:ERROR", tempFile.toString());
        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);

        Files.deleteIfExists(tempFile);
    }

    // ── fail-fast paths surface as USAGE (2) before reading the file ─────────────────────────

    @Test
    void unknownParamKey_exitsUsageBeforeReadingFile() {
        int exitCode = executeValidation(
                "validation", "--params=NOT_A_REAL_RULE.NOT_A_REAL_PARAM:x", "non_existent_file.gff3");
        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
    }

    @Test
    void paramForClassLevelOffRule_exitsUsage() {
        // PARAM_FIXTURE_DISABLED_CLASS is disabled at the class level (@Gff3Validation(enabled =
        // false)), with no --rules involved at all.
        int exitCode = executeValidation(
                "validation", "--params=PARAM_FIXTURE_DISABLED_CLASS_RULE.THRESHOLD:5", "non_existent_file.gff3");
        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
    }

    @Test
    void paramForMethodSeverityOffRule_viaRulesOverride_exitsUsage() {
        int exitCode = executeValidation(
                "validation",
                "--rules=PARAM_FIXTURE_LONG:OFF",
                "--params=PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS:30",
                "non_existent_file.gff3");
        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
    }

    @Test
    void paramForFixEnableOffRule_viaInternalFixOverrideMap_failsAsCliException() throws Exception {
        // Exercises buildParameterProvider directly (same package, protected access) with a
        // fixOverrides map shaped like the one ValidationCommand assembles internally for
        // GAP_GENERATION, but keyed on the real PARAM_FIXTURE_FIX_RULE fixture, which does
        // declare a @Parameter (GAP_GENERATION does not).
        java.util.Map<String, Boolean> fixOverrides = java.util.Map.of("PARAM_FIXTURE_FIX_RULE", false);
        validationCommand.params =
                new CliParamsOption(new java.util.HashMap<>(java.util.Map.of("PARAM_FIXTURE_FIX_RULE.THRESHOLD", "5")));

        assertThrows(
                uk.ac.ebi.embl.gff3tools.exception.CLIException.class,
                () -> validationCommand.buildParameterProvider(java.util.Map.of(), fixOverrides));
    }

    // ── help listing ──────────────────────────────────────────────────────

    @Test
    void helpListingIncludesEnabledParamAndOmitsOffClassParam() {
        String help =
                validationCommand.renderParameterHelp(java.util.Map.of(), java.util.Map.of("GAP_GENERATION", false));

        assertTrue(help.contains("PARAM_FIXTURE_LONG.MIN_AMINO_ACIDS"));
        assertFalse(help.contains("PARAM_FIXTURE_DISABLED_CLASS_RULE.THRESHOLD"));
    }

    @Test
    void listParamsFlag_printsListingAndSkipsFileProcessing() {
        int exitCode = executeValidation("validation", "--list-params");
        assertEquals(0, exitCode, "--list-params must succeed without an input file");
    }

    // ── library-symmetry: a library caller building ParameterProvider directly from the same
    // raw map produces identical fail-fast behavior to the CLI ───────────────────────

    @Test
    void libraryCallerBuildingParameterProviderDirectly_matchesCliFailFastBehavior() {
        java.util.Map<String, String> raw = java.util.Map.of("NOT_A_REAL_RULE.NOT_A_REAL_PARAM", "x");
        uk.ac.ebi.embl.gff3tools.validation.ValidationConfig config =
                new uk.ac.ebi.embl.gff3tools.validation.ValidationConfig(
                        new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>());

        assertThrows(uk.ac.ebi.embl.gff3tools.validation.ParameterResolutionException.class, () -> {
            uk.ac.ebi.embl.gff3tools.validation.ParameterProvider provider =
                    new uk.ac.ebi.embl.gff3tools.validation.ParameterProvider();
            provider.configure(raw, config);
        });

        int exitCode = executeValidation(
                "validation", "--params=NOT_A_REAL_RULE.NOT_A_REAL_PARAM:x", "non_existent_file.gff3");
        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
    }

    private int executeValidation(String... args) {
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .registerConverter(CliParamsOption.class, new ParamsConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }
}
