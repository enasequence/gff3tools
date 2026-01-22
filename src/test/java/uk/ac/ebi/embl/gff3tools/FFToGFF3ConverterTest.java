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
package uk.ac.ebi.embl.gff3tools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.fftogff3.*;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.validation.*;
import uk.ac.ebi.embl.gff3tools.validation.builtin.*;

class FFToGFF3ConverterTest {

    static Path fastaPath = Path.of("translation.fasta");

    @BeforeAll
    public static void setUp() throws Exception {
        Files.deleteIfExists(fastaPath);
    }

    @Test
    void testWriteGFF3() throws Exception {

        Map<String, Path> testFiles = TestUtils.getTestFiles("fftogff3_rules", ".embl");

        for (String filePrefix : testFiles.keySet()) {

            ValidationEngineBuilder builder = new ValidationEngineBuilder();

            try (BufferedReader testFileReader = TestUtils.getResourceReaderWithPath(
                    testFiles.get(filePrefix).toString())) {

                // We need new ValidationEngine each time as we cache data in our tests.
                GFF3FileFactory rule = new GFF3FileFactory(builder.build(), fastaPath);

                ReaderOptions readerOptions = new ReaderOptions();
                readerOptions.setIgnoreSequence(true);
                EmblEntryReader entryReader =
                        new EmblEntryReader(testFileReader, EmblEntryReader.Format.EMBL_FORMAT, "", readerOptions);
                Writer gff3Writer = new StringWriter();
                GFF3File gff3 = rule.from(entryReader, null);
                gff3.writeGFF3String(gff3Writer);

                String expected;
                String expectedFilePath = testFiles.get(filePrefix).toString().replace(".embl", ".gff3");
                try (BufferedReader gff3TestFileReader = TestUtils.getResourceReaderWithPath(expectedFilePath)) {
                    expected = new BufferedReader(gff3TestFileReader).lines().collect(Collectors.joining("\n"));
                }

                assertEquals(expected.trim(), gff3Writer.toString().trim(), "Error on test case: " + filePrefix);
                gff3Writer.close();
                Files.deleteIfExists(fastaPath);
            } catch (Exception e) {
                fail("Error on test case: " + filePrefix + " - " + e.getMessage());
            }
        }
    }

    @Test
    void testWriteGFF3UsingReducedFlatfile() throws IOException {

        Path scaffoldPath = TestUtils.getResourceFile("./fftogff3_rules/reduced/scaffold-reduced.embl")
                .toPath();
        // Test contig file is synthetic taken from CAXMMS010000001 and CAVNYM020000001
        Path contigPath = TestUtils.getResourceFile("./fftogff3_rules/reduced/contig-reduced.embl")
                .toPath();
        Path expectedScaffoldPath = TestUtils.getResourceFile("./fftogff3_rules/reduced/scaffold-reduced-expected.gff3")
                .toPath();
        Path expectedContigPath = TestUtils.getResourceFile("./fftogff3_rules/reduced/contig-reduced-expected.gff3")
                .toPath();
        Path masterPath = TestUtils.getResourceFile("./fftogff3_rules/reduced/master.embl")
                .toPath();

        testConvert(scaffoldPath, expectedScaffoldPath, masterPath);
        testConvert(contigPath, expectedContigPath, masterPath);
        Files.deleteIfExists(fastaPath);
    }

    private void testConvert(Path inputFile, Path expectedFile, Path masterFile) {
        ValidationEngineBuilder engineBuilder = new ValidationEngineBuilder();
        ValidationEngine engine = engineBuilder.build();
        FFToGff3Converter converter = new FFToGff3Converter(engine, masterFile);
        try (BufferedReader testFileReader = Files.newBufferedReader(inputFile);
                BufferedReader expectedFileReader = Files.newBufferedReader(expectedFile);
                StringWriter stringWriter = new StringWriter();
                BufferedWriter bufferedWriter = new BufferedWriter(stringWriter); ) {

            converter.convert(testFileReader, bufferedWriter);
            bufferedWriter.flush();

            String expected = expectedFileReader.lines().collect(Collectors.joining("\n"));
            assertEquals(expected.trim(), stringWriter.toString().trim(), "Error on test case: ");

        } catch (Exception e) {
            fail("Error on test case: " + inputFile + " - " + e.getMessage());
        }
    }

    @Test
    void testEmblToGff3_withOutputSequence_extractsNucleotideSequences() throws Exception {
        // Use contig-reduced.embl which has sequence data
        Path inputFile = TestUtils.getResourceFile("./fftogff3_rules/reduced/contig-reduced.embl")
                .toPath();
        Path outputFasta = Files.createTempFile("nucleotide-output", ".fasta");

        ValidationEngine engine = new ValidationEngineBuilder().build();
        // Pass fastaOutputPath to enable sequence extraction
        FFToGff3Converter converter = new FFToGff3Converter(engine, null, outputFasta);

        try (BufferedReader inputReader = Files.newBufferedReader(inputFile);
                StringWriter gff3Writer = new StringWriter();
                BufferedWriter bufferedWriter = new BufferedWriter(gff3Writer)) {

            converter.convert(inputReader, bufferedWriter);
            bufferedWriter.flush();

            // Verify GFF3 output was created
            String gff3Content = gff3Writer.toString();
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");

            // Verify FASTA output contains nucleotide sequence
            String fastaContent = Files.readString(outputFasta);
            assertFalse(fastaContent.isEmpty(), "FASTA output should not be empty");
            assertTrue(fastaContent.contains(">"), "FASTA should have header line");
            // The sequence from contig-reduced.embl contains "tgcctaagcc"
            assertTrue(
                    fastaContent.toLowerCase().contains("tgcctaagcc"),
                    "FASTA should contain the nucleotide sequence from the input file");

        } finally {
            Files.deleteIfExists(outputFasta);
        }
    }

    @Test
    void testEmblToGff3_withoutOutputSequence_discardSequences() throws Exception {
        // Use contig-reduced.embl which has sequence data
        Path inputFile = TestUtils.getResourceFile("./fftogff3_rules/reduced/contig-reduced.embl")
                .toPath();

        ValidationEngine engine = new ValidationEngineBuilder().build();
        // No fastaOutputPath - sequences should be discarded
        FFToGff3Converter converter = new FFToGff3Converter(engine, null, null);

        try (BufferedReader inputReader = Files.newBufferedReader(inputFile);
                StringWriter gff3Writer = new StringWriter();
                BufferedWriter bufferedWriter = new BufferedWriter(gff3Writer)) {

            converter.convert(inputReader, bufferedWriter);
            bufferedWriter.flush();

            // Verify GFF3 output was created
            String gff3Content = gff3Writer.toString();
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");

            // Verify nucleotide sequence is NOT in GFF3 output
            assertFalse(
                    gff3Content.toLowerCase().contains("tgcctaagcc"),
                    "GFF3 should NOT contain nucleotide sequences when fastaOutputPath is null");
        }
    }

    @Test
    void testEmblToGff3_inputWithoutSequence_fastaOutputEmpty() throws Exception {
        // Use partial_location_end.embl which has no sequence data
        Path inputFile = TestUtils.getResourceFile("./fftogff3_rules/partial_location_end.embl")
                .toPath();
        Path outputFasta = Files.createTempFile("nucleotide-output", ".fasta");

        ValidationEngine engine = new ValidationEngineBuilder().build();
        FFToGff3Converter converter = new FFToGff3Converter(engine, null, outputFasta);

        try (BufferedReader inputReader = Files.newBufferedReader(inputFile);
                StringWriter gff3Writer = new StringWriter();
                BufferedWriter bufferedWriter = new BufferedWriter(gff3Writer)) {

            converter.convert(inputReader, bufferedWriter);
            bufferedWriter.flush();

            // Verify GFF3 output was created
            String gff3Content = gff3Writer.toString();
            assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");

            // Verify FASTA output is empty (input file has no sequences)
            String fastaContent = Files.readString(outputFasta);
            assertTrue(fastaContent.isEmpty(), "FASTA output should be empty when input has no sequences");

        } finally {
            Files.deleteIfExists(outputFasta);
        }
    }
}
