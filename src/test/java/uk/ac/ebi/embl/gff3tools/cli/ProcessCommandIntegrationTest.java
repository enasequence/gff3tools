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
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

/**
 * Integration tests for the process command and its sub-commands.
 * Tests the full workflow from count-regions to replace-ids.
 */
public class ProcessCommandIntegrationTest {

    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    // ========================================================================
    // End-to-End Workflow Tests
    // ========================================================================

    @Test
    void testCountThenReplaceWorkflow() throws IOException {
        // Create a test file with multiple sequence regions
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
                        + "BN000066.2\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            // Step 1: Count regions
            String[] countArgs = {"process", "count-regions", inputFile.toString()};
            Main.main(countArgs);
            mock.verify(() -> Main.exit(0));

            String count = outContent.toString().trim();
            assertEquals("2", count, "Should count 2 sequence regions");

            // Reset output stream
            outContent.reset();

            // Step 2: Replace IDs with the correct number of accessions
            String[] replaceArgs = {
                "process",
                "replace-ids",
                "--accessions",
                "ACC001,ACC002",
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(replaceArgs);
            mock.verify(() -> Main.exit(0), times(2));

            // Verify output file content
            String output = Files.readString(outputFile);
            assertTrue(output.contains("##sequence-region ACC001 1 5000"));
            assertTrue(output.contains("##sequence-region ACC002 1 3000"));
            assertTrue(output.contains("ACC001\tENA\tgene\t100\t500"));
            assertTrue(output.contains("ACC002\tENA\tgene\t200\t600"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testSequentialMappingOrder() throws IOException {
        // Verify that accessions are mapped in file order, not alphabetically
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region ZZZ999 1 1000\n"
                        + // Z comes last alphabetically
                        "##sequence-region AAA111 1 2000\n"
                        + // A comes first alphabetically
                        "##sequence-region MMM555 1 3000\n"
                        + // M is in the middle
                        "ZZZ999\tENA\tgene\t1\t100\t.\t+\t.\tID=gene1\n"
                        + "AAA111\tENA\tgene\t1\t200\t.\t+\t.\tID=gene2\n"
                        + "MMM555\tENA\tgene\t1\t300\t.\t+\t.\tID=gene3\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            // First accession should replace ZZZ999, second should replace AAA111, third should replace MMM555
            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                "FIRST,SECOND,THIRD",
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);
            mock.verify(() -> Main.exit(0));

            String output = Files.readString(outputFile);

            // Verify sequential mapping (file order, not alphabetical)
            assertTrue(output.contains("##sequence-region FIRST 1 1000"));
            assertTrue(output.contains("##sequence-region SECOND 1 2000"));
            assertTrue(output.contains("##sequence-region THIRD 1 3000"));
            assertTrue(output.contains("FIRST\tENA\tgene\t1\t100"));
            assertTrue(output.contains("SECOND\tENA\tgene\t1\t200"));
            assertTrue(output.contains("THIRD\tENA\tgene\t1\t300"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testFASTASectionPreserved() throws IOException {
        // Verify that FASTA section is copied unchanged
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "BN000065.1\tENA\tCDS\t100\t500\t.\t+\t0\tID=cds1\n"
                        + "##FASTA\n"
                        + ">cds1\n"
                        + "ATGCATGCATGC\n"
                        + ">BN000065.1\n"
                        + // Even if FASTA header matches old sequence region ID
                        "GGGGCCCCAAAA\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process", "replace-ids", "--accessions", "ACC123", "-o", outputFile.toString(), inputFile.toString()
            };
            Main.main(args);
            mock.verify(() -> Main.exit(0));

            String output = Files.readString(outputFile);

            // Verify FASTA section is unchanged (per R5 and assumption about FASTA headers)
            assertTrue(output.contains("##FASTA"));
            assertTrue(output.contains(">cds1"));
            assertTrue(output.contains("ATGCATGCATGC"));
            assertTrue(output.contains(">BN000065.1")); // FASTA header NOT replaced
            assertTrue(output.contains("GGGGCCCCAAAA"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testVersionNumberRemoval() throws IOException {
        // Verify that version numbers are removed when replacing
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.12 1 3000\n"
                        + "##sequence-region BN000067 1 2000\n"
                        + // No version
                        "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
                        + "BN000066.12\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n"
                        + "BN000067\tENA\tgene\t300\t700\t.\t+\t.\tID=gene3\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                "ACC001,ACC002,ACC003",
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);
            mock.verify(() -> Main.exit(0));

            String output = Files.readString(outputFile);

            // All replacements should use versionless accessions
            assertFalse(output.contains("BN000065.1"));
            assertFalse(output.contains("BN000066.12"));
            assertFalse(output.contains("BN000067"));
            assertTrue(output.contains("ACC001\tENA\tgene\t100\t500"));
            assertTrue(output.contains("ACC002\tENA\tgene\t200\t600"));
            assertTrue(output.contains("ACC003\tENA\tgene\t300\t700"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testIdempotency() throws IOException {
        // Test R12: Running replace-ids twice should be idempotent with same accessions
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n");

        Path outputFile1 = Files.createTempFile("output1", ".gff3");
        Path outputFile2 = Files.createTempFile("output2", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            // First replacement
            String[] args1 = {
                "process", "replace-ids", "--accessions", "ACC123", "-o", outputFile1.toString(), inputFile.toString()
            };
            Main.main(args1);

            // Second replacement using first output as input
            String[] args2 = {
                "process", "replace-ids", "--accessions", "ACC123", "-o", outputFile2.toString(), outputFile1.toString()
            };
            Main.main(args2);

            // Both outputs should be identical
            String output1 = Files.readString(outputFile1);
            String output2 = Files.readString(outputFile2);
            assertEquals(output1, output2, "Running replace-ids twice with same accessions should be idempotent");
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile1);
            Files.deleteIfExists(outputFile2);
        }
    }

    // ========================================================================
    // Error Handling Tests
    // ========================================================================

    @Test
    void testMismatchedCountTooFewAccessions() throws IOException {
        // Test R7: Exit with error when fewer accessions than sequence regions
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n"
                        + "##sequence-region BN000067 1 2000\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                "ACC001,ACC002", // Only 2, but file has 3
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);

            // Should exit with USAGE error (code 2)
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));

            // Should show expected vs provided counts
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(errorOutput.contains("3"), "Error should mention expected count of 3");
            assertTrue(errorOutput.contains("2"), "Error should mention provided count of 2");
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testMismatchedCountTooManyAccessions() throws IOException {
        // Test R7: Exit with error when more accessions than sequence regions
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(inputFile, "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                "ACC001,ACC002,ACC003", // 3, but file has only 1
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);

            // Should exit with USAGE error (code 2)
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));

            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(errorOutput.contains("1"), "Error should mention expected count of 1");
            assertTrue(errorOutput.contains("3"), "Error should mention provided count of 3");
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testEmptyAccession() throws IOException {
        // Test R9: Reject empty accessions
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                "ACC001,,ACC002", // Empty second accession (double comma)
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));

            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("empty") || errorOutput.contains("blank"),
                    "Error should mention empty/blank accession");
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testBlankAccession() throws IOException {
        // Test R9: Reject blank accessions (whitespace only)
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                "ACC001,   ", // Blank second accession (whitespace)
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));

            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(errorOutput.contains("empty") || errorOutput.contains("blank"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testWhitespaceTrimming() throws IOException {
        // Test R9b: Whitespace around commas is trimmed
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                " ACC001 , ACC002 ", // Whitespace around accessions
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            String output = Files.readString(outputFile);
            assertTrue(output.contains("ACC001")); // No leading/trailing whitespace
            assertTrue(output.contains("ACC002"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testInvalidGFF3Format() throws IOException {
        // Test R11: Invalid GFF3 should exit with VALIDATION_ERROR
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(inputFile, "Not a valid GFF3 file\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            // Test count-regions
            String[] countArgs = {"process", "count-regions", inputFile.toString()};
            Main.main(countArgs);
            mock.verify(() -> Main.exit(CLIExitCode.VALIDATION_ERROR.asInt()));

            // Reset and test replace-ids
            errContent.reset();
            String[] replaceArgs = {
                "process", "replace-ids", "--accessions", "ACC001", "-o", outputFile.toString(), inputFile.toString()
            };
            Main.main(replaceArgs);
            mock.verify(() -> Main.exit(CLIExitCode.VALIDATION_ERROR.asInt()), times(2));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    // ========================================================================
    // Stdin/Stdout Tests
    // ========================================================================

    @Test
    void testCountRegionsStdout() throws IOException {
        // Test R8: count-regions outputs to stdout by default
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {"process", "count-regions", inputFile.toString()};
            Main.main(args);

            mock.verify(() -> Main.exit(0));
            assertEquals("2", outContent.toString().trim());

            // Test R10: No info logs should appear in stdout when outputting count
            String output = outContent.toString();
            assertFalse(output.contains("INFO"), "Stdout should not contain INFO logs");
            assertFalse(output.contains("Counting"), "Stdout should not contain informational messages");
        } finally {
            Files.deleteIfExists(inputFile);
        }
    }

    @Test
    void testReplaceIdsStdout() throws IOException {
        // Test R8 & R10: replace-ids can write to stdout with suppressed logs
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            // No output file specified = stdout
            String[] args = {"process", "replace-ids", "--accessions", "ACC123", inputFile.toString()};
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            String output = outContent.toString();
            assertTrue(output.contains("##sequence-region ACC123 1 5000"));
            assertTrue(output.contains("ACC123\tENA\tgene\t100\t500"));

            // Verify no info logs in stdout
            assertFalse(output.contains("INFO"), "Stdout should not contain INFO logs");
            assertFalse(output.contains("Replacing"), "Stdout should not contain informational messages");
        } finally {
            Files.deleteIfExists(inputFile);
        }
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    void testEmptyFile() throws IOException {
        // GFF3 file with only version header, no sequence regions
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(inputFile, "##gff-version 3\n");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] countArgs = {"process", "count-regions", inputFile.toString()};
            Main.main(countArgs);

            assertEquals("0", outContent.toString().trim());
            mock.verify(() -> Main.exit(0));
        } finally {
            Files.deleteIfExists(inputFile);
        }
    }

    @Test
    void testFileWithOnlyFASTA() throws IOException {
        // GFF3 file with no sequence regions, only FASTA section
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(inputFile, "##gff-version 3\n" + "##FASTA\n" + ">seq1\n" + "ATGCATGC\n");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] countArgs = {"process", "count-regions", inputFile.toString()};
            Main.main(countArgs);

            assertEquals("0", outContent.toString().trim());
            mock.verify(() -> Main.exit(0));
        } finally {
            Files.deleteIfExists(inputFile);
        }
    }

    @Test
    void testManySequenceRegions() throws IOException {
        // Test with 100 sequence regions to verify performance
        StringBuilder gff3Content = new StringBuilder("##gff-version 3\n");
        StringBuilder accessions = new StringBuilder();

        for (int i = 1; i <= 100; i++) {
            gff3Content.append(String.format("##sequence-region SEQ%03d 1 %d\n", i, i * 1000));
            if (i > 1) accessions.append(",");
            accessions.append(String.format("ACC%03d", i));
        }

        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(inputFile, gff3Content.toString());

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            // Count should be fast
            String[] countArgs = {"process", "count-regions", inputFile.toString()};
            Main.main(countArgs);
            assertEquals("100", outContent.toString().trim());

            outContent.reset();

            // Replace should handle 100 replacements
            String[] replaceArgs = {
                "process",
                "replace-ids",
                "--accessions",
                accessions.toString(),
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(replaceArgs);
            mock.verify(() -> Main.exit(0), times(2));

            String output = Files.readString(outputFile);
            assertTrue(output.contains("##sequence-region ACC001 1 1000"));
            assertTrue(output.contains("##sequence-region ACC100 1 100000"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testSpecialCharactersInAccessions() throws IOException {
        // Test accessions with underscores, hyphens, dots (common in real accessions)
        Path inputFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                inputFile,
                "##gff-version 3\n" + "##sequence-region SEQ1 1 1000\n"
                        + "##sequence-region SEQ2 1 2000\n"
                        + "##sequence-region SEQ3 1 3000\n");

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            String[] args = {
                "process",
                "replace-ids",
                "--accessions",
                "ACC_001,ACC-002,ACC.003",
                "-o",
                outputFile.toString(),
                inputFile.toString()
            };
            Main.main(args);
            mock.verify(() -> Main.exit(0));

            String output = Files.readString(outputFile);
            assertTrue(output.contains("ACC_001"));
            assertTrue(output.contains("ACC-002"));
            assertTrue(output.contains("ACC.003"));
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void testRealWorldFile() throws IOException {
        // Test with the real demo file if it exists
        Path demoFile = Path.of("src/test/resources/demo/OZ026791.gff3");
        if (!Files.exists(demoFile)) {
            return; // Skip if demo file doesn't exist
        }

        Path outputFile = Files.createTempFile("output", ".gff3");

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);

            // Count regions
            String[] countArgs = {"process", "count-regions", demoFile.toString()};
            Main.main(countArgs);
            String count = outContent.toString().trim();
            assertEquals("1", count, "OZ026791.gff3 should have 1 sequence region");

            outContent.reset();

            // Replace with real-looking accession
            String[] replaceArgs = {
                "process", "replace-ids", "--accessions", "LR999999", "-o", outputFile.toString(), demoFile.toString()
            };
            Main.main(replaceArgs);
            mock.verify(() -> Main.exit(0), times(2));

            String output = Files.readString(outputFile);
            assertTrue(output.contains("##sequence-region LR999999"));
            assertTrue(output.contains("LR999999\t"));
            // Note: FASTA headers and feature IDs may still contain OZ026791
            // but sequence-region directive and seqid column should be replaced
            assertFalse(output.contains("##sequence-region OZ026791"));
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }
}
