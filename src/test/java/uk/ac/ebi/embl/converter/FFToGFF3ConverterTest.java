package uk.ac.ebi.embl.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.converter.gff3.IGFF3Feature;
import uk.ac.ebi.embl.converter.rules.*;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FFToGFF3ConverterTest {

    @Test
    void testWriteGFF3() throws Exception {

        String filenamePrefix = "embl_BN000065/embl_BN000065";
        FFEntryToGFF3Headers.class.getConstructor().newInstance();
        List<IConversionRule> rules = List.of(
                /*new FFEntryToGFF3Headers(),
                new FFEntryToGFF3SourceAttributes(),
                new FFEntryToGFF3Model(),*/
                new FFFeatureToGFF3Feature()
        );
        for ( IConversionRule rule : rules) {
            Entry entry;
            String testName = rule.getClass().getSimpleName();
            Map<String, Path> testFiles =  TestUtils.getTestFiles(testName);

            for(String filePrefix: testFiles.keySet()) {
                rule = rule.getClass().getDeclaredConstructor().newInstance();
                try (BufferedReader testFileReader = TestUtils.getResourceReader(testFiles.get(filePrefix).toString())) {
                    ReaderOptions readerOptions = new ReaderOptions();
                    readerOptions.setIgnoreSequence(true);
                    EmblEntryReader entryReader = new EmblEntryReader(
                            testFileReader, EmblEntryReader.Format.EMBL_FORMAT, filenamePrefix, readerOptions);
                    entryReader.read();
                    entry = entryReader.getEntry();
                }
                Writer gff3Writer = new StringWriter();
                IGFF3Feature gff3Model = (IGFF3Feature) rule.from(entry);
                gff3Model.writeGFF3String(gff3Writer);

                String expected;
                String expectedFilePath=testFiles.get(filePrefix).toString().replace(".embl",".gff3");
                try (BufferedReader testFileReader = TestUtils.getResourceReader(expectedFilePath)) {
                    expected = new BufferedReader(testFileReader).lines().collect(Collectors.joining("\n"));
                }

                assertEquals(expected.trim(), gff3Writer.toString().trim());
                gff3Writer.close();
            }
        }
    }
}
