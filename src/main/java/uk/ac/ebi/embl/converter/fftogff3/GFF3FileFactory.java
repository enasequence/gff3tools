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
import uk.ac.ebi.embl.converter.gff3.GFF3Annotation;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.gff3.GFF3Header;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

public class GFF3FileFactory implements IConversionRule<EmblEntryReader, GFF3File> {
  @Override
  public GFF3File from(EmblEntryReader entryReader) throws ConversionError {
    GFF3Header header = new GFF3Header("3.1.26");
    GFF3AnnotationFactory annotationFactory = new GFF3AnnotationFactory();
    List<GFF3Annotation> annotations = new ArrayList<>();
    try {
      while (entryReader.read() != null && entryReader.isEntry()) {
        annotations.add(annotationFactory.from(entryReader.getEntry()));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new GFF3File(header, annotations);
  }
}
