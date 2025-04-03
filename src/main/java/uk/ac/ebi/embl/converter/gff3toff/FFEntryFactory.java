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

import java.io.IOException;
import java.util.List;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.gff3.GFF3RecordSet;
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.ff.FFEntryMapper;
import uk.ac.ebi.embl.gff3.reader.GFF3FlatFileEntryReader;

public class FFEntryFactory implements IConversionRule<GFF3FlatFileEntryReader, List<Entry>> {
  @Override
  public List<Entry> from(GFF3FlatFileEntryReader entryReader) throws ConversionError {
    try {
      while (entryReader.read() != null && entryReader.isEntry()) {
        GFF3RecordSet recordSet = entryReader.getEntry();
        List<Entry> emblEntryList = FFEntryMapper.mapGFF3RecordToEntry(recordSet);
        return emblEntryList;
      }
      return null;
    } catch (IOException e) {
      throw new ConversionError();
    }
  }
}
