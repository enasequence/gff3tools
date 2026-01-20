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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

public class MainIntegrationTest {

    @BeforeEach
    void setup() {}

    @AfterEach
    void tearDown() {}

    @Test
    void testCLIExceptionExitCode() {
        String[] args = new String[] {"conversion", "-f", "invalid_format", "-t", "gff3", "input.gff3"};
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testReadExceptionExitCode() {
        String[] args = new String[] {"conversion", "-f", "embl", "-t", "gff3", "non_existent_input.embl"}; //
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.NON_EXISTENT_FILE.asInt()));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testValidationExceptionExitCode() throws IOException {
        // Create a temporary file with an invalid GFF3 header to trigger
        // ValidationException
        Path tempFile = Files.createTempFile("invalid_gff3", ".gff3");
        Files.writeString(tempFile, "This is an invalid file\n");

        String[] args = new String[] {"conversion", "-f", "gff3", "-t", "embl", tempFile.toString()};
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.VALIDATION_ERROR.asInt()));
            assertTrue(errContent.toString().contains("GFF3_INVALID_HEADER"));
        } finally {
            Files.deleteIfExists(tempFile);
            System.setErr(originalErr);
        }
    }

    @Test
    void testEmblToGff3_withOutputSequence_writesNucleotideSequencesToFasta() throws IOException {
        // Use the test file that has sequence data
        Path inputFile = Path.of("src/test/resources/fftogff3_rules/reduced/contig-reduced.embl");
        Path outputGff3 = Files.createTempFile("output", ".gff3");
        Path outputFasta = Files.createTempFile("output", ".fasta");

        String[] args = new String[] {
            "conversion",
            "-f",
            "embl",
            "-t",
            "gff3",
            "--output-sequence",
            outputFasta.toString(),
            inputFile.toString(),
            outputGff3.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            // Verify GFF3 output was created
            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
            String gff3Content = Files.readString(outputGff3);
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have header");

            // Verify FASTA output contains nucleotide sequence
            assertTrue(Files.exists(outputFasta), "FASTA output file should exist");
            String fastaContent = Files.readString(outputFasta);
            assertFalse(fastaContent.isEmpty(), "FASTA output should not be empty");
            assertTrue(fastaContent.contains(">"), "FASTA should have header line");
            // The sequence from the test file contains "tgcctaagcc"
            assertTrue(
                    fastaContent.toLowerCase().contains("tgcctaagcc"),
                    "FASTA should contain the nucleotide sequence from the input file");
        } finally {
            Files.deleteIfExists(outputGff3);
            Files.deleteIfExists(outputFasta);
            System.setErr(originalErr);
        }
    }

    @Test
    void testEmblToGff3_withoutOutputSequence_discardSequences() throws IOException {
        // Use the test file that has sequence data
        Path inputFile = Path.of("src/test/resources/fftogff3_rules/reduced/contig-reduced.embl");
        Path outputGff3 = Files.createTempFile("output", ".gff3");

        // No --output-sequence option provided
        String[] args =
                new String[] {"conversion", "-f", "embl", "-t", "gff3", inputFile.toString(), outputGff3.toString()};

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            // Verify GFF3 output was created
            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
            String gff3Content = Files.readString(outputGff3);
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have header");

            // Verify no FASTA section in GFF3 output (nucleotide sequences should be discarded)
            // GFF3 may have ##FASTA for translation sequences, but not nucleotide sequences
            // The nucleotide sequence "tgcctaagcc" should NOT appear in the GFF3 output
            assertFalse(
                    gff3Content.toLowerCase().contains("tgcctaagcc"),
                    "GFF3 should NOT contain nucleotide sequences when --output-sequence is not provided");
        } finally {
            Files.deleteIfExists(outputGff3);
            System.setErr(originalErr);
        }
    }

    @Test
    void testEmblToGff3_withOutputSequence_shortForm() throws IOException {
        // Test with -os short form
        Path inputFile = Path.of("src/test/resources/fftogff3_rules/reduced/contig-reduced.embl");
        Path outputGff3 = Files.createTempFile("output", ".gff3");
        Path outputFasta = Files.createTempFile("output", ".fasta");

        String[] args = new String[] {
            "conversion",
            "-f",
            "embl",
            "-t",
            "gff3",
            "-os",
            outputFasta.toString(),
            inputFile.toString(),
            outputGff3.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            // Verify FASTA output contains nucleotide sequence
            assertTrue(Files.exists(outputFasta), "FASTA output file should exist");
            String fastaContent = Files.readString(outputFasta);
            assertFalse(fastaContent.isEmpty(), "FASTA output should not be empty with -os option");
            assertTrue(
                    fastaContent.toLowerCase().contains("tgcctaagcc"),
                    "FASTA should contain the nucleotide sequence from the input file");
        } finally {
            Files.deleteIfExists(outputGff3);
            Files.deleteIfExists(outputFasta);
            System.setErr(originalErr);
        }
    }

    @Test
    void testEmblToGff3_withoutSequenceInInput_fastaOutputEmpty() throws IOException {
        // Use a test file without sequence data
        Path inputFile = Path.of("src/test/resources/fftogff3_rules/partial_location_end.embl");
        Path outputGff3 = Files.createTempFile("output", ".gff3");
        Path outputFasta = Files.createTempFile("output", ".fasta");

        String[] args = new String[] {
            "conversion",
            "-f",
            "embl",
            "-t",
            "gff3",
            "--output-sequence",
            outputFasta.toString(),
            inputFile.toString(),
            outputGff3.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            // Verify GFF3 output was created
            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");

            // Verify FASTA output is empty (no sequences in input file)
            assertTrue(Files.exists(outputFasta), "FASTA output file should exist");
            String fastaContent = Files.readString(outputFasta);
            assertTrue(
                    fastaContent.isEmpty(), "FASTA output should be empty when input file has no nucleotide sequences");
        } finally {
            Files.deleteIfExists(outputGff3);
            Files.deleteIfExists(outputFasta);
            System.setErr(originalErr);
        }
    }
}
