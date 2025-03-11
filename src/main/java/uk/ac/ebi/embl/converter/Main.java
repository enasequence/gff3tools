package uk.ac.ebi.embl.converter;

import java.io.BufferedReader;
import java.io.Console;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import uk.ac.ebi.embl.converter.gff3.GFF3Model;
import uk.ac.ebi.embl.converter.rules.FFEntryToGFF3Model;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.api.entry.Entry;

public class Main {

  public static void main(String[] args) throws Exception {
    String filename = "src/test/resources/FFFeaturesToGFF3Features/gene_mrna_parents.embl";
    ReaderOptions readerOptions = new ReaderOptions();
    readerOptions.setIgnoreSequence(true);
    try (BufferedReader bufferedReader = Files.newBufferedReader(Path.of(filename))) {
      EmblEntryReader entryReader = new EmblEntryReader(bufferedReader, EmblEntryReader.Format.EMBL_FORMAT, filename,
              readerOptions);
      ValidationResult validationResult = entryReader.read();
      Entry entry = entryReader.getEntry();
      // TODO: Is this entry correct?
      // 1. Generate the same file back
      Writer gff3Writer = new StringWriter();
      FFEntryToGFF3Model fftogff3 = new FFEntryToGFF3Model();
      GFF3Model model = fftogff3.from(entry);
      model.writeGFF3String(gff3Writer);
      Files.write(Paths.get("test_out.gff3"), gff3Writer.toString().getBytes());
    }
  }
}
