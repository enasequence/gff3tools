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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
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
    void conversionToEmbl_withSequence_succeeds() throws Exception {
        Path fasta = tempDir.resolve("sequence.fasta");
        Files.writeString(
                fasta,
                ">seq1 | {\"description\":\"test\", \"molecule_type\":\"genomic DNA\", \"topology\":\"linear\"}\n"
                        + "ATGAAATAAGGGCCCAAATTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT\n");

        Path inputFile = tempDir.resolve("valid.gff3");
        Files.writeString(
                inputFile,
                """
                ##gff-version 3
                ##sequence-region seq1 1 100
                seq1\t.\tgene\t1\t21\t.\t+\t.\tID=gene1
                seq1\t.\tCDS\t1\t9\t.\t+\t0\tID=cds1;Parent=gene1
                """);

        Path outputFile = tempDir.resolve("output.embl");

        int exitCode = executeConversion(
                "conversion", "--sequence", fasta.toString(), inputFile.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Conversion with --sequence should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");
    }

    @Test
    void conversionToEmbl_withoutSequence_succeeds() throws Exception {
        Path inputFile = tempDir.resolve("valid.gff3");
        Files.writeString(
                inputFile,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                """);

        Path outputFile = tempDir.resolve("output.embl");

        // Without --sequence, conversion should still work (translation is skipped)
        int exitCode = executeConversion("conversion", inputFile.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Conversion without --sequence should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");
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

    @Test
    void testGetFileExtension_simple() {
        assertEquals(
                "tsv",
                FileConversionCommand.getFileExtension(Path.of("input.tsv")).orElse(null));
        assertEquals(
                "gff3",
                FileConversionCommand.getFileExtension(Path.of("output.gff3")).orElse(null));
        assertEquals(
                "embl",
                FileConversionCommand.getFileExtension(Path.of("data.embl")).orElse(null));
    }

    @Test
    void testGetFileExtension_gzipped() {
        // All gzipped formats should be recognized
        assertEquals(
                "tsv",
                FileConversionCommand.getFileExtension(Path.of("input.tsv.gz")).orElse(null));
        assertEquals(
                "embl",
                FileConversionCommand.getFileExtension(Path.of("input.embl.gz")).orElse(null));
        assertEquals(
                "gff3",
                FileConversionCommand.getFileExtension(Path.of("input.gff3.gz")).orElse(null));
    }

    @Test
    void testGetFileExtension_noExtension() {
        assertNull(
                FileConversionCommand.getFileExtension(Path.of("noextension")).orElse(null));
    }

    @Test
    void testGetFileExtension_withPath() {
        assertEquals(
                "tsv",
                FileConversionCommand.getFileExtension(Path.of("/some/path/to/input.tsv"))
                        .orElse(null));
        assertEquals(
                "tsv",
                FileConversionCommand.getFileExtension(Path.of("/some/path/to/input.tsv.gz"))
                        .orElse(null));
    }

    @Test
    void testConversionFileFormat_tsvIncluded() {
        // Verify tsv is a valid format
        ConversionFileFormat format = ConversionFileFormat.valueOf("tsv");
        assertEquals(ConversionFileFormat.tsv, format);
    }

    @Test
    void testOutputSequenceOption_longForm() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("--output-sequence", "output.fasta", "input.embl", "output.gff3");
        assertEquals(Path.of("output.fasta"), cmd.fastaOutputPath);
    }

    @Test
    void testOutputSequenceOption_shortForm() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("-os", "sequences.fa", "input.embl", "output.gff3");
        assertEquals(Path.of("sequences.fa"), cmd.fastaOutputPath);
    }

    @Test
    void testOutputSequenceOption_notProvided() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("input.embl", "output.gff3");
        assertNull(cmd.fastaOutputPath);
    }

    @Test
    void testOutputSequenceOption_withOtherOptions() {
        FileConversionCommand cmd = new FileConversionCommand();
        new CommandLine(cmd).parseArgs("-f", "embl", "-t", "gff3", "-os", "seqs.fasta", "input.embl", "output.gff3");
        assertEquals(Path.of("seqs.fasta"), cmd.fastaOutputPath);
        assertEquals(ConversionFileFormat.embl, cmd.fromFileType);
        assertEquals(ConversionFileFormat.gff3, cmd.toFileType);
    }

    private static final String GAPPY_FASTA =
            ">TEST01.1 | {\"description\":\"gappy\", \"molecule_type\":\"genomic DNA\", \"topology\":\"linear\"}\n"
                    + "ATGCATGCNNNNNNNNNNATGCATGCTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT\n";

    @Test
    void fastaToGff3_emitsGapFeatures() throws Exception {
        Path inputFile = tempDir.resolve("input.fasta");
        Files.writeString(inputFile, GAPPY_FASTA);
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeConversion("conversion", inputFile.toString(), outputFile.toString());

        assertEquals(0, exitCode, "FASTA to GFF3 conversion should succeed");
        assertTrue(Files.exists(outputFile), "Output GFF3 file should be created");
        String content = Files.readString(outputFile);
        assertTrue(content.contains("##sequence-region TEST01.1 1 100"), content);
        assertTrue(content.contains("TEST01.1\t.\tgap\t9\t18\t"), content);
        assertTrue(content.contains("estimated_length=10"), content);
    }

    @Test
    void fastaToGff3_gzippedInput_emitsGapFeatures() throws Exception {
        Path inputFile = tempDir.resolve("input.fasta.gz");
        try (OutputStream gz = new GZIPOutputStream(Files.newOutputStream(inputFile))) {
            gz.write(GAPPY_FASTA.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeConversion("conversion", inputFile.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Gzipped FASTA to GFF3 conversion should succeed");
        assertTrue(Files.exists(outputFile), "Output GFF3 file should be created");
        assertTrue(Files.readString(outputFile).contains("TEST01.1\t.\tgap\t9\t18\t"));
    }

    @Test
    void fastaToGff3_linkageEvidenceWithoutGapType_failsWithUsageError() throws Exception {
        Path inputFile = tempDir.resolve("input.fasta");
        Files.writeString(inputFile, GAPPY_FASTA);
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode =
                executeConversion("conversion", "-le", "unspecified", inputFile.toString(), outputFile.toString());

        assertEquals(
                CLIExitCode.USAGE.asInt(), exitCode, "--linkage-evidence without --gap-type should be a usage error");
        assertFalse(Files.exists(outputFile), "No output file should be created on a usage error");
    }

    @Test
    void fastaToGff3_gapTypeRequiringLinkage_withoutLinkage_failsWithUsageError() throws Exception {
        Path inputFile = tempDir.resolve("input.fasta");
        Files.writeString(inputFile, GAPPY_FASTA);
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode =
                executeConversion("conversion", "-gt", "within scaffold", inputFile.toString(), outputFile.toString());

        assertEquals(
                CLIExitCode.USAGE.asInt(),
                exitCode,
                "--gap-type requiring linkage without --linkage-evidence should be a usage error");
        assertFalse(Files.exists(outputFile));
    }

    @Test
    void fastaToGff3_plainSequenceFormat_failsWithUsageError() throws Exception {
        Path inputFile = tempDir.resolve("input.fasta");
        Files.writeString(inputFile, GAPPY_FASTA);
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeConversion(
                "conversion", "--sequence-format", "plain", inputFile.toString(), outputFile.toString());

        assertEquals(
                CLIExitCode.USAGE.asInt(),
                exitCode,
                "plain sequence input to FASTA to GFF3 should be a usage error, not silent empty output");
        assertFalse(Files.exists(outputFile), "No output file should be created on a usage error");
    }

    private int executeConversion(String... args) {
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }
}
