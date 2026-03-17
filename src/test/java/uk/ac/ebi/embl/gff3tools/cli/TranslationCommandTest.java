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

class TranslationCommandTest {

    @TempDir
    Path tempDir;

    /**
     * Creates a FASTA file with JSON headers as expected by JsonHeaderFastaReader.
     * The sequence contains a CDS region at positions 1-9: ATGAAATAA
     * which translates to MK (ATG=M, AAA=K, TAA=stop).
     */
    private Path createFastaFile(String seqId) throws Exception {
        Path fasta = tempDir.resolve("sequence.fasta");
        // JsonHeaderFastaReader expects: >submissionId | {json}
        Files.writeString(fasta, ">%s | {\"description\":\"test\", \"molecule_type\":\"dna\", \"topology\":\"linear\"}\n".formatted(seqId)
                + "ATGAAATAA\n");
        return fasta;
    }

    /**
     * Creates a plain sequence file (raw nucleotide, no header).
     */
    private Path createPlainSequenceFile() throws Exception {
        Path seq = tempDir.resolve("sequence.seq");
        Files.writeString(seq, "ATGAAATAA\n");
        return seq;
    }

    /**
     * Creates a minimal GFF3 file with a single CDS feature.
     * Uses a standalone CDS (no Parent) to avoid DANGLING_PARENT validation errors.
     */
    private Path createGff3File(String seqId) throws Exception {
        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(gff3, """
                ##gff-version 3
                ##sequence-region %s 1 9
                %s\t.\tCDS\t1\t9\t.\t+\t0\tID=cds1
                """.formatted(seqId, seqId));
        return gff3;
    }

    private int executeTranslate(String... args) {
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }

    // --- gff3-fasta mode tests ---

    @Test
    void gff3FastaMode_createsOutputWithFastaSection() throws Exception {
        Path fasta = createFastaFile("seq1");
        Path gff3 = createGff3File("seq1");
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeTranslate(
                "translate", "--sequence", fasta.toString(),
                "--translation-mode", "gff3_fasta",
                "-o", output.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code");
        assertTrue(Files.exists(output), "Output file should be created");

        String content = Files.readString(output);
        assertTrue(content.contains("##gff-version"), "Output should contain GFF3 header");
        assertTrue(content.contains("##FASTA"), "Output should contain ##FASTA section");
        // Translation attributes should be stripped from features
        assertFalse(content.contains("translation="), "Features should not have translation attribute in gff3-fasta mode");
    }

    @Test
    void gff3FastaMode_isDefault() throws Exception {
        Path fasta = createFastaFile("seq1");
        Path gff3 = createGff3File("seq1");
        Path output = tempDir.resolve("output.gff3");

        // No --translation-mode specified
        int exitCode = executeTranslate(
                "translate", "--sequence", fasta.toString(),
                "-o", output.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code");
        assertTrue(Files.exists(output), "Output file should be created");

        String content = Files.readString(output);
        assertTrue(content.contains("##FASTA"), "Default mode should be gff3-fasta");
    }

    @Test
    void gff3FastaMode_defaultOutputPath() throws Exception {
        Path fasta = createFastaFile("seq1");
        Path gff3 = createGff3File("seq1");
        // No -o specified, should default to <stem>.translated.gff3
        Path expectedOutput = tempDir.resolve("input.translated.gff3");

        int exitCode = executeTranslate(
                "translate", "--sequence", fasta.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code");
        assertTrue(Files.exists(expectedOutput), "Default output file should be created at " + expectedOutput);
    }

    // --- fasta mode tests ---

    @Test
    void fastaMode_createsTranslationFasta() throws Exception {
        Path fasta = createFastaFile("seq1");
        Path gff3 = createGff3File("seq1");
        Path output = tempDir.resolve("translations.fasta");

        int exitCode = executeTranslate(
                "translate", "--sequence", fasta.toString(),
                "--translation-mode", "fasta",
                "-o", output.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code");
        assertTrue(Files.exists(output), "Translation FASTA output should be created");

        String content = Files.readString(output);
        // Should contain FASTA-formatted translation
        assertTrue(content.contains(">"), "Output should contain FASTA header");
        assertFalse(content.contains("##gff-version"), "FASTA output should not contain GFF3 directives");
    }

    @Test
    void fastaMode_defaultOutputPath() throws Exception {
        Path fasta = createFastaFile("seq1");
        Path gff3 = createGff3File("seq1");
        Path expectedOutput = tempDir.resolve("input.translation.fasta");

        int exitCode = executeTranslate(
                "translate", "--sequence", fasta.toString(),
                "--translation-mode", "fasta",
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code");
        assertTrue(Files.exists(expectedOutput), "Default fasta output file should be created at " + expectedOutput);
    }

    // --- attribute mode tests ---

    @Test
    void attributeMode_doesNotCreateOutputFile() throws Exception {
        Path fasta = createFastaFile("seq1");
        Path gff3 = createGff3File("seq1");

        int exitCode = executeTranslate(
                "translate", "--sequence", fasta.toString(),
                "--translation-mode", "attribute",
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code");
        // No output file should be created for attribute mode
        assertFalse(Files.exists(tempDir.resolve("input.translated.gff3")));
        assertFalse(Files.exists(tempDir.resolve("input.translation.fasta")));
    }

    // --- format detection tests ---

    @Test
    void formatDetection_fastaExtension() throws Exception {
        Path fasta = createFastaFile("seq1");
        Path gff3 = createGff3File("seq1");
        Path output = tempDir.resolve("output.gff3");

        // .fasta extension should be auto-detected
        int exitCode = executeTranslate(
                "translate", "--sequence", fasta.toString(),
                "-o", output.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code");
        assertTrue(Files.exists(output));
    }

    @Test
    void formatDetection_faExtension() throws Exception {
        // Create a .fa file (alias for FASTA)
        Path fa = tempDir.resolve("sequence.fa");
        Files.writeString(fa, ">seq1 | {\"description\":\"test\", \"molecule_type\":\"dna\", \"topology\":\"linear\"}\n"
                + "ATGAAATAA\n");
        Path gff3 = createGff3File("seq1");
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeTranslate(
                "translate", "--sequence", fa.toString(),
                "-o", output.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code for .fa extension");
    }

    @Test
    void formatDetection_plainSequence() throws Exception {
        Path seq = createPlainSequenceFile();
        Path gff3 = createGff3File("seq1");
        Path output = tempDir.resolve("output.gff3");

        // .seq extension should be detected as plain
        int exitCode = executeTranslate(
                "translate", "--sequence", seq.toString(),
                "-o", output.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success exit code for plain sequence");
        assertTrue(Files.exists(output));
    }

    @Test
    void formatDetection_explicitOverride() throws Exception {
        // Create a file with non-standard extension but explicit format
        Path seq = tempDir.resolve("sequence.dat");
        Files.writeString(seq, "ATGAAATAA\n");
        Path gff3 = createGff3File("seq1");
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeTranslate(
                "translate", "--sequence", seq.toString(),
                "--sequence-format", "plain",
                "-o", output.toString(),
                gff3.toString());

        assertEquals(0, exitCode, "Expected success with explicit format override");
    }

    @Test
    void formatDetection_unknownExtension_fails() throws Exception {
        Path unknown = tempDir.resolve("sequence.xyz");
        Files.writeString(unknown, "ATGAAATAA\n");
        Path gff3 = createGff3File("seq1");

        int exitCode = executeTranslate(
                "translate", "--sequence", unknown.toString(),
                gff3.toString());

        assertNotEquals(0, exitCode, "Should fail for unrecognized extension");
    }

    // --- error cases ---

    @Test
    void missingSequenceOption_fails() throws Exception {
        Path gff3 = createGff3File("seq1");

        int exitCode = executeTranslate(
                "translate", gff3.toString());

        assertNotEquals(0, exitCode, "Should fail when --sequence is not provided");
    }

    @Test
    void nonExistentSequenceFile_fails() throws Exception {
        Path gff3 = createGff3File("seq1");

        int exitCode = executeTranslate(
                "translate", "--sequence", "/tmp/nonexistent.fasta",
                gff3.toString());

        assertNotEquals(0, exitCode, "Should fail for non-existent sequence file");
    }
}
