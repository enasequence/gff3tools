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
package uk.ac.ebi.embl.gff3tools.fftogff3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class FFToGff3Converter implements Converter {

    private final ValidationEngine validationEngine;

    public FFToGff3Converter(ValidationEngine validationEngine) {
        this.validationEngine = validationEngine;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        EmblEntryReader entryReader =
                new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());

        GFF3FileFactory fftogff3 = new GFF3FileFactory(validationEngine);
        GFF3File file = fftogff3.from(entryReader, resolveMasterMetadata());
        file.writeGFF3String(writer);

        // Check for collected errors at end of processing
        validationEngine.throwIfErrorsCollected();
    }

    private MasterMetadata resolveMasterMetadata() {
        ValidationContext context = validationEngine.getContext();
        if (!context.contains(MasterMetadataProvider.class)) {
            return null;
        }
        return context.get(MasterMetadataProvider.class).getGlobalMetadata().orElse(null);
    }

    private ReaderOptions getReaderOptions() {
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        return readerOptions;
    }
}
