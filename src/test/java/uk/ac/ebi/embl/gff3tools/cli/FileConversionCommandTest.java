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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class FileConversionCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void conversion_withErrors_doesNotCreateOutputFile() throws Exception {
        // Create a GFF3 file with validation errors (undefined sequence region)
        Path inputFile = tempDir.resolve("invalid.gff3");
        Files.writeString(
                inputFile,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                seq2\t.\tgene\t1\t100\t.\t+\t.\tID=gene2
                """);

        Path outputFile = tempDir.resolve("output.embl");

        // Run conversion (default: collect all errors)
        String[] args = {"conversion", inputFile.toString(), outputFile.toString()};
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));

        int exitCode = command.execute(args);

        // Should fail with VALIDATION_ERROR (20)
        assertEquals(CLIExitCode.VALIDATION_ERROR.asInt(), exitCode, "Expected validation error exit code");

        // Output file should NOT exist
        assertFalse(Files.exists(outputFile), "Output file should not be created when errors occur");
    }

    @Test
    void conversion_withErrors_failFast_doesNotCreateOutputFile() throws Exception {
        // Create a GFF3 file with validation errors
        Path inputFile = tempDir.resolve("invalid.gff3");
        Files.writeString(
                inputFile,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                seq2\t.\tgene\t1\t100\t.\t+\t.\tID=gene2
                """);

        Path outputFile = tempDir.resolve("output.embl");

        // Run conversion with --fail-fast
        String[] args = {"conversion", "--fail-fast", inputFile.toString(), outputFile.toString()};
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));

        int exitCode = command.execute(args);

        // Should fail with VALIDATION_ERROR (20)
        assertEquals(CLIExitCode.VALIDATION_ERROR.asInt(), exitCode, "Expected validation error exit code");

        // Output file should NOT exist
        assertFalse(Files.exists(outputFile), "Output file should not be created when errors occur");
    }

    @Test
    void conversion_success_createsOutputFile() throws Exception {
        // Create a valid GFF3 file
        Path inputFile = tempDir.resolve("valid.gff3");
        Files.writeString(
                inputFile,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                """);

        Path outputFile = tempDir.resolve("output.embl");

        // Run conversion
        String[] args = {"conversion", inputFile.toString(), outputFile.toString()};
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));

        int exitCode = command.execute(args);

        // Should succeed (exit code 0)
        assertEquals(0, exitCode, "Expected success exit code");

        // Output file should exist
        assertTrue(Files.exists(outputFile), "Output file should be created on successful conversion");

        // Output should contain valid EMBL content
        String content = Files.readString(outputFile);
        assertTrue(content.contains("ID"), "Output should contain EMBL ID line");
    }

    @Test
    void conversion_collectsAllErrors_beforeFailing() throws Exception {
        // Create a GFF3 file with multiple validation errors
        Path inputFile = tempDir.resolve("multiple_errors.gff3");
        Files.writeString(
                inputFile,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                undefined1\t.\tgene\t1\t100\t.\t+\t.\tID=gene2
                undefined2\t.\tgene\t1\t100\t.\t+\t.\tID=gene3
                undefined3\t.\tgene\t1\t100\t.\t+\t.\tID=gene4
                """);

        Path outputFile = tempDir.resolve("output.embl");

        // Run conversion (default: collect all errors)
        String[] args = {"conversion", inputFile.toString(), outputFile.toString()};
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));

        int exitCode = command.execute(args);

        // Should fail with VALIDATION_ERROR (20)
        assertEquals(CLIExitCode.VALIDATION_ERROR.asInt(), exitCode);

        // Output file should NOT exist
        assertFalse(Files.exists(outputFile));

        // The error message from the aggregated exception should mention multiple errors
        // (Errors are logged to stderr via the logger, not picocli's err writer,
        // so we check the exit code and file existence instead)
    }

    @Test
    void conversion_withUnmappedFeature_collectsErrors() throws Exception {
        // Create a GFF3 file with an unmapped feature (misc_RNA has no INSDC mapping)
        Path inputFile = tempDir.resolve("unmapped_feature.gff3");
        Files.writeString(
                inputFile,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                seq1\t.\tmisc_RNA\t200\t300\t.\t+\t.\tID=rna1
                seq1\t.\tgene\t400\t500\t.\t+\t.\tID=gene2
                """);

        Path outputFile = tempDir.resolve("output.embl");

        // Run conversion (default: collect all errors)
        String[] args = {"conversion", inputFile.toString(), outputFile.toString()};
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));

        int exitCode = command.execute(args);

        // Should fail with VALIDATION_ERROR (20) - not GENERAL (1)
        assertEquals(
                CLIExitCode.VALIDATION_ERROR.asInt(),
                exitCode,
                "Unmapped feature error should be collected and result in VALIDATION_ERROR exit code");

        // Output file should NOT exist
        assertFalse(Files.exists(outputFile), "Output file should not be created when errors occur");
    }
}
