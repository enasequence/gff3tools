package uk.ac.ebi.embl.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

import java.io.*;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FFToGFF3ConverterTest {

    @Test
    void testWriteGFF3() throws Exception {
        FFToGFF3Converter ffToGFF3Converter = new FFToGFF3Converter();
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        String filenamePrefix = "embl_BN000065/embl_BN000065";
        Entry entry;
        try (BufferedReader testFileReader = TestUtils.getResourceReader(filenamePrefix + ".embl")) {
            EmblEntryReader entryReader = new EmblEntryReader(
                    testFileReader, EmblEntryReader.Format.EMBL_FORMAT, filenamePrefix, readerOptions);
            entryReader.read();
            entry = entryReader.getEntry();
        }

        Writer gff3Writer = new StringWriter();
        ffToGFF3Converter.writeGFF3(entry, gff3Writer);

        String expected;
        try (BufferedReader testFileReader = TestUtils.getResourceReader(filenamePrefix + ".gff3")) {
            expected = new BufferedReader(testFileReader).lines().collect(Collectors.joining("\n"));
        }

        assertEquals(expected, gff3Writer.toString());
    }
}
