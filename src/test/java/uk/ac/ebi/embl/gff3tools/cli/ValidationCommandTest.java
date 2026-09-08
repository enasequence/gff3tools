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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

public class ValidationCommandTest {

    @TempDir
    Path tempDir;

    private ValidationCommand validationCommand;

    private static final String GAPPY_FASTA =
            ">seq1 | {\"description\":\"test\", \"molecule_type\":\"genomic DNA\", \"topology\":\"linear\"}\n"
                    + "ATGCATGCNNNNNNNNNNATGCATGCTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT\n";

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

    @Test
    void validation_outputToFile_writesFixedGff3() throws Exception {
        // gene lacking gene_synonym/gene on the mRNA/rRNA triggers CdsRnaLocusFix to propagate it
        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3.1.26
                ##sequence-region BN000065.1 1 315242
                BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;
                BN000065.1\t.\tmRNA\t133806\t191728\t.\t+\t.\tParent=gene_RHD;
                BN000065.1\t.\trRNA\t133970\t145841\t.\t+\t.\tParent=gene_RHD;number=1;
                """);
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeValidation("validation", gff3.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Validation with output should succeed");
        assertTrue(Files.exists(outputFile), "Output file should be created");
        String content = Files.readString(outputFile);
        String rrnaLine =
                content.lines().filter(l -> l.contains("rRNA")).findFirst().orElse("");
        assertTrue(rrnaLine.contains("gene=RHD"), "Fix should have propagated gene=RHD onto the rRNA: " + rrnaLine);
    }

    @Test
    void validation_outputToStdout_writesFixedGff3() throws Exception {
        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3.1.26
                ##sequence-region BN000065.1 1 315242
                BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;
                BN000065.1\t.\tmRNA\t133806\t191728\t.\t+\t.\tParent=gene_RHD;
                BN000065.1\t.\trRNA\t133970\t145841\t.\t+\t.\tParent=gene_RHD;number=1;
                """);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        int exitCode;
        try {
            System.setOut(new PrintStream(capturedOut));
            exitCode = executeValidation("validation", gff3.toString(), "-");
        } finally {
            System.setOut(originalOut);
        }

        assertEquals(0, exitCode, "Validation with '-' output should succeed");
        String content = capturedOut.toString(StandardCharsets.UTF_8);
        assertTrue(content.contains("##gff-version 3.1.26"), "Fixed gff3 should be written to stdout: " + content);
        String rrnaLine =
                content.lines().filter(l -> l.contains("rRNA")).findFirst().orElse("");
        assertTrue(rrnaLine.contains("gene=RHD"), "Fix should have propagated gene=RHD onto the rRNA: " + rrnaLine);
    }

    @Test
    void validation_reportOnlyDefault_noOutputArgument_noFileWritten() throws Exception {
        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(gff3, "##gff-version 3\n");

        int exitCode = executeValidation("validation", gff3.toString());

        assertEquals(0, exitCode, "Report-only validation should succeed");
        // Nothing to write to and nothing written: the directory holds only the input file.
        try (var entries = Files.list(gff3.getParent())) {
            assertEquals(1, entries.count(), "No output file should be created in report-only mode");
        }
    }

    @Test
    void validation_headerOnlyFile_withOutput_doesNotThrow() throws Exception {
        // Regression test: GFF3FileReader.read() replays a final null annotation at EOF when the
        // file has no annotations at all; the output path must not NPE on that.
        Path gff3 = tempDir.resolve("header_only.gff3");
        Files.writeString(gff3, "##gff-version 3\n");
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeValidation("validation", gff3.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Header-only file with output should not throw");
        assertTrue(Files.exists(outputFile), "Output file should be created");
        assertEquals("##gff-version 3\n", Files.readString(outputFile));
    }

    @Test
    void validation_gzippedInput_succeeds() throws Exception {
        Path gff3Gz = tempDir.resolve("input.gff3.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gff3Gz))) {
            out.write("##gff-version 3\n".getBytes(StandardCharsets.UTF_8));
        }
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeValidation("validation", gff3Gz.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Gzipped input should be transparently decompressed");
        assertEquals("##gff-version 3\n", Files.readString(outputFile));
    }

    @Test
    void validation_stdinInput_withOutput_warnsAndOmitsFastaSection() throws Exception {
        String gff3WithFasta = "##gff-version 3.1.26\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n"
                + "##FASTA\n"
                + ">BN000065.1|CDS_RHD\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLEDQKGLVASYQVGQDLTVMAAI\n";
        Path outputFile = tempDir.resolve("output.gff3");

        InputStream originalIn = System.in;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        int exitCode;
        try {
            System.setIn(new ByteArrayInputStream(gff3WithFasta.getBytes(StandardCharsets.UTF_8)));
            System.setErr(new PrintStream(errContent));
            // An explicit "" for the input positional forces the stdin path, matching how an
            // absent argument is also treated (see AbstractCommand#isStdioSentinel).
            exitCode = executeValidation("validation", "", outputFile.toString());
        } finally {
            System.setIn(originalIn);
            System.setErr(originalErr);
        }

        assertEquals(0, exitCode, "Validation reading from stdin with output should succeed");
        String warnings = errContent.toString(StandardCharsets.UTF_8);
        assertTrue(
                warnings.contains("Reading from stdin") && warnings.contains("FASTA"),
                "Expected a warning about the FASTA section being omitted for stdin input: " + warnings);
        String content = Files.readString(outputFile);
        assertFalse(content.contains("##FASTA"), "FASTA section cannot be re-read from stdin: " + content);
        assertTrue(content.contains("ID=gene_RHD"), "Annotation content should still be written: " + content);
    }

    @Test
    void validation_gzippedInputWithFastaSection_preservesFastaSection() throws Exception {
        String gff3WithFasta = "##gff-version 3.1.26\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n"
                + "##FASTA\n"
                + ">BN000065.1|CDS_RHD\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLEDQKGLVASYQVGQDLTVMAAI\n";
        Path gff3Gz = tempDir.resolve("input.gff3.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gff3Gz))) {
            out.write(gff3WithFasta.getBytes(StandardCharsets.UTF_8));
        }
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeValidation("validation", gff3Gz.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Validation of gzipped input with output should succeed");
        String content = Files.readString(outputFile);
        assertTrue(
                content.contains("##FASTA")
                        && content.contains(">BN000065.1|CDS_RHD")
                        && content.contains("MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLEDQKGLVASYQVGQDLTVMAAI"),
                "The FASTA/translation section must round-trip from gzipped input, not be lost: " + content);
    }

    @Test
    void validation_gapGeneration_firesOnlyWhenOutputRequested_andRespectsMinGapLength() throws Exception {
        Path fasta = tempDir.resolve("sequence.fasta");
        Files.writeString(fasta, GAPPY_FASTA);

        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seq1 1 100
                seq1\t.\tgene\t1\t8\t.\t+\t.\tID=gene1
                """);
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode =
                executeValidation("validation", "--sequence", fasta.toString(), gff3.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Validation with output and a gap-worthy sequence should succeed");
        String content = Files.readString(outputFile);
        assertTrue(
                content.lines().anyMatch(l -> l.contains("\tgap\t9\t18\t")),
                "A gap feature covering the 10-base N run should have been generated: " + content);
    }

    @Test
    void validation_gapType_reachesGeneratedGapFeature() throws Exception {
        Path fasta = tempDir.resolve("sequence.fasta");
        Files.writeString(fasta, GAPPY_FASTA);

        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seq1 1 100
                seq1\t.\tgene\t1\t8\t.\t+\t.\tID=gene1
                """);
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode = executeValidation(
                "validation",
                "--sequence",
                fasta.toString(),
                "--gap-type",
                "telomere",
                gff3.toString(),
                outputFile.toString());

        assertEquals(0, exitCode, "Validation with --gap-type should succeed");
        String content = Files.readString(outputFile);
        String gapLine = content.lines()
                .filter(l -> l.contains("\tgap\t9\t18\t"))
                .findFirst()
                .orElse("");
        assertTrue(gapLine.contains("gap_type=telomere"), "--gap-type should reach the generated gap: " + gapLine);
    }

    @Test
    void validation_withSequence_writesComputedTranslationToFastaSection() throws Exception {
        Path fasta = tempDir.resolve("cds.fasta");
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
        Path outputFile = tempDir.resolve("output.gff3");

        int exitCode =
                executeValidation("validation", "--sequence", fasta.toString(), gff3.toString(), outputFile.toString());

        assertEquals(0, exitCode, "Validation with output and --sequence should succeed");
        String content = Files.readString(outputFile);
        assertTrue(
                content.contains("##FASTA")
                        && content.contains(">seq1|cds1")
                        && content.contains("MFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"),
                "The CDS translation computed by TranslationFix must be written to the output's "
                        + "##FASTA section, not silently discarded: " + content);
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
}
