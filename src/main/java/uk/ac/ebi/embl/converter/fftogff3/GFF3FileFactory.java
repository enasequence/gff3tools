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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.converter.exception.ReadException;
import uk.ac.ebi.embl.converter.exception.ValidationException;
import uk.ac.ebi.embl.converter.gff3.GFF3Annotation;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.gff3.GFF3Header;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

public class GFF3FileFactory {
    public GFF3File from(EmblEntryReader entryReader) throws ValidationException, ReadException {
        GFF3Header header = new GFF3Header("3.1.26");
        List<GFF3Annotation> annotations = new ArrayList<>();
        int entryCount = 0;
        try {
            while (entryReader.read() != null && entryReader.isEntry()) {
                annotations.add(new GFF3AnnotationFactory(entryCount > 0).from(entryReader.getEntry()));
                entryCount++;
            }
        } catch (IOException e) {
            throw new ReadException(e);
        }

        return new GFF3File(header, annotations);
    }
}
