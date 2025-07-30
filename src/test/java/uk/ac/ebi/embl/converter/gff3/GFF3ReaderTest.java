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
package uk.ac.ebi.embl.converter.gff3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.TestUtils;
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.converter.validation.*;

public class GFF3ReaderTest {
    @Test
    void canParseAllExamples() throws Exception {
        Map<String, Path> testFiles = TestUtils.getTestFiles("fftogff3_rules", ".gff3");

        for (String filePrefix : testFiles.keySet()) {
            File file = new File(testFiles.get(filePrefix).toUri());

            try (FileReader filerReader = new FileReader(file);
                    BufferedReader reader = new BufferedReader(filerReader);
                    GFF3FileReader gff3Reader = new GFF3FileReader(reader)) {
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

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(reader)) {
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
        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(reader)) {
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

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(reader)) {
            gff3Reader.readHeader();
            gff3Reader.readAnnotation();
        } catch (UndefinedSeqIdException e) {
            Assertions.assertTrue(e.getMessage().contains(ValidationRule.GFF3_UNDEFINED_SEQID.getDescription()));
            Assertions.assertEquals(2, e.getLine());
            return;
        }
        fail(String.format("Expected exception when parsing file: %s", testFile.getPath()));
    }

    @Test
    void testUndefinedSeqIdNoExceptionWhenRuleOff() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/undefined_seq_id.gff3");
        Map<ValidationRule, RuleSeverity> ruleSeverityMap = new HashMap<>();
        ruleSeverityMap.put(ValidationRule.GFF3_UNDEFINED_SEQID, RuleSeverity.OFF);
        RuleSeverityState.INSTANCE.putAll(ruleSeverityMap);

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(reader)) {
            gff3Reader.readHeader();
            GFF3Annotation annotation = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation);
            Assertions.assertEquals(2, annotation.getFeatures().size());
            annotation = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation);
            Assertions.assertEquals(3, annotation.getFeatures().size());
            annotation = gff3Reader.readAnnotation();
            Assertions.assertNull(annotation);
        } finally {
            // Reset the rule severity to ERROR for other tests
            ruleSeverityMap.put(ValidationRule.GFF3_UNDEFINED_SEQID, RuleSeverity.ERROR);
            RuleSeverityState.INSTANCE.putAll(ruleSeverityMap);
        }
    }

    @Test
    void testInvalidRecordNoExceptionWhenRuleOff() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/invalid_record.gff3");
        Map<ValidationRule, RuleSeverity> ruleSeverityMap = new HashMap<>();
        ruleSeverityMap.put(ValidationRule.GFF3_INVALID_RECORD, RuleSeverity.OFF);
        RuleSeverityState.INSTANCE.putAll(ruleSeverityMap);

        try (FileReader filerReader = new FileReader(testFile);
                BufferedReader reader = new BufferedReader(filerReader);
                GFF3FileReader gff3Reader = new GFF3FileReader(reader)) {
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
        } finally {
            // Reset the rule severity to ERROR for other tests
            ruleSeverityMap.put(ValidationRule.GFF3_INVALID_RECORD, RuleSeverity.ERROR);
            RuleSeverityState.INSTANCE.putAll(ruleSeverityMap);
        }
    }

    @Test
    void testDirectiveResolution() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 200\n"
                + "seq1\tsource\tfeature1\t1\t100\t.\t+\t.\tID=feat1\n"
                + "###\n"
                + "seq1\tsource\tfeature2\t100\t200\t.\t+\t.\tID=feat2\n";

        try (GFF3FileReader gff3Reader = new GFF3FileReader(new StringReader(gff3Content))) {
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
                + "seq1\tsource\tfeature1\t1\t100\t.\t+\t.\tID=feata1\n"
                + "##sequence-region seq2 1 200\n"
                + "seq2\tsource\tfeature1\t1\t100\t.\t+\t.\tID=featb1\n"
                + "seq1\tsource\tfeature2\t100\t200\t.\t+\t.\tID=feata2\n"
                + "seq2\tsource\tfeature2\t1\t100\t.\t+\t.\tID=featb2\n";

        try (GFF3FileReader gff3Reader = new GFF3FileReader(new StringReader(gff3Content))) {
            gff3Reader.readHeader();
            gff3Reader.readAnnotation(); // Read first annotation
            gff3Reader.readAnnotation(); // Read second annotation
            gff3Reader.readAnnotation(); // This should trigger the exception
            fail("Expected UngroupedFeaturesException to be thrown.");
        } catch (UngroupedFeaturesException e) {
            Assertions.assertTrue(e.getMessage().contains("The seq id \"seq1\" was used previously"));
            Assertions.assertEquals(6, e.getLine()); // Line 5 is where the duplicate sequence-region is
        }
    }

    private void test(String attributeLine) throws Exception {
        try (GFF3FileReader gff3Reader = new GFF3FileReader(new StringReader(attributeLine))) {
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
