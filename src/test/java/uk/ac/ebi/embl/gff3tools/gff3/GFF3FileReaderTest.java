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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3FileFactory;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
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
    void readAnnotationSetsEachFeaturesSourceLine() throws Exception {
        String gff3Content = "##gff-version 3\n"
                + "##sequence-region seq1 1 200\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feat1\n"
                + "seq1\tsource\tCDS\t50\t80\t.\t+\t.\tID=cds1\n";

        ValidationEngine validationEngine = getValidationEngine();
        try (GFF3FileReader gff3Reader =
                new GFF3FileReader(validationEngine, new StringReader(gff3Content), Path.of("input.gff3"))) {
            gff3Reader.readHeader();
            GFF3Annotation annotation = gff3Reader.readAnnotation();

            Assertions.assertNotNull(annotation);
            // Line 1 is "##gff-version 3", line 2 is "##sequence-region seq1 1 200", so the gene
            // feature is on line 3 and the CDS feature is on line 4.
            assertEquals(3, annotation.getFeatures().get(0).getLine());
            assertEquals(4, annotation.getFeatures().get(1).getLine());
        }
    }

    @Test
    void readAnnotation_featurelessRegionBeforeFasta_isReturned() throws Exception {
        String gff3Content = "##gff-version 3\n"
                + "##sequence-region ACC2.1 1 40\n"
                + "##sequence-region ACC1.1 1 24\n"
                + "ACC1.1\tENA\tgene\t1\t12\t.\t+\t.\tID=gene1\n"
                + "##FASTA\n"
                + ">ACC1.1|CDS_1\n"
                + "MKP\n";

        List<String> annotations = new ArrayList<>();
        try (GFF3FileReader gff3Reader = new GFF3FileReader(
                getValidationEngineFailFast(), new StringReader(gff3Content), Path.of("input.gff3"))) {
            gff3Reader.readHeader();
            GFF3Annotation annotation;
            while ((annotation = gff3Reader.readAnnotation()) != null) {
                annotations.add(annotation.getAccession() + ":"
                        + annotation.getFeatures().size());
            }
            // the reader stays at the end once everything has been returned
            Assertions.assertNull(gff3Reader.readAnnotation());
        }

        // before the fix the first translation header ended the file and ACC2.1 was never returned
        assertEquals(List.of("ACC1.1:1", "ACC2.1:0"), annotations);
    }

    @Test
    void read_featurelessRegionsBeforeFasta_comeAfterAnnotatedOnes() throws Exception {
        String gff3Content = "##gff-version 3\n"
                + "##sequence-region ACC4.1 1 40\n"
                + "##sequence-region ACC1.1 1 24\n"
                + "##sequence-region ACC2.1 1 40\n"
                + "##sequence-region ACC3.1 1 24\n"
                + "ACC1.1\tENA\tgene\t1\t12\t.\t+\t.\tID=gene1\n"
                + "###\n"
                + "ACC3.1\tENA\tgene\t1\t12\t.\t+\t.\tID=gene3\n"
                + "###\n"
                + "##FASTA\n"
                + ">ACC1.1|CDS_1\n"
                + "MKP\n"
                + ">ACC3.1|CDS_3\n"
                + "MKP\n";

        // read() is what the GFF3 to flatfile conversion uses
        List<String> annotations = new ArrayList<>();
        try (GFF3FileReader gff3Reader = new GFF3FileReader(
                getValidationEngineFailFast(), new StringReader(gff3Content), Path.of("input.gff3"))) {
            gff3Reader.readHeader();
            gff3Reader.read(annotation -> annotations.add(
                    annotation.getAccession() + ":" + annotation.getFeatures().size()));
        }

        // annotated ones in file order, then the featureless ones sorted by accession
        assertEquals(List.of("ACC1.1:1", "ACC3.1:1", "ACC2.1:0", "ACC4.1:0"), annotations);
    }

    @Test
    void readAnnotation_featurelessRegionBeforeFasta_translationsStillReadable(@TempDir Path tempDir) throws Exception {
        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(
                gff3,
                "##gff-version 3\n"
                        + "##sequence-region ACC2.1 1 40\n"
                        + "##sequence-region ACC1.1 1 24\n"
                        // a gene, not a CDS: the CDS rules would reject this short sequence, and
                        // translations are read by file offset whatever the features are
                        + "ACC1.1\tENA\tgene\t1\t12\t.\t+\t.\tID=gene1\n"
                        + "##FASTA\n"
                        + ">ACC1.1|CDS_1\n"
                        + "MKP\n");

        try (GFF3FileReader gff3Reader = new GFF3FileReader(getValidationEngineFailFast(), gff3)) {
            gff3Reader.readHeader();
            while (gff3Reader.readAnnotation() != null) {
                // read every annotation, including the featureless ACC2.1
            }

            Map<String, OffsetRange> translations = gff3Reader.getTranslationOffsetMap();
            assertEquals(Set.of("ACC1.1|CDS_1"), translations.keySet());
            assertEquals("MKP", gff3Reader.getTranslation(translations.get("ACC1.1|CDS_1")));
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

    /** Reads every annotation {@link GFF3FileReader#readAnnotation()} returns, without merging. */
    private List<GFF3Annotation> readAllAnnotations(String gff3Content) throws Exception {
        List<GFF3Annotation> annotations = new ArrayList<>();
        Files.writeString(Path.of("input.gff3"), gff3Content, Charset.defaultCharset());
        try (GFF3FileReader gff3Reader =
                new GFF3FileReader(getValidationEngine(), new StringReader(gff3Content), Path.of("input.gff3"))) {
            gff3Reader.readHeader();
            GFF3Annotation annotation;
            while ((annotation = gff3Reader.readAnnotation()) != null) {
                annotations.add(annotation);
            }
        } finally {
            Files.deleteIfExists(Path.of("input.gff3"));
        }
        return annotations;
    }

    @Test
    void testResolutionDirectiveAfterLastFeatureDoesNotRepeatAccessionAsEmptyAnnotation() throws Exception {
        String gff3Content = "##gff-version 3\n"
                + "##sequence-region ID1 1 12\n"
                + "##sequence-region ID2 1 8\n"
                + "ID1\tENA\tCDS\t1\t12\t.\t+\t0\tID=cds1\n"
                + "###\n"
                + "ID2\tENA\tCDS\t1\t8\t.\t+\t0\tID=cds2\n"
                + "###\n";

        List<GFF3Annotation> annotations = readAllAnnotations(gff3Content);

        assertEquals(
                List.of("ID1", "ID2"),
                annotations.stream().map(GFF3Annotation::getAccession).toList());
        assertTrue(annotations.stream().allMatch(GFF3Annotation::hasFeatures));
    }

    @Test
    void testResolutionDirectiveEndingSingleAccessionDoesNotRepeatIt() throws Exception {
        String gff3Content = "##gff-version 3\n"
                + "##sequence-region seq1 1 200\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=gene1\n"
                + "###\n"
                + "###\n";

        List<GFF3Annotation> annotations = readAllAnnotations(gff3Content);

        assertEquals(1, annotations.size());
        assertEquals("seq1", annotations.get(0).getAccession());
        assertEquals(1, annotations.get(0).getFeatures().size());
    }

    @Test
    void testResolutionDirectiveStillEmitsSequenceRegionWithoutFeatures() throws Exception {
        // Only accessions that were actually returned are recorded: a region no feature references
        // is still emitted once, as an empty annotation.
        String gff3Content = "##gff-version 3\n"
                + "##sequence-region seq1 1 200\n"
                + "##sequence-region seq2 1 200\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=gene1\n"
                + "###\n";

        List<GFF3Annotation> annotations = readAllAnnotations(gff3Content);

        assertEquals(
                List.of("seq1", "seq2"),
                annotations.stream().map(GFF3Annotation::getAccession).toList());
        assertTrue(annotations.get(0).hasFeatures());
        assertFalse(annotations.get(1).hasFeatures());
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
        String withSpecies1 = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        String withSpecies2 = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        assertEquals(
                List.of(withSpecies1, withSpecies2), readAndWriteOneDocumentPerAnnotation(withSpecies1 + withSpecies2));

        // GFF3 with out species
        String withoutSpecies = "##gff-version 3\n" + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n\n";
        assertEquals(List.of(withoutSpecies), readAndWriteOneDocumentPerAnnotation(withoutSpecies));

        // GFF3 header on each annotation
        String noFeatures1 = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n\n";
        String noFeatures2 = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n\n";
        assertEquals(
                List.of(noFeatures1, noFeatures2), readAndWriteOneDocumentPerAnnotation(noFeatures1 + noFeatures2));

        String input = noFeatures1 + noFeatures2;
        String output = testReadWithHeaderOnce(input);
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

        String expectedDocument1 = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHD;gene=RHD;\n\n"
                + "##FASTA\n"
                + ">BN000065.1|CDS_RHX\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLE\n\n";

        String expectedDocument2 = "##gff-version 3\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHX;gene=RHD;\n\n"
                + "##FASTA\n"
                + ">BN000066.1|CDS_RHD\n"
                + "MSSKYPRSVRRCLPLWALTLEAALILLFYFFTHYDASLEMSSKYPRSVRRCLPLWALTLE\n"
                + "AALILLFYFFTHYDASLE\n\n";

        assertEquals(List.of(expectedDocument1, expectedDocument2), readAndWriteOneDocumentPerAnnotation(input));
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

    @Test
    void testReadGff3() throws Exception {
        String input = "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n"
                + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHD;gene=RHD;\n"
                + "##gff-version 3\n"
                + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_RHD;gene=RHD;\n"
                + "BN000066.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHX;gene=RHD;\n";

        String expected = "##gff-version 3.1.26\n" + "##species http://example.org?name=Homo sapiens\n"
                + "##sequence-region BN000065.1 1 315242\n"
                + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHD;gene=RHD;\n"
                + "\n"
                + "##sequence-region BN000066.1 1 315242\n"
                + "BN000066.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_RHX;gene=RHD;\n"
                + "\n";

        String output = testReadWithGff3FactoryOnce(input);
        assertEquals(expected, output);
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

    private String testReadWithGff3FactoryOnce(String input)
            throws IOException, ValidationException, ReadException, WriteException {
        StringWriter writer = new StringWriter();
        Files.writeString(Path.of("input.gff3"), input, Charset.defaultCharset());
        try (GFF3FileReader reader =
                new GFF3FileReader(getValidationEngine(), new StringReader(input), Path.of("input.gff3"))) {
            GFF3Header gff3Header = reader.readHeader();

            List<GFF3Annotation> annotations = new ArrayList<>();
            reader.read(annotation -> {
                // first annotation → write header + species only once
                annotations.add(annotation);
            });

            GFF3File gff3File = GFF3FileFactory.fromAnnotationAndReader(annotations, reader, false, Optional.empty());
            gff3File.writeGFF3String(writer);
            Files.deleteIfExists(Path.of("input.gff3"));
        }
        return writer.toString();
    }

    /**
     * Writes each annotation as a document of its own and returns those documents, in the order the
     * reader produced them.
     *
     * <p>Each document gets its own writer: joining them into one output would not be valid
     * GFF3, however well formed each is on its own. {@link #testReadWithHeaderAndFastaInEnd}
     * covers the other shape, one document holding every annotation.
     */
    private List<String> readAndWriteOneDocumentPerAnnotation(String input)
            throws IOException, ValidationException, ReadException, WriteException {
        List<String> documents = new ArrayList<>();
        Files.deleteIfExists(Path.of("input.gff3"));
        Files.writeString(Path.of("input.gff3"), input, Charset.defaultCharset());
        try (GFF3FileReader reader =
                new GFF3FileReader(getValidationEngine(), new StringReader(input), Path.of("input.gff3"))) {
            GFF3Header gff3Header = reader.readHeader();
            reader.read(annotation -> {
                StringWriter writer = new StringWriter();
                GFF3File.builder()
                        .header(gff3Header)
                        .species(reader.getSpecies())
                        .annotations(Collections.singletonList(annotation))
                        .gff3Reader(reader)
                        .writeAnnotationFasta(true)
                        .build()
                        .writeGFF3String(writer);
                documents.add(writer.toString());
            });
            Files.deleteIfExists(Path.of("input.gff3"));
        }

        for (String document : documents) {
            assertIsSingleAnnotationDocument(document);
        }
        return documents;
    }

    /** Asserts that {@code document} is a valid GFF3 document holding exactly one annotation. */
    private void assertIsSingleAnnotationDocument(String document)
            throws IOException, ValidationException, ReadException, WriteException {
        int fastaCount = document.split("##FASTA", -1).length - 1;
        Assertions.assertTrue(fastaCount <= 1, "a GFF3 document must not contain two FASTA sections:\n" + document);

        int marker = document.indexOf("##FASTA");
        if (marker >= 0) {
            Assertions.assertTrue(
                    !document.substring(marker).contains("\t"),
                    "no feature line may follow the FASTA section:\n" + document);
        }

        Path roundTrip = Path.of("roundtrip.gff3");
        Files.writeString(roundTrip, document, Charset.defaultCharset());
        List<GFF3Annotation> readBack = new ArrayList<>();
        try (GFF3FileReader reader = new GFF3FileReader(getValidationEngine(), new StringReader(document), roundTrip)) {
            reader.readHeader();
            reader.read(readBack::add);
        } finally {
            Files.deleteIfExists(roundTrip);
        }
        assertEquals(1, readBack.size(), "a single-annotation document must read back as one annotation");
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
                    .writeAnnotationFasta(true)
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

    @Test
    void testPathConstructorReadsAnnotation() throws Exception {
        Path testFile = TestUtils.getResourceFile("fftogff3_rules/reduced/contig-reduced-expected.gff3")
                .toPath();
        ValidationEngine validationEngine = getValidationEngine();

        try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, testFile)) {
            GFF3Header header = gff3Reader.readHeader();
            Assertions.assertNotNull(header);

            GFF3Annotation annotation = gff3Reader.readAnnotation();
            Assertions.assertNotNull(annotation);
            Assertions.assertFalse(annotation.getFeatures().isEmpty());
            gff3Reader.getTranslationOffsetMap().forEach((key, value) -> {
                Assertions.assertNotNull(key);
                Assertions.assertNotNull(value);
            });
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
