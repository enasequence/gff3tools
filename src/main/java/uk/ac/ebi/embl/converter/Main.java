/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.converter;

import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import uk.ac.ebi.embl.converter.fftogff3.FFGFF3FileFactory;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

public class Main {

  public static void main(String[] args) throws Exception {
    String filename = "src/test/resources/fftogff3_rules/parents_no_parent.embl";
    ReaderOptions readerOptions = new ReaderOptions();
    readerOptions.setIgnoreSequence(true);
    try (BufferedReader bufferedReader = Files.newBufferedReader(Path.of(filename))) {
      EmblEntryReader entryReader =
          new EmblEntryReader(
              bufferedReader, EmblEntryReader.Format.EMBL_FORMAT, filename, readerOptions);

      Writer gff3Writer = new StringWriter();
      FFGFF3FileFactory fftogff3 = new FFGFF3FileFactory();
      GFF3File file = fftogff3.from(entryReader);
      file.writeGFF3String(gff3Writer);
      Files.write(Paths.get("test_out.gff3"), gff3Writer.toString().getBytes());
    }
  }
}
