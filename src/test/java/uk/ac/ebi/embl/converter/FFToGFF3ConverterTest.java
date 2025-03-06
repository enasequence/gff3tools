package uk.ac.ebi.embl.converter;

import io.vavr.Tuple2;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.converter.gff3.GFF3Model;
import uk.ac.ebi.embl.converter.gff3.IGFF3Feature;
import uk.ac.ebi.embl.converter.rules.FFToGFF3Model;
import uk.ac.ebi.embl.converter.rules.IConversionRule;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FFToGFF3ConverterTest {

    @Test
    void testWriteGFF3() throws Exception {

        String[] toTest = new String[]{
                "HeadersAndSource",
                "GeneID"
        };
        for (String testName : toTest) {
            Entry entry;
            try (BufferedReader testFileReader = TestUtils.getResourceReader(testName + "/in.embl")) {
                ReaderOptions readerOptions = new ReaderOptions();
                readerOptions.setIgnoreSequence(true);
                EmblEntryReader entryReader = new EmblEntryReader(
                        testFileReader, EmblEntryReader.Format.EMBL_FORMAT, "in", readerOptions);
                entryReader.read();
                entry = entryReader.getEntry();
                entryReader.getEntry();
            }
            Writer gff3Writer = new StringWriter();
            FFToGFF3Model fftogff3 = new FFToGFF3Model(entry.getPrimaryAccession());

            Tuple2<Optional<GFF3Model>, List<IConversionRule.ConversionError>> gff3Model = fftogff3.from(entry.getFeatures().listIterator());
            assertTrue(gff3Model._1.isPresent());

            gff3Model._1.get().writeGFF3String(gff3Writer);

            String expected;
            try (BufferedReader testFileReader = TestUtils.getResourceReader(testName + "/out.gff3")) {
                expected = new BufferedReader(testFileReader).lines().collect(Collectors.joining("\n"));
            }

            assertEquals(new ArrayList<>(), gff3Model._2);
            assertEquals(expected.trim(), gff3Writer.toString().trim());
        }
    }
}
