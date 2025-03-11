package uk.ac.ebi.embl.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.converter.gff3.GFF3Model;
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

            Entry entry;
            Map<String, Path> testFiles =  TestUtils.getTestFiles("fftogff3_rules");

            for(String filePrefix: testFiles.keySet()) {
                FFEntryToGFF3Model rule = new FFEntryToGFF3Model();
                try (BufferedReader testFileReader = TestUtils.getResourceReader(testFiles.get(filePrefix).toString())) {
                    ReaderOptions readerOptions = new ReaderOptions();
                    readerOptions.setIgnoreSequence(true);
                    EmblEntryReader entryReader = new EmblEntryReader(
                            testFileReader, EmblEntryReader.Format.EMBL_FORMAT, "", readerOptions);
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

                assertEquals(expected.trim(), gff3Writer.toString().trim(), "Error on test case: " + filePrefix);
                gff3Writer.close();
            }

    }
}
