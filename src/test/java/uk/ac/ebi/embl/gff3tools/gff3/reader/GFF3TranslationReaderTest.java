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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
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

    @Test
    void testInvalidCharacterInFirstRecordIsReportedNotDropped() throws IOException {
        // The byte layout matters: with three annotation rows the boundary search probes
        // the "MKV1L" line directly. That line must not be mistaken for annotation, or the
        // boundary lands on ">B" and record A is silently dropped instead of reported.
        String row = "chr1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        String content = "##gff-version 3\n" + row.repeat(3) + "##FASTA\n>A\nMKV1L\n>B\nMKVL\n>C\nMKVL\n";
        Files.writeString(tempFile, content, StandardOpenOption.TRUNCATE_EXISTING);

        ReadException exception = Assertions.assertThrows(ReadException.class, () -> reader.readTranslationOffset());

        Assertions.assertTrue(exception.getMessage().contains("Illegal character '1'"));
        Assertions.assertTrue(exception.getMessage().contains("position 120"));
    }

    @Test
    void testCommentLineInFastaSectionIsReportedNotDropped() throws IOException {
        // With this byte layout the boundary search probes the "# comment" line directly.
        // It must not be mistaken for annotation, or the boundary lands on ">B" and
        // record A is silently dropped instead of the '#' line being reported.
        String row = "chr1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        String content = "##gff-version 3\n" + row.repeat(3) + "##FASTA\n>A\nMKVL\n# comment\n>B\nMKVL\n>C\nMKVL\n";
        Files.writeString(tempFile, content, StandardOpenOption.TRUNCATE_EXISTING);

        ReadException exception = Assertions.assertThrows(ReadException.class, () -> reader.readTranslationOffset());

        Assertions.assertTrue(exception.getMessage().contains("Illegal character '#'"));
    }

    @Test
    void testGzippedFileIsSkippedWithWarning() throws IOException, ReadException {
        // Offsets found by seeking in compressed bytes do not point into the decompressed text,
        // so compressed input is not read at all: no translations, no failure, one warning.
        writeGzipped(tempFile, Files.readAllBytes(tempFile));

        ListAppender<ILoggingEvent> logs = captureLogs();
        try {
            Assertions.assertTrue(reader.readTranslationOffset().isEmpty());
        } finally {
            stopCapturingLogs(logs);
        }

        Assertions.assertTrue(
                logs.list.stream()
                        .anyMatch(e -> e.getLevel() == Level.WARN
                                && e.getFormattedMessage().contains("The input file is compressed")),
                "Expected a warning that compressed input is not read");
    }

    @Test
    void testBgzippedFileIsSkipped() throws IOException, ReadException {
        // FastaReader can open BGZF itself, but the boundary would still be searched for in the
        // compressed bytes. With a file this size that offset lands inside the FASTA section and
        // the first records are silently dropped, so BGZF is skipped like plain gzip.
        StringBuilder content = new StringBuilder("##gff-version 3\n");
        for (int i = 0; i < 50; i++) {
            content.append("seq1\t.\tCDS\t1\t90\t.\t+\t0\tID=cds").append(i).append('\n');
        }
        content.append("##FASTA\n");
        Random random = new Random(1);
        String aminoAcids = "ACDEFGHIKLMNPQRSTVWY";
        for (int i = 0; i < 500; i++) {
            content.append(">seq1|cds").append(i).append('\n');
            for (int line = 0; line < 5; line++) {
                for (int j = 0; j < 60; j++) {
                    content.append(aminoAcids.charAt(random.nextInt(aminoAcids.length())));
                }
                content.append('\n');
            }
        }
        writeBgzipped(tempFile, content.toString().getBytes(StandardCharsets.US_ASCII));

        Assertions.assertTrue(reader.readTranslationOffset().isEmpty());
    }

    @Test
    void testDuplicateHeaderIsReportedNotOverwritten() throws IOException {
        // Keeping either copy would silently pick a translation for the submitter.
        Files.writeString(
                tempFile,
                "##FASTA\n>SEQ1|cds1\nMFIRST\n>SEQ2|cds2\nMKVL\n>SEQ1|cds1\nMSECND\n",
                StandardOpenOption.TRUNCATE_EXISTING);

        ReadException exception = Assertions.assertThrows(ReadException.class, () -> reader.readTranslationOffset());

        Assertions.assertTrue(exception.getMessage().contains("\">SEQ1|cds1\" appears more than once"));
    }

    @Test
    void testTabInFastaSectionIsReportedNotDropped() throws IOException {
        // With this byte layout the boundary search probes the "MKVL\t" line directly. A tab
        // alone must not mark it as annotation, or the boundary lands on ">B" and record A is
        // silently dropped instead of the tab being reported.
        String row = "chr1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        String content = "##gff-version 3\n" + row.repeat(3) + "##FASTA\n>A\nMKVL\t\n>B\nMKVL\n>C\nMKVL\n";
        Files.writeString(tempFile, content, StandardOpenOption.TRUNCATE_EXISTING);

        ReadException exception = Assertions.assertThrows(ReadException.class, () -> reader.readTranslationOffset());

        Assertions.assertTrue(exception.getMessage().contains("byte value: 9"), exception.getMessage());
    }

    private static void writeGzipped(Path file, byte[] content) throws IOException {
        try (OutputStream gz = new GZIPOutputStream(Files.newOutputStream(file))) {
            gz.write(content);
        }
    }

    /** Writes {@code content} as BGZF, the block format bgzip produces, ending with its EOF block. */
    private static void writeBgzipped(Path file, byte[] content) throws IOException {
        int blockSize = 0xff00; // bgzip's own uncompressed block size
        try (OutputStream out = Files.newOutputStream(file)) {
            for (int start = 0; start < content.length; start += blockSize) {
                int length = Math.min(blockSize, content.length - start);
                out.write(bgzfBlock(content, start, length));
            }
            out.write(HexFormat.of().parseHex("1f8b08040000000000ff0600424302001b0003000000000000000000"));
        }
    }

    private static byte[] bgzfBlock(byte[] content, int start, int length) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(content, start, length);
        deflater.finish();
        byte[] compressed = new byte[length + 1024];
        int compressedLength = deflater.deflate(compressed);
        deflater.end();

        CRC32 crc = new CRC32();
        crc.update(content, start, length);

        // gzip header with the BGZF "BC" extra field, whose value is the total block size minus one
        ByteBuffer block = ByteBuffer.allocate(18 + compressedLength + 8).order(ByteOrder.LITTLE_ENDIAN);
        block.put(new byte[] {31, (byte) 139, 8, 4, 0, 0, 0, 0, 0, (byte) 255, 6, 0, 'B', 'C', 2, 0});
        block.putShort((short) (block.capacity() - 1));
        block.put(compressed, 0, compressedLength);
        block.putInt((int) crc.getValue());
        block.putInt(length);
        return block.array();
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(GFF3TranslationReader.class)).addAppender(appender);
        return appender;
    }

    private static void stopCapturingLogs(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(GFF3TranslationReader.class)).detachAppender(appender);
        appender.stop();
    }
}
