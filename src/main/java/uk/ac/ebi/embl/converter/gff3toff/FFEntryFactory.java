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
import uk.ac.ebi.embl.converter.fftogff3.FFtoGFF3ConversionError;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3ValidationError;

public class FFEntryFactory {

    public EmblFlatFile from(GFF3FileReader entryReader) throws FFtoGFF3ConversionError {
        GFF3Mapper mapper = new GFF3Mapper();
        try {
            List<Entry> entries = mapper.mapGFF3ToEntry(entryReader);
            return new EmblFlatFile(entries);
        } catch (IOException | GFF3ValidationError e) {
            throw new FFtoGFF3ConversionError(e.getMessage());
        }
    }
}
