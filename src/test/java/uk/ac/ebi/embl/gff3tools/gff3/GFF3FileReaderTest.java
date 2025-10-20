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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
                    GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader)) {
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
        ValidationEngine validationEngine = getValidationEngine();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader)) {
            gff3Reader.readHeader();
        } catch (InvalidGFF3HeaderException e) {
            Assertions.assertTrue(e.getMessage().contains("GFF3 header not found"));
            Assertions.assertEquals(1, e.getLine());
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
        ValidationEngine validationEngine = getValidationEngine();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader)) {
            gff3Reader.readHeader(); // Read header first
            while (true) {
                if (gff3Reader.readAnnotation() == null) {
                    fail(String.format("Expected exception when parsing file: %s", testFile.getPath()));
                }
            }
        } catch (InvalidGFF3RecordException e) {
            Assertions.assertTrue(e.getMessage().contains("Invalid gff3 record"));
            Assertions.assertEquals(10, e.getLine()); // Line 3 is the invalid record
            return;
        }
    }

    @Test
    void testUndefinedSeqIdException() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/undefined_seq_id.gff3");
        ValidationEngine validationEngine = getValidationEngine();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader)) {
            gff3Reader.readHeader();
            gff3Reader.readAnnotation();
        } catch (UndefinedSeqIdException e) {
            Assertions.assertTrue(e.getMessage().contains("GFF3_UNDEFINED_SEQID"));
            Assertions.assertEquals(2, e.getLine());
            return;
        }
        fail(String.format("Expected exception when parsing file: %s", testFile.getPath()));
    }

    @Test
    void testUndefinedSeqIdNoExceptionWhenRuleOff() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/undefined_seq_id.gff3");
        Map<String, RuleSeverity> ruleSeverityMap = new HashMap<>();
        ruleSeverityMap.put("GFF3_UNDEFINED_SEQID", RuleSeverity.OFF);
        ValidationEngineBuilder builder = getValidationEngineBuilder();
        builder.overrideMethodRules(ruleSeverityMap);
        ValidationEngine validationEngine = builder.build();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader)) {
            gff3Reader.readHeader();
            GFF3Annotation annotation = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation);
            Assertions.assertEquals(2, annotation.getFeatures().size());
            annotation = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation);
            Assertions.assertEquals(3, annotation.getFeatures().size());
            annotation = gff3Reader.readAnnotation();
            Assertions.assertNull(annotation);
        }
    }

    @Test
    void testInvalidRecordNoExceptionWhenRuleOff() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/invalid_record.gff3");
        Map<String, RuleSeverity> ruleSeverityMap = new HashMap<>();
        ruleSeverityMap.put("GFF3_INVALID_RECORD", RuleSeverity.OFF);
        ValidationEngineBuilder builder = getValidationEngineBuilder();
        builder.overrideMethodRules(ruleSeverityMap);
        ValidationEngine validationEngine = builder.build();

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader)) {
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
            Assertions.assertEquals(5, annotation.getFeatures().size());
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

        try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, new StringReader(gff3Content))) {
            gff3Reader.readHeader();

            GFF3Annotation annotation1 = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation1);
            Assertions.assertEquals(1, annotation1.getFeatures().size());
            Assertions.assertEquals("seq1", annotation1.getFeatures().get(0).accession());
            Assertions.assertEquals(
                    "feat1", annotation1.getFeatures().get(0).getId().get());

            GFF3Annotation annotation2 = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation2);
            Assertions.assertEquals(1, annotation2.getFeatures().size());
            Assertions.assertEquals("seq1", annotation2.getFeatures().get(0).accession());
            Assertions.assertEquals(
                    "feat2", annotation2.getFeatures().get(0).getId().get());

            GFF3Annotation annotation3 = gff3Reader.readAnnotation();
            Assertions.assertNull(annotation3);
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

        ValidationEngine validationEngine = getValidationEngine();

        try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, new StringReader(gff3Content))) {
            gff3Reader.readHeader();
            gff3Reader.readAnnotation(); // Read first annotation
            gff3Reader.readAnnotation(); // This should trigger an exception
            fail("Expected DuplicateSeqIdException to be thrown.");
        } catch (ValidationException e) {
            Assertions.assertTrue(e.getMessage().contains("Violation of rule GFF3_DUPLICATE_SEQID on line 6: The seq id \"seq1\" was used previously"));
            Assertions.assertEquals(6, e.getLine()); // Line 5 is where the duplicate sequence-region is
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
        String output = testReadWithHeaderOnEachAnnotation(input);
        assertEquals(input, output);

        // GFF3 with out species
        input = "##gff-version 3\n" + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        output = testReadWithHeaderOnEachAnnotation(input);
        assertEquals(input, output);

        // GFF3 header on wach annotation
        input = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n\n"
                + "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n\n";
        output = testReadWithHeaderOnEachAnnotation(input);
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

    private String testReadWithHeaderOnce(String input)
            throws IOException, ValidationException, ReadException, WriteException {
        StringWriter writer = new StringWriter();
        try (GFF3FileReader reader = new GFF3FileReader(getValidationEngine(), new StringReader(input))) {
            GFF3Header gff3Header = reader.readHeader();

            AtomicBoolean first = new AtomicBoolean(true);

            reader.read(annotation -> {
                if (first.getAndSet(false)) {
                    // first annotation → write header + species only once
                    GFF3Species gff3Species = reader.getSpecies();
                    GFF3File gff3File =
                            new GFF3File(gff3Header, gff3Species, Collections.singletonList(annotation), null);
                    gff3File.writeGFF3String(writer);
                } else {
                    // subsequent annotations → only write features
                    GFF3File gff3File = new GFF3File(null, null, Collections.singletonList(annotation), null);
                    gff3File.writeGFF3String(writer);
                }
            });
        }
        return writer.toString();
    }

    private String testReadWithHeaderOnEachAnnotation(String input)
            throws IOException, ValidationException, ReadException, WriteException {
        StringWriter writer = new StringWriter();
        try (GFF3FileReader reader = new GFF3FileReader(getValidationEngine(), new StringReader(input))) {
            GFF3Header gff3Header = reader.readHeader();

            reader.read(annotation -> {
                GFF3Species gff3Species = reader.getSpecies();
                GFF3File gff3File = new GFF3File(gff3Header, gff3Species, Collections.singletonList(annotation), null);
                gff3File.writeGFF3String(writer);
            });
        }
        return writer.toString();
    }

    private void test(String attributeLine) throws Exception {
        ValidationEngine validationEngine = getValidationEngine();

        try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, new StringReader(attributeLine))) {
            Map<String, Object> attrMap = gff3Reader.attributesFromString(attributeLine);

            assertEquals(attributeLine, getAttributeString(attrMap));
        }
    }

    private String getAttributeString(Map<String, Object> attributes) throws WriteException, IOException {
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
