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
package uk.ac.ebi.embl.converter.fftogff3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.converter.cli.Params;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

public class FFToGff3Converter {

  private static final Logger LOG = LoggerFactory.getLogger(FFToGff3Converter.class);

  public void convert(Params params) throws IOException {
    try (BufferedReader bufferedReader = Files.newBufferedReader(params.inFile.toPath());
         StringWriter gff3Writer = new StringWriter()) {
      EmblEntryReader entryReader =
          new EmblEntryReader(
              bufferedReader,
              EmblEntryReader.Format.EMBL_FORMAT,
              params.inFile.getAbsolutePath(),
              getReaderOptions());

      GFF3FileFactory fftogff3 = new GFF3FileFactory();
      GFF3File file = fftogff3.from(entryReader);
      file.writeGFF3String(gff3Writer);
      Files.write(params.outFile.toPath(), gff3Writer.toString().getBytes());
      LOG.info("Gff3 file is written in: {}", params.outFile.toPath());
    }
  }

  private ReaderOptions getReaderOptions() {
    ReaderOptions readerOptions = new ReaderOptions();
    readerOptions.setIgnoreSequence(true);
    return readerOptions;
  }
}
