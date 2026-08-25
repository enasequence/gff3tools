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

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.ebi.embl.fastareader.FastaReader;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceIndex;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
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
    void testReadTranslationOffset_ReturnsCorrectKeys() throws ReadException {
        Map<String, Long> map = reader.readTranslationOffset();

        Assertions.assertEquals(2, map.size());

        // Because TreeMap orders alphabetically by key
        List<String> keys = new ArrayList<>(map.keySet());
        Assertions.assertEquals("BN000065.1|CDS_RHX", keys.get(0));
        Assertions.assertEquals("BN000066.1|CDS_RHD", keys.get(1));

        Assertions.assertEquals("ATGCATGCATGATAT", reader.readTranslation(map.get("BN000065.1|CDS_RHX")));
        Assertions.assertEquals("TTTTGGGGAT", reader.readTranslation(map.get("BN000066.1|CDS_RHD")));
    }

    @Test
    void testReadingStopsAtFasta() throws ReadException {
        Map<String, Long> map = reader.readTranslationOffset();

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
        Map<String, Long> map = singleReader.readTranslationOffset();

        Assertions.assertEquals(1, map.size());
        Assertions.assertTrue(map.containsKey("ID1"));

        Files.delete(file);
    }

    @Test
    void testEmptyFileReturnsEmptyMap() throws Exception {
        Path empty = Files.createTempFile("empty", ".gff3");
        Files.write(empty, new byte[0]);

        GFF3TranslationReader emptyReader = new GFF3TranslationReader(null, empty);

        Map<String, Long> map = emptyReader.readTranslationOffset();
        Assertions.assertTrue(map.isEmpty());

        Files.delete(empty);
    }

    @Test
    void testNewlinesAreRemoved() throws ReadException {
        Map<String, Long> map = reader.readTranslationOffset();

        String seq = reader.readTranslation(map.get("BN000065.1|CDS_RHX"));

        Assertions.assertFalse(seq.contains("\n"));
        Assertions.assertEquals("ATGCATGCATGATAT", seq);
    }

    @Test
    void testInvalidSequenceTriggersValidationError() throws IOException {
        // Digit '1' is illegal for the protein alphabet, so FastaReader fails eagerly
        // during map construction rather than lazily per read.
        Files.writeString(tempFile, ">IDBAD\nATGC1234\n", StandardOpenOption.TRUNCATE_EXISTING);

        Assertions.assertThrows(ReadException.class, () -> reader.readTranslationOffset());
    }

    @Test
    void testInvalidSequenceExceptionNamesIllegalCharacterAndPosition() throws IOException {
        // '1' is byte offset 11 in this content, so the cause message must name both
        // the offending character and its absolute file position.
        Files.writeString(tempFile, ">IDBAD\nATGC1234\n", StandardOpenOption.TRUNCATE_EXISTING);

        ReadException exception = Assertions.assertThrows(ReadException.class, () -> reader.readTranslationOffset());

        Assertions.assertTrue(exception.getMessage().contains("Illegal character '1'"));
        Assertions.assertTrue(exception.getMessage().contains("position 11"));
    }

    @Test
    void testZeroBaseTranslationReturnsEmptyString() throws Exception {
        // FastaReader's normal load path cannot represent a zero-base entry (its
        // internal scan advances past each entry via its last base byte, which is
        // undefined for an entry with no bases), so the zero-base SequenceIndex is
        // injected directly via FastaReader's index-reload constructor.
        Path file = Files.createTempFile("zero_base", ".gff3");
        Files.write(file, "placeholder".getBytes(StandardCharsets.UTF_8));

        SequenceIndex zeroBaseIndex = new SequenceIndex();
        zeroBaseIndex.lines = new ArrayList<>();
        HashMap<Long, SequenceIndex> indexes = new HashMap<>();
        indexes.put(1L, zeroBaseIndex);

        FastaReader fastaReader =
                new FastaReader(file.toFile(), SequenceAlphabet.defaultProteinAlphabet(), indexes, null);

        GFF3TranslationReader zeroBaseReader = new GFF3TranslationReader(mockEngine, file);
        Field fastaReaderField = GFF3TranslationReader.class.getDeclaredField("fastaReader");
        fastaReaderField.setAccessible(true);
        fastaReaderField.set(zeroBaseReader, fastaReader);

        Assertions.assertEquals("", zeroBaseReader.readTranslation(1L));

        Files.delete(file);
    }

    @Test
    void testNoSequenceGff3() throws Exception {
        Path noSequence = Files.createTempFile("noSequence", ".gff3");
        Files.write(noSequence, "id\tsource\tattribute\nid\tsource\tattribute".getBytes(StandardCharsets.UTF_8));

        GFF3TranslationReader emptyReader = new GFF3TranslationReader(null, noSequence);

        Map<String, Long> map = emptyReader.readTranslationOffset();
        Assertions.assertTrue(map.isEmpty());

        Files.delete(noSequence);
    }

    @Test
    void testInvalidTranslationSequenceGff3() throws Exception {
        // The malformed annotation line above the header is classified as
        // annotation-side; the FastaReader opens at ">test_1", which contains only
        // legal characters, so this now reads successfully with no throw.
        Path noSequence = Files.createTempFile("noSequence", ".gff3");
        Files.write(
                noSequence,
                "id\tsource\tattribute\nid\tsource\n>test_1\nATGCATGCATAT".getBytes(StandardCharsets.UTF_8));

        GFF3TranslationReader emptyReader = new GFF3TranslationReader(null, noSequence);

        Map<String, Long> map = Assertions.assertDoesNotThrow(emptyReader::readTranslationOffset);

        Assertions.assertEquals(1, map.size());
        Assertions.assertEquals("ATGCATGCATAT", emptyReader.readTranslation(map.get("test_1")));

        Files.delete(noSequence);
    }
}
