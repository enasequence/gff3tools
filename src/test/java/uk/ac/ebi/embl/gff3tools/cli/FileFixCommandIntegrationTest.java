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

class FileFixCommandIntegrationTest {

    private static final Path STALE_GAPS_GFF3 = Path.of("src/test/resources/gap_regeneration/stale_gaps.gff3");
    private static final Path STALE_GAPS_FASTA = Path.of("src/test/resources/gap_regeneration/stale_gaps.fasta");

    @TempDir
    Path tempDir;

    private int executeFix(String... args) {
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }

    private int executeFixCapturingErr(String[] args, StringWriter err) {
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }

    @Test
    void fix_regeneratesGapsAndPreservesOtherFeatures_succeeds() throws Exception {
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix(
                "fix",
                "-gff3",
                STALE_GAPS_GFF3.toString(),
                "--sequence",
                STALE_GAPS_FASTA.toString(),
                "-o",
                output.toString());

        assertEquals(0, exitCode, "fix should succeed on a valid GFF3 + FASTA pair");
        assertTrue(Files.exists(output), "Output file should be created");

        String content = Files.readString(output);
        assertTrue(content.contains("gene\t1\t21"), "Non-gap feature must be preserved untouched");
        assertFalse(content.contains("\t22\t24\t"), "Stale gap feature must be removed");
        assertTrue(content.contains("\tgap\t21\t30\t"), "New gap must be regenerated from the FASTA N-run");
        assertTrue(content.contains("estimated_length=10"), "Regenerated gap must carry the correct estimated_length");
    }

    @Test
    void fix_withGapTypeAndLinkageEvidence_appliesToRegeneratedGaps() throws Exception {
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix(
                "fix",
                "-gff3",
                STALE_GAPS_GFF3.toString(),
                "--sequence",
                STALE_GAPS_FASTA.toString(),
                "-gt",
                "within scaffold",
                "-le",
                "paired-ends",
                "-o",
                output.toString());

        assertEquals(0, exitCode);
        String content = Files.readString(output);
        assertTrue(content.contains("gap_type=within scaffold"));
        assertTrue(content.contains("linkage_evidence=paired-ends"));
    }

    @Test
    void fix_missingSequence_failsWithUsageError() {
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix("fix", "-gff3", STALE_GAPS_GFF3.toString(), "-o", output.toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode, "Missing --sequence should be a usage error");
        assertFalse(Files.exists(output));
    }

    @Test
    void fix_invalidGapType_failsWithUsageError() {
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix(
                "fix",
                "-gff3",
                STALE_GAPS_GFF3.toString(),
                "--sequence",
                STALE_GAPS_FASTA.toString(),
                "-gt",
                "not a real gap type",
                "-o",
                output.toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertFalse(Files.exists(output));
    }

    @Test
    void fix_linkageEvidenceWithoutGapType_failsWithUsageError() {
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix(
                "fix",
                "-gff3",
                STALE_GAPS_GFF3.toString(),
                "--sequence",
                STALE_GAPS_FASTA.toString(),
                "-le",
                "paired-ends",
                "-o",
                output.toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertFalse(Files.exists(output));
    }

    @Test
    void fix_accessionNotCoveredByFasta_failsAndDoesNotWriteOutput() throws Exception {
        Path gff3 = tempDir.resolve("mismatched.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seqX 1 50
                seqX\t.\tgene\t1\t20\t.\t+\t.\tID=gene1
                """);
        Path fasta = tempDir.resolve("mismatched.fasta");
        Files.writeString(
                fasta,
                ">seqY | {\"description\":\"other\", \"molecule_type\":\"dna\", \"topology\":\"linear\"}\n"
                        + "A".repeat(50) + "\n");
        Path output = tempDir.resolve("output.gff3");

        int exitCode =
                executeFix("fix", "-gff3", gff3.toString(), "--sequence", fasta.toString(), "-o", output.toString());

        // NOTE: an accession missing from the sequence source surfaces as a raw
        // IllegalStateException from ValidationUtils#resolveSequenceLength (used by
        // SequenceLengthValidation/FeatureLocationValidation), not a ValidationException, so it
        // is not collected/aggregated and instead propagates as CLIExitCode.GENERAL. This is
        // pre-existing behavior shared by every command backed by ValidationEngine and is not a
        // gap-regeneration or `fix`-command concern; out of scope to change here.
        assertEquals(
                CLIExitCode.GENERAL.asInt(),
                exitCode,
                "An accession absent from the FASTA currently surfaces as an unhandled error");
        assertFalse(Files.exists(output), "No output file should be created on failure");
    }

    @Test
    void fix_invalidFastaHeaderMoleculeType_failsWithValidationError() throws Exception {
        // Pins existing cross-cutting behavior: `fix` always registers an active FastaHeaderProvider
        // (it requires --sequence), so FastaHeaderFormatValidation always runs alongside gap
        // regeneration and rejects a header with a non-controlled-vocabulary molecule_type.
        Path fasta = tempDir.resolve("invalid_molecule_type.fasta");
        Files.writeString(
                fasta,
                ">seq1 | {\"description\":\"stale gaps fixture\", \"molecule_type\":\"dna\", \"topology\":\"linear\"}\n"
                        + "AAAAAAAAAAAAAAAAAAAANNNNNNNNNNAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n");
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix(
                "fix", "-gff3", STALE_GAPS_GFF3.toString(), "--sequence", fasta.toString(), "-o", output.toString());

        assertEquals(
                CLIExitCode.VALIDATION_ERROR.asInt(),
                exitCode,
                "An invalid molecule_type should fail fix with a validation error, not just a usage error");
        assertFalse(Files.exists(output), "No output file should be created on failure");
    }

    @Test
    void fix_missingGff3Option_failsWithUsageError() {
        StringWriter err = new StringWriter();
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFixCapturingErr(
                new String[] {"fix", "--sequence", STALE_GAPS_FASTA.toString(), "-o", output.toString()}, err);

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertTrue(err.toString().contains("-gff3"));
        assertFalse(Files.exists(output));
    }

    @Test
    void fix_missingOutputOption_failsWithUsageError() {
        StringWriter err = new StringWriter();

        int exitCode = executeFixCapturingErr(
                new String[] {"fix", "-gff3", STALE_GAPS_GFF3.toString(), "--sequence", STALE_GAPS_FASTA.toString()},
                err);

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertTrue(err.toString().contains("-o"));
    }

    @Test
    void fix_nonExistentGff3Input_failsWithUsageError() {
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix(
                "fix",
                "-gff3",
                "does_not_exist.gff3",
                "--sequence",
                STALE_GAPS_FASTA.toString(),
                "-o",
                output.toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertFalse(Files.exists(output));
    }

    @Test
    void fix_invalidGff3Extension_failsWithUsageError() throws Exception {
        Path badExt = tempDir.resolve("input.gff2");
        Files.writeString(badExt, "##gff-version 3\n");
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeFix(
                "fix", "-gff3", badExt.toString(), "--sequence", STALE_GAPS_FASTA.toString(), "-o", output.toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertFalse(Files.exists(output));
    }

    @Test
    void fix_invalidOutputExtension_failsWithUsageError() {
        Path output = tempDir.resolve("output.embl");

        int exitCode = executeFix(
                "fix",
                "-gff3",
                STALE_GAPS_GFF3.toString(),
                "--sequence",
                STALE_GAPS_FASTA.toString(),
                "-o",
                output.toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertFalse(Files.exists(output));
    }

    @Test
    void fix_outputDirectoryDoesNotExist_failsWithUsageError() {
        Path output = tempDir.resolve("missing_dir").resolve("output.gff3");

        int exitCode = executeFix(
                "fix",
                "-gff3",
                STALE_GAPS_GFF3.toString(),
                "--sequence",
                STALE_GAPS_FASTA.toString(),
                "-o",
                output.toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertFalse(Files.exists(output));
    }
}
