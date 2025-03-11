package uk.ac.ebi.embl.converter;

import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.fftogff3.FFGFF3FileFactory;
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
      FFGFF3FileFactory fftogff3 = new FFGFF3FileFactory();
      GFF3File model = fftogff3.from(entry);
      model.writeGFF3String(gff3Writer);
      Files.write(Paths.get("test_out.gff3"), gff3Writer.toString().getBytes());
    }
  }
}
