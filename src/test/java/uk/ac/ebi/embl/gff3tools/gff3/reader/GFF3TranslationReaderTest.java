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
package uk.ac.ebi.embl.gff3tools.gff3.reader;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class GFF3TranslationReaderTest {
    private Path tempFile;
    private GFF3TranslationReader reader;

    @Mock
    private ValidationEngine mockEngine;

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        String content = String.join(
                "\n",
                "##gff-version 3",
                "chr1\t.\tgene\t1\t1000\t.\t+\t.\tID=gene1",
                "##FASTA",
                ">BN000065.1|CDS_RHX",
                "ATGCATGC\nATG",
                "ATAT",
                ">BN000066.1|CDS_RHD",
                "TTTTGGGG\nA\nT",
                "");

        tempFile = Files.createTempFile("test_gff3", ".gff3");
        Files.write(tempFile, content.getBytes());

        reader = new GFF3TranslationReader(mockEngine, tempFile);
    }

    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testReadTranslationOffset_ReturnsCorrectKeys() {
        Map<String, OffsetRange> map = reader.readTranslationOffset();

        Assertions.assertEquals(2, map.size());

        // Because TreeMap orders alphabetically by key
        List<String> keys = new ArrayList<>(map.keySet());
        Assertions.assertEquals("BN000065.1|CDS_RHX", keys.get(0));
        Assertions.assertEquals("BN000066.1|CDS_RHD", keys.get(1));

        Assertions.assertEquals("ATGCATGCATGATAT", reader.readTranslation(map.get("BN000065.1|CDS_RHX")));
        Assertions.assertEquals("TTTTGGGGAT", reader.readTranslation(map.get("BN000066.1|CDS_RHD")));
    }

    @Test
    void testOffsetsPointInsideFile() throws IOException {
        Map<String, OffsetRange> map = reader.readTranslationOffset();

        long fileSize = Files.size(tempFile);

        for (OffsetRange r : map.values()) {
            Assertions.assertTrue(r.start >= 0, "Start offset should be >= 0");
            Assertions.assertTrue(r.end <= fileSize, "End offset should be within file");
            Assertions.assertTrue(r.start < r.end, "Start should be < end");
        }
    }

    @Test
    void testReadingStopsAtFasta() {
        Map<String, OffsetRange> map = reader.readTranslationOffset();

        // Must NOT include markers or GFF content
        Assertions.assertFalse(map.containsKey("##FASTA"));
        Assertions.assertFalse(map.containsKey("##gff-version 3"));
    }

    @Test
    void testReadingHandlesSingleEntry() throws Exception {
        String content = String.join("\n", "##FASTA", ">ID1", "AAAAA", "");

        Path file = Files.createTempFile("single", ".gff3");
        Files.write(file, content.getBytes());

        GFF3TranslationReader singleReader = new GFF3TranslationReader(mockEngine, file);
        Map<String, OffsetRange> map = singleReader.readTranslationOffset();

        Assertions.assertEquals(1, map.size());
        Assertions.assertTrue(map.containsKey("ID1"));

        Files.delete(file);
    }

    @Test
    void testEmptyFileReturnsEmptyMap() throws Exception {
        Path empty = Files.createTempFile("empty", ".gff3");
        Files.write(empty, new byte[0]);

        GFF3TranslationReader emptyReader = new GFF3TranslationReader(null, empty);

        Map<String, OffsetRange> map = emptyReader.readTranslationOffset();
        Assertions.assertTrue(map.isEmpty());

        Files.delete(empty);
    }

    @Test
    void testReadTranslationExtractsCorrectSequence() throws IOException {

        String file = Files.readString(tempFile);
        int start = file.indexOf("TTTT");
        int end = start + "TTTTGGGG\n".length() - 1;

        OffsetRange r = new OffsetRange(start, end);

        String seq = reader.readTranslation(r);
        Assertions.assertEquals("TTTTGGGG", seq);
    }

    @Test
    void testNewlinesAreRemoved() throws IOException {
        String file = Files.readString(tempFile);
        int start = file.indexOf("ATGC");
        int end = start + "ATGCATGC\nATG\nATAT".length() - 1;

        OffsetRange r = new OffsetRange(start, end);
        String seq = reader.readTranslation(r);

        Assertions.assertFalse(seq.contains("\n"));
        Assertions.assertEquals("ATGCATGCATGATAT", seq);
    }

    @Test
    void testInvalidSequenceTriggersValidationError() throws IOException, ValidationException {
        // Inject an invalid character into the file
        Files.writeString(tempFile, ">IDBAD\nATGC1234\n", StandardOpenOption.TRUNCATE_EXISTING);

        // Locate "ATGC1234"
        String file = Files.readString(tempFile);
        int start = file.indexOf("ATGC1234");
        int end = start + "ATGC1234".length() - 1;

        OffsetRange r = new OffsetRange(start, end);

        reader.readTranslation(r);

        verify(mockEngine, times(1)).handleSyntacticError(any());
    }

    @Test
    void testEmptyRangeReturnsEmptyString() {
        OffsetRange r = new OffsetRange(10, 9); // end < start

        String seq = reader.readTranslation(r);
        Assertions.assertEquals("", seq);
    }

    @Test
    void testNoSequenceGff3() throws IOException, ValidationException {
        // Inject an invalid character into the file
        Files.writeString(tempFile, "id\tsource\t", StandardOpenOption.TRUNCATE_EXISTING);

        Path noSequence = Files.createTempFile("noSequence", ".gff3");
        Files.write(
                noSequence,
                "id\tsource\tattribute\nid\tsource\tattribute".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING);

        GFF3TranslationReader emptyReader = new GFF3TranslationReader(null, noSequence);

        Map<String, OffsetRange> map = emptyReader.readTranslationOffset();
        Assertions.assertTrue(map.isEmpty());

        Files.delete(noSequence);
    }

    @Test
    void testInvalidTranslationSequenceGff3() throws IOException, ValidationException {
        // Inject an invalid character into the file
        Files.writeString(tempFile, "id\tsource\t", StandardOpenOption.TRUNCATE_EXISTING);

        Path noSequence = Files.createTempFile("noSequence", ".gff3");
        Files.write(
                noSequence,
                "id\tsource\tattribute\nid\tsource\n>test_1\nATGCATGCATAT".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING);

        GFF3TranslationReader emptyReader = new GFF3TranslationReader(null, noSequence);

        RuntimeException ex =
                Assertions.assertThrows(RuntimeException.class, () -> emptyReader.readTranslationOffset());

        Assertions.assertTrue(ex.getMessage().contains("Invalid GFF3 translation sequence:"));

        Files.delete(noSequence);
    }
}
