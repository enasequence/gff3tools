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
package uk.ac.ebi.embl.gff3tools.gff3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.*;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

public class GFF3FileReaderTest {

    ValidationEngine getValidationEngine() {
        ValidationEngineBuilder builder = getValidationEngineBuilder();
        return builder.build();
    }

    /**
     * Returns a validation engine configured with fail-fast mode enabled.
     * Use this for tests that expect exceptions to be thrown immediately on error.
     */
    ValidationEngine getValidationEngineFailFast() {
        return getValidationEngineBuilder().failFast(true).build();
    }

    ValidationEngineBuilder getValidationEngineBuilder() {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();

        return builder;
    }

    @Test
    void canParseAllExamples() throws Exception {
        Map<String, Path> testFiles = TestUtils.getTestFiles("fftogff3_rules", ".gff3");

        for (String filePrefix : testFiles.keySet()) {
            File file = new File(testFiles.get(filePrefix).toUri());

            ValidationEngine validationEngine = getValidationEngine();

            try (FileReader filerReader = new FileReader(file);
                    BufferedReader reader = new BufferedReader(filerReader);
                    GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, file.toPath())) {
                gff3Reader.readHeader();
                while (true) {
                    if (gff3Reader.readAnnotation() == null) break;
                }
            } catch (Exception e) {
                fail(String.format("Error parsing file: %s", filePrefix), e);
            }
        }
    }

    @Test
    void testMissingHeader() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/empty_file.gff3");
        ValidationEngine validationEngine = getValidationEngineFailFast();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, testFile.toPath())) {
            gff3Reader.readHeader();
        } catch (InvalidGFF3HeaderException e) {
            Assertions.assertTrue(e.getMessage().contains("GFF3 header not found"));
            assertEquals(1, e.getLine());
            return;
        }
        fail(String.format("Expected exception when parsing file: %s", testFile.getPath()));
    }

    @Test
    void testAttributesFromAndToString() throws Exception {

        test("ID=ID_TEST;qualifier1=test_1;qualifier2=test_2;");
        test("ID=ID_TEST;qualifier1=test_1,test_2,test_3;");
        test("ID=ID_TEST;qualifier1=test_1,test_3;qualifier2=test_2;");
        test("ID=ID_TEST;qualifier1=test_1,test_3;");
        test("ID=ID_TEST;qualifier1=%00%09%25%3B%2C;");
    }

    @Test
    void testInvalidRecord() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/invalid_record.gff3");
        ValidationEngine validationEngine = getValidationEngineFailFast();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, testFile.toPath())) {
            gff3Reader.readHeader(); // Read header first
            while (true) {
                if (gff3Reader.readAnnotation() == null) {
                    fail(String.format("Expected exception when parsing file: %s", testFile.getPath()));
                }
            }
        } catch (InvalidGFF3RecordException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid gff3 record"));
            assertEquals(10, e.getLine()); // Line 3 is the invalid record
            return;
        }
    }

    @Test
    void testUndefinedSeqIdException() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/undefined_seq_id.gff3");
        ValidationEngine validationEngine = getValidationEngineFailFast();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, testFile.toPath())) {
            gff3Reader.readHeader();
            gff3Reader.readAnnotation();
        } catch (UndefinedSeqIdException e) {
            Assertions.assertTrue(e.getMessage().contains("GFF3_UNDEFINED_SEQID"));
            assertEquals(2, e.getLine());
            return;
        }
        fail(String.format("Expected exception when parsing file: %s", testFile.getPath()));
    }

    @Test
    void testUndefinedSeqIdNoExceptionWhenRuleOff() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/undefined_seq_id.gff3");
        Map<String, RuleSeverity> ruleSeverityMap = new HashMap<>();
        ruleSeverityMap.put("GFF3_UNDEFINED_SEQID", RuleSeverity.OFF);
        ValidationEngine validationEngine = getValidationEngineBuilder()
                .overrideMethodRules(ruleSeverityMap)
                .build();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, testFile.toPath())) {
            gff3Reader.readHeader();
            GFF3Annotation annotation = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation);
            assertEquals(2, annotation.getFeatures().size());
            annotation = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation);
            assertEquals(3, annotation.getFeatures().size());
            annotation = gff3Reader.readAnnotation();
            Assertions.assertNull(annotation);
        }
    }

    @Test
    void testInvalidRecordNoExceptionWhenRuleOff() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/invalid_record.gff3");
        Map<String, RuleSeverity> ruleSeverityMap = new HashMap<>();
        ruleSeverityMap.put("GFF3_INVALID_RECORD", RuleSeverity.OFF);
        ValidationEngine validationEngine = getValidationEngineBuilder()
                .overrideMethodRules(ruleSeverityMap)
                .build();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, testFile.toPath())) {
            gff3Reader.readHeader();
            GFF3Annotation annotation = null;
            while (true) {
                GFF3Annotation currentAnnotation = gff3Reader.readAnnotation();
                if (currentAnnotation == null) break;
                if (annotation == null) {
                    annotation = currentAnnotation;
                } else {
                    annotation.getFeatures().addAll(currentAnnotation.getFeatures());
                }
            }
            Assertions.assertNotNull(annotation);
            // The invalid record is skipped, so we expect 5 features instead of 6 if it
            // were valid
            assertEquals(5, annotation.getFeatures().size());
        }
    }

    @Test
    void testDirectiveResolution() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 200\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feat1\n"
                + "###\n"
                + "seq1\tsource\tgene\t100\t200\t.\t+\t.\tID=feat2\n";

        ValidationEngine validationEngine = getValidationEngine();
        Files.writeString(Path.of("input.gff3"), gff3Content, Charset.defaultCharset());

        try (GFF3FileReader gff3Reader =
                new GFF3FileReader(validationEngine, new StringReader(gff3Content), Path.of("input.gff3"))) {
            gff3Reader.readHeader();

            GFF3Annotation annotation1 = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation1);
            assertEquals(1, annotation1.getFeatures().size());
            assertEquals("seq1", annotation1.getFeatures().get(0).accession());
            assertEquals("feat1", annotation1.getFeatures().get(0).getId().get());

            GFF3Annotation annotation2 = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation2);
            assertEquals(1, annotation2.getFeatures().size());
            assertEquals("seq1", annotation2.getFeatures().get(0).accession());
            assertEquals("feat2", annotation2.getFeatures().get(0).getId().get());

            GFF3Annotation annotation3 = gff3Reader.readAnnotation();
            Assertions.assertNull(annotation3);
            Files.deleteIfExists(Path.of("input.gff3"));
        }
    }

    @Test
    void testSequenceRegionAfterFeatures() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 200\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feata1\n"
                + "##sequence-region seq2 1 200\n"
                + "seq2\tsource\tgene\t1\t100\t.\t+\t.\tID=featb1\n"
                + "seq1\tsource\tgene\t100\t200\t.\t+\t.\tID=feata2\n"
                + "seq2\tsource\tgene\t1\t100\t.\t+\t.\tID=featb2\n";

        ValidationEngine validationEngine = getValidationEngineFailFast();
        Files.writeString(Path.of("input.gff3"), gff3Content, Charset.defaultCharset());
        try (GFF3FileReader gff3Reader =
                new GFF3FileReader(validationEngine, new StringReader(gff3Content), Path.of("input.gff3"))) {
            gff3Reader.readHeader();
            gff3Reader.readAnnotation(); // Read first annotation
            gff3Reader.readAnnotation(); // This should trigger an exception
            fail("Expected DuplicateSeqIdException to be thrown.");
            Files.deleteIfExists(Path.of("input.gff3"));
        } catch (ValidationException e) {
            Assertions.assertTrue(
                    e.getMessage()
                            .contains(
                                    "Violation of rule GFF3_DUPLICATE_SEQID on line 6: The seq id \"seq1\" was used previously"));
            assertEquals(6, e.getLine()); // Line 5 is where the duplicate sequence-region is
        }
    }

    @Test
    void testReadSpecies_noSpecies() throws Exception {
        // GFF3 with species
        String input = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n"
                + "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        String output = testReadWithHeaderAndFastaOnEachAnnotation(input);
        assertEquals(input, output);

        // GFF3 with out species
        input = "##gff-version 3\n" + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        output = testReadWithHeaderAndFastaOnEachAnnotation(input);
        assertEquals(input, output);

        // GFF3 header on each annotation
        input = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n\n"
                + "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n\n";
        output = testReadWithHeaderAndFastaOnEachAnnotation(input);
        assertEquals(input, output);

        input = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n\n"
                + "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n\n";
        output = testReadWithHeaderOnce(input);
        String inputWithoutRepeatingVersionAndSequence = input.replaceAll("##gff-version 3\\n", "");
        inputWithoutRepeatingVersionAndSequence = inputWithoutRepeatingVersionAndSequence.replaceAll(
                "##species http://example.org\\?name=Homo sapiens\n", "");
        inputWithoutRepeatingVersionAndSequence = "##gff-version 3\n##species http://example.org?name=Homo sapiens\n"
                + inputWithoutRepeatingVersionAndSequence;
        assertEquals(inputWithoutRepeatingVersionAndSequence, output);

        File testFile = TestUtils.getResourceFile("reader/version-in-all-annotation.gff3");
        File expecttedFile = TestUtils.getResourceFile("reader/version-in-all-annotation-expected.gff3");
        String expectedOutput = Files.readString(expecttedFile.toPath());
        input = Files.readString(testFile.toPath());

        output = testReadWithHeaderOnce(input);
        assertEquals(expectedOutput, output);
    }

    @Test
    void testReadTranslation() throws Exception {
        String input = "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n"
                + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHD;gene=RHD;\n"
                + "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n"
                + "BN000066.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHX;gene=RHD;\n"
                + "##FASTA\n"
                + ">BN000065.1|CDS_RHX\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLE\n\n"
                + ">BN000066.1|CDS_RHD\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLEMSSKYPRSVRRCLPLWALTLE\n"
                + "AALILLFYFFTHYDASLE\n\n";

        String expected = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHD;gene=RHD;\n\n"
                + "##FASTA\n"
                + ">BN000065.1|CDS_RHX\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLE\n\n"
                + "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHX;gene=RHD;\n\n"
                + "##FASTA\n"
                + ">BN000066.1|CDS_RHD\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLEMSSKYPRSVRRCLPLWALTLE\n"
                + "AALILLFYFFTHYDASLE\n\n";

        String output = testReadWithHeaderAndFastaOnEachAnnotation(input);
        assertEquals(expected, output);
    }

    @Test
    void testReadTranslationAndWriteTranslationInEnd() throws Exception {
        String input = "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHD;gene=RHD;\n\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHX;gene=RHD;\n\n"
                + "##FASTA\n"
                + ">BN000065.1|CDS_RHX\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLE\n"
                + ">BN000066.1|CDS_RHD\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLEMSSKYPRSVRRCLPLWALTLE\n"
                + "AALILLFYFFTHYDASLE\n\n";

        String output = testReadWithHeaderAndFastaInEnd(input);
        assertEquals(input, output);
    }

    private String testReadWithHeaderOnce(String input)
            throws IOException, ValidationException, ReadException, WriteException {
        StringWriter writer = new StringWriter();
        Files.writeString(Path.of("input.gff3"), input, Charset.defaultCharset());
        try (GFF3FileReader reader =
                new GFF3FileReader(getValidationEngine(), new StringReader(input), Path.of("input.gff3"))) {
            GFF3Header gff3Header = reader.readHeader();

            AtomicBoolean first = new AtomicBoolean(true);

            reader.read(annotation -> {
                if (first.getAndSet(false)) {
                    // first annotation → write header + species only once
                    GFF3Species gff3Species = reader.getSpecies();
                    GFF3File gff3File = GFF3File.builder()
                            .header(gff3Header)
                            .species(gff3Species)
                            .annotations(Collections.singletonList(annotation))
                            .gff3Reader(reader)
                            .build();
                    gff3File.writeGFF3String(writer);
                } else {
                    // subsequent annotations → only write features
                    GFF3File gff3File = GFF3File.builder()
                            .annotations(Collections.singletonList(annotation))
                            .gff3Reader(reader)
                            .build();
                    gff3File.writeGFF3String(writer);
                }
            });
            Files.deleteIfExists(Path.of("input.gff3"));
        }
        return writer.toString();
    }

    private String testReadWithHeaderAndFastaOnEachAnnotation(String input)
            throws IOException, ValidationException, ReadException, WriteException {
        StringWriter writer = new StringWriter();
        Files.deleteIfExists(Path.of("input.gff3"));
        Files.writeString(Path.of("input.gff3"), input, Charset.defaultCharset());
        try (GFF3FileReader reader =
                new GFF3FileReader(getValidationEngine(), new StringReader(input), Path.of("input.gff3"))) {
            GFF3Header gff3Header = reader.readHeader();
            AtomicReference<GFF3File> gff3File = new AtomicReference<>();
            reader.read(annotation -> {
                GFF3Species gff3Species = reader.getSpecies();
                gff3File.set(GFF3File.builder()
                        .header(gff3Header)
                        .species(gff3Species)
                        .annotations(Collections.singletonList(annotation))
                        .gff3Reader(reader)
                        .writeAnnotationFasta(true)
                        .build());
                gff3File.get().writeGFF3String(writer);
            });
            Files.deleteIfExists(Path.of("input.gff3"));
        }
        return writer.toString();
    }

    private String testReadWithHeaderAndFastaInEnd(String input)
            throws IOException, ValidationException, ReadException, WriteException {
        StringWriter writer = new StringWriter();
        Files.deleteIfExists(Path.of("input.gff3"));
        Files.writeString(Path.of("input.gff3"), input, Charset.defaultCharset());
        try (GFF3FileReader reader =
                new GFF3FileReader(getValidationEngine(), new StringReader(input), Path.of("input.gff3"))) {
            GFF3Header gff3Header = reader.readHeader();
            List<GFF3Annotation> annotations = new ArrayList<>();
            AtomicReference<GFF3Species> gff3Species = new AtomicReference<>();

            reader.read(annotation -> {
                gff3Species.set(reader.getSpecies());
                annotations.add(annotation);
            });

            GFF3File gff3File1 = GFF3File.builder()
                    .header(gff3Header)
                    .species(gff3Species.get())
                    .annotations(annotations)
                    .gff3Reader(reader)
                    .build();

            gff3File1.writeGFF3String(writer);

            Files.deleteIfExists(Path.of("input.gff3"));
        }
        return writer.toString();
    }

    private void test(String attributeLine) throws Exception {
        ValidationEngine validationEngine = getValidationEngine();
        Files.writeString(Path.of("input.gff3"), attributeLine, Charset.defaultCharset());
        try (GFF3FileReader gff3Reader =
                new GFF3FileReader(validationEngine, new StringReader(attributeLine), Path.of("input.gff3"))) {
            Map<String, List<String>> attrMap = gff3Reader.attributesFromString(attributeLine);

            assertEquals(attributeLine, getAttributeString(attrMap));
            Files.deleteIfExists(Path.of("input.gff3"));
        }
    }

    private String getAttributeString(Map<String, List<String>> attributes) throws WriteException, IOException {
        try (StringWriter gff3Writer = new StringWriter()) {
            GFF3Annotation annotation = new GFF3Annotation();
            GFF3Feature gff3Feature = TestUtils.createGFF3Feature("ID", "Parent", attributes);
            annotation.addFeature(gff3Feature);
            annotation.writeGFF3String(gff3Writer);

            // retutn only attributes
            return gff3Writer.toString().split("\t")[8].trim();
        }
    }
}
