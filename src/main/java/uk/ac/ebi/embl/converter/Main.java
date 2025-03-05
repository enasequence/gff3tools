package uk.ac.ebi.embl.converter;

import java.io.BufferedReader;
import java.io.Console;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import uk.ac.ebi.embl.converter.rules.FFEntryToGFF3Model;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.api.entry.Entry;

public class Main {

  public static void main(String[] args) throws Exception {
    System.out.println(FFEntryToGFF3Model.class.getSimpleName());
//    FFToGFF3Converter ffToGFF3Converter = new FFToGFF3Converter();
//    String filename = "src/test/resources/embl_BN000065/embl_BN000065.embl";
//    ReaderOptions readerOptions = new ReaderOptions();
//    readerOptions.setIgnoreSequence(true);
//    try (BufferedReader bufferedReader = Files.newBufferedReader(Path.of(filename))) {
//      EmblEntryReader entryReader = new EmblEntryReader(bufferedReader, EmblEntryReader.Format.EMBL_FORMAT, filename,
//          readerOptions);
//      ValidationResult validationResult = entryReader.read();
//      Entry entry = entryReader.getEntry();
//      // TODO: Is this entry correct?
//      // 1. Generate the same file back
//      Writer gff3Writer = new StringWriter();
//      ffToGFF3Converter.writeGFF3(entry, gff3Writer);
//      Files.write(Paths.get("embl_BN000065.embl"), gff3Writer.toString().getBytes());
//    }
  }
}
