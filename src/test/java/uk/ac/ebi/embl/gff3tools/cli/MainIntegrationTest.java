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

    // TSV to GFF3 conversion integration tests

    @Test
    void testTsvToGff3_successfulConversion() throws IOException {
        Path inputFile = Path.of("src/test/resources/tsvtogff3/its-two-entries.tsv");
        Path outputGff3 = Files.createTempFile("output", ".gff3");

        String[] args =
                new String[] {"conversion", "-f", "tsv", "-t", "gff3", inputFile.toString(), outputGff3.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
            String gff3Content = Files.readString(outputGff3);
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");
            assertTrue(gff3Content.contains("##species"), "GFF3 should have species directive");
            assertTrue(gff3Content.contains("Boletus sensibilis"), "GFF3 should contain organism name");
        } finally {
            Files.deleteIfExists(outputGff3);
        }
    }

    @Test
    void testTsvToGff3_withOutputSequence() throws IOException {
        Path inputFile = Path.of("src/test/resources/tsvtogff3/its-two-entries.tsv");
        Path outputGff3 = Files.createTempFile("output", ".gff3");
        Path outputFasta = Files.createTempFile("output", ".fasta");

        String[] args = new String[] {
            "conversion",
            "-f",
            "tsv",
            "-t",
            "gff3",
            "--output-sequence",
            outputFasta.toString(),
            inputFile.toString(),
            outputGff3.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
            String gff3Content = Files.readString(outputGff3);
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");

            assertTrue(Files.exists(outputFasta), "FASTA output file should exist");
            String fastaContent = Files.readString(outputFasta);
            assertFalse(fastaContent.isEmpty(), "FASTA output should contain nucleotide sequences");
            assertTrue(fastaContent.contains(">"), "FASTA should have sequence headers");
        } finally {
            Files.deleteIfExists(outputGff3);
            Files.deleteIfExists(outputFasta);
        }
    }

    @Test
    void testTsvToGff3_gzipInput() throws IOException {
        Path inputFile = Path.of("src/test/resources/tsvtogff3/its-two-entries.tsv.gz");
        Path outputGff3 = Files.createTempFile("output", ".gff3");

        String[] args =
                new String[] {"conversion", "-f", "tsv", "-t", "gff3", inputFile.toString(), outputGff3.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
            String gff3Content = Files.readString(outputGff3);
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");
            assertTrue(gff3Content.contains("Boletus"), "GFF3 should contain organism name");
        } finally {
            Files.deleteIfExists(outputGff3);
        }
    }

    @Test
    void testTsvToGff3_rrnaTemplate() throws IOException {
        Path inputFile = Path.of("src/test/resources/tsvtogff3/rrna-single-entry.tsv");
        Path outputGff3 = Files.createTempFile("output", ".gff3");

        String[] args =
                new String[] {"conversion", "-f", "tsv", "-t", "gff3", inputFile.toString(), outputGff3.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
            String gff3Content = Files.readString(outputGff3);
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");
            assertTrue(gff3Content.contains("Escherichia coli"), "GFF3 should contain organism name");
        } finally {
            Files.deleteIfExists(outputGff3);
        }
    }

    @Test
    void testTsvToGff3_missingTemplateId() throws IOException {
        Path inputFile = Path.of("src/test/resources/tsvtogff3/no-template-id.tsv");
        Path outputGff3 = Files.createTempFile("output", ".gff3");

        String[] args =
                new String[] {"conversion", "-f", "tsv", "-t", "gff3", inputFile.toString(), outputGff3.toString()};

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            // Should fail with a read error due to missing template ID
            mock.verify(() -> Main.exit(CLIExitCode.READ_ERROR.asInt()));
        } finally {
            Files.deleteIfExists(outputGff3);
            System.setErr(originalErr);
        }
    }

    @Test
    void testTsvToGff3_cdsTemplate_translationsInOutput() throws IOException {
        Path inputFile = Path.of("src/test/resources/tsvtogff3/cds-single-entry.tsv");
        Path outputGff3 = Files.createTempFile("output", ".gff3");

        String[] args =
                new String[] {"conversion", "-f", "tsv", "-t", "gff3", inputFile.toString(), outputGff3.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
            String gff3Content = Files.readString(outputGff3);
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");
            assertTrue(gff3Content.contains("##FASTA"), "GFF3 should contain ##FASTA section for translations");
            // ATGGCT...GCT(×32)...TGA translates to Met + Ala×32 (stop excluded)
            assertTrue(
                    gff3Content.contains("MAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                    "GFF3 should contain translated amino acid sequence");
        } finally {
            Files.deleteIfExists(outputGff3);
        }
    }

    @Test
    void testTsvToGff3_nonExistentFile() {
        String[] args = new String[] {"conversion", "-f", "tsv", "-t", "gff3", "non_existent.tsv", "output.gff3"};

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
    void testTsvToGff3_withoutOutputSequence_noFastaInOutput() throws IOException {
        Path inputFile = Path.of("src/test/resources/tsvtogff3/its-two-entries.tsv");
        Path outputGff3 = Files.createTempFile("output", ".gff3");

        // No --output-sequence option
        String[] args =
                new String[] {"conversion", "-f", "tsv", "-t", "gff3", inputFile.toString(), outputGff3.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);

            mock.verify(() -> Main.exit(0));

            String gff3Content = Files.readString(outputGff3);
            // Nucleotide sequences should NOT be in the GFF3 output (only translation sequences, if any)
            assertFalse(
                    gff3Content.toLowerCase().contains("atcagcatacacgcaacag"),
                    "GFF3 should NOT contain nucleotide sequences when --output-sequence is not provided");
        } finally {
            Files.deleteIfExists(outputGff3);
        }
    }
}
