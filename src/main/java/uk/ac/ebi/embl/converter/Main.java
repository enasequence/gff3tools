package uk.ac.ebi.embl.converter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import io.vavr.Tuple2;
import uk.ac.ebi.embl.converter.gff3.GFF3Model;
import uk.ac.ebi.embl.converter.rules.FFToGFF3Model;
import uk.ac.ebi.embl.converter.rules.IConversionRule;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.api.entry.Entry;

public class Main {

//  public static void main(String[] args) throws Exception {
//    FFToGFF3Converter ffToGFF3Converter = new FFToGFF3Converter();
//    String filename = "src/test/resources/OverlappingGenes/in.embl";
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
//      Files.write(Paths.get("src/test/resources/OverlappingGenes/out.gff3"), gff3Writer.toString().getBytes());
//    }
//  }

  public static void main(String[] args) throws Exception {
    String filename = "src/test/resources/GeneID/in.embl";
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
      FFToGFF3Model fftogff3 = new FFToGFF3Model(entry.getPrimaryAccession());
      Tuple2<Optional<GFF3Model>, List<IConversionRule.ConversionError>> results =
              fftogff3.from(entry.getFeatures().listIterator());
      results._1.ifPresent((e) -> {
          try {
              e.writeGFF3String(gff3Writer);
          } catch (IOException ex) {
              throw new RuntimeException(ex);
          }
      });
      Files.write(Paths.get("src/test/resources/GeneID/test_out.gff3"), gff3Writer.toString().getBytes());
    }
  }
}
