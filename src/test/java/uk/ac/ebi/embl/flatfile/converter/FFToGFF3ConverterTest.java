package uk.ac.ebi.embl.flatfile.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FFToGFF3ConverterTest {

    @Test
    void writeGFF3() throws Exception {
        FFToGFF3Converter ffToGFF3Converter = new FFToGFF3Converter();
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        String filenamePrefix = "embl_BN000065/embl_BN000065";
        Entry entry;
        try (InputStreamReader testFileReader = new InputStreamReader(
                Objects.requireNonNull(
                        FFToGFF3ConverterTest.class.getClassLoader().getResourceAsStream(filenamePrefix + ".embl")))) {
            EmblEntryReader entryReader = new EmblEntryReader(
                    new BufferedReader(testFileReader), EmblEntryReader.Format.EMBL_FORMAT, filenamePrefix, readerOptions);
            entryReader.read();
            entry = entryReader.getEntry();
        }

        Writer gff3Writer = new StringWriter();
        ffToGFF3Converter.writeGFF3(entry, gff3Writer);

        String expected;
        try (InputStreamReader testFileReader = new InputStreamReader(
                Objects.requireNonNull(
                        FFToGFF3ConverterTest.class.getClassLoader().getResourceAsStream(filenamePrefix + ".gff3")))) {
            expected = new BufferedReader(testFileReader).lines().collect(Collectors.joining("\n"));
        }

        assertEquals(expected, gff3Writer.toString());
    }
}
