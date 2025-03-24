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

import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.gff3.GFF3Header;
import uk.ac.ebi.embl.converter.gff3.GFF3Sequence;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

public class FFGFF3FileFactory implements IConversionRule<EmblEntryReader, GFF3File> {
  @Override
  public GFF3File from(EmblEntryReader input) throws ConversionError {
    GFF3Header header = new GFF3Header("3.1.26");
    FFGFF3SequenceFactory seqFactory = new FFGFF3SequenceFactory();
    List<GFF3Sequence> sequences = new ArrayList<>();
    try {
      while (input.read() != null && input.isEntry()) {
        sequences.add(seqFactory.from(input.getEntry()));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new GFF3File(header, sequences);
  }
}
