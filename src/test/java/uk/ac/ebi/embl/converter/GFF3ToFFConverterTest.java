package uk.ac.ebi.embl.converter;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.gff3.GFF3RecordSet;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3.reader.GFF3FlatFileEntryReader;

import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GFF3ToFFConverterTest {

//  @Test
//  void testConvertRecordSetToEntry() throws Exception {
//    GFF3ToFFConverter gff3ToFFConverter = new GFF3ToFFConverter();
//    String filenamePrefix = "source";
//    Writer ffWriter = new StringWriter();
//    try (BufferedReader testFileReader = TestUtils.getResourceReader(filenamePrefix + ".gff3")) {
//      GFF3FlatFileEntryReader entryReader = new GFF3FlatFileEntryReader(new BufferedReader(testFileReader));
//      ValidationResult _validationResult = entryReader.read();
//      GFF3RecordSet recordSet = entryReader.getEntry();
//      Entry entry = gff3ToFFConverter.convertRecordSetToEntry(recordSet);
//      new EmblEntryWriter(entry).write(ffWriter);
//      ffWriter.close();
//    }
//
//    String expected;
//    try (BufferedReader testFileReader = TestUtils.getResourceReader(filenamePrefix + ".embl")) {
//      expected = new BufferedReader(testFileReader).lines().collect(Collectors.joining("\n"));
//    }
//
//    assertEquals(expected, ffWriter.toString());
//
//  }

}
