package uk.ac.ebi.embl.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.converter.gff3.GFF3Model;
import uk.ac.ebi.embl.converter.gff3.IGFF3Feature;
import uk.ac.ebi.embl.converter.rules.FFEntryToGFF3Headers;
import uk.ac.ebi.embl.converter.rules.FFEntryToGFF3Model;
import uk.ac.ebi.embl.converter.rules.FFEntryToGFF3SourceAttributes;
import uk.ac.ebi.embl.converter.rules.IConversionRule;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

import java.io.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FFToGFF3ConverterTest {

    @Test
    void testWriteGFF3() throws Exception {

        String filenamePrefix = "embl_BN000065/embl_BN000065";
        FFEntryToGFF3Headers.class.getConstructor().newInstance();
        Class<?>[] toTest = new Class[]{
                FFEntryToGFF3Headers.class,
                FFEntryToGFF3SourceAttributes.class,
                FFEntryToGFF3Model.class
        };
        for (Class<?> testcase : toTest) {
            Entry entry;
            String testName = testcase.getSimpleName();
            try (BufferedReader testFileReader = TestUtils.getResourceReader(testName + "/in.embl")) {
                ReaderOptions readerOptions = new ReaderOptions();
                readerOptions.setIgnoreSequence(true);
                EmblEntryReader entryReader = new EmblEntryReader(
                        testFileReader, EmblEntryReader.Format.EMBL_FORMAT, filenamePrefix, readerOptions);
                entryReader.read();
                entry = entryReader.getEntry();
            }
            Writer gff3Writer = new StringWriter();
            @SuppressWarnings("unchecked") IConversionRule<Entry, IGFF3Feature> rule = (IConversionRule<Entry, IGFF3Feature>) testcase.getConstructor().newInstance();
            IGFF3Feature gff3Model = rule.from(entry);
            gff3Model.writeGFF3String(gff3Writer);

            String expected;
            try (BufferedReader testFileReader = TestUtils.getResourceReader(testName + "/out.gff3")) {
                expected = new BufferedReader(testFileReader).lines().collect(Collectors.joining("\n"));
            }

            assertEquals(expected.trim(), gff3Writer.toString().trim());
        }
    }
}
