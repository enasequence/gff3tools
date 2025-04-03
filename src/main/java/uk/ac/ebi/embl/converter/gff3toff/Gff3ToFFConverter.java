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
package uk.ac.ebi.embl.converter.gff3toff;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.converter.cli.Params;
import uk.ac.ebi.embl.converter.ff.EmblFlatFile;
import uk.ac.ebi.embl.gff3.reader.GFF3FlatFileEntryReader;

public class Gff3ToFFConverter {

  private static final Logger LOG = LoggerFactory.getLogger(Gff3ToFFConverter.class);

  public void convert(Params params) throws IOException {
    Path filePath = params.inFile.toPath();
    try (BufferedReader bufferedReader = Files.newBufferedReader(filePath);
        StringWriter ffWriter = new StringWriter()) {
      GFF3FlatFileEntryReader entryReader = new GFF3FlatFileEntryReader(bufferedReader);

      FFEntryFactory ffEntryFactory = new FFEntryFactory();
      List<Entry> entries = ffEntryFactory.from(entryReader);
      EmblFlatFile emblFlatFile = new EmblFlatFile(entries);
      emblFlatFile.writeFFString(ffWriter);
      Files.write(params.outFile.toPath(), ffWriter.toString().getBytes());
      LOG.info("Embl flat file is written in: {}", params.outFile.toPath());
    }
  }
}
