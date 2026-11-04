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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.metadata.MasterEntryJsonWriter;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class FFToGff3Converter implements Converter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FFToGff3Converter.class);

    private final ValidationEngine validationEngine;
    private final Path masterOutputPath;

    public FFToGff3Converter(ValidationEngine validationEngine) {
        this(validationEngine, null);
    }

    /**
     * @param masterOutputPath when not null, a master-entry JSON describing the (first) flat file
     *     entry is written there as a by-product, once the conversion has completed without errors
     */
    public FFToGff3Converter(ValidationEngine validationEngine, Path masterOutputPath) {
        this.validationEngine = validationEngine;
        this.masterOutputPath = masterOutputPath;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        EmblEntryReader entryReader =
                new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());

        GFF3FileFactory fftogff3 = new GFF3FileFactory(validationEngine);
        List<MasterMetadata> generatedMasters = new ArrayList<>();
        Consumer<Entry> entryObserver = masterOutputPath == null
                ? entry -> {}
                : entry -> {
                    // Built before conversion, which may modify the entry. Only the first entry
                    // is kept: a master describes one record.
                    if (generatedMasters.isEmpty()) {
                        generatedMasters.add(MasterEntryJsonWriter.fromEntry(entry));
                    } else if (generatedMasters.size() == 1) {
                        generatedMasters.add(null);
                        LOGGER.warn("Input has more than one entry; the master entry JSON describes only the first");
                    }
                };
        GFF3File file = fftogff3.from(entryReader, resolveMasterMetadata(), entryObserver);
        file.writeGFF3String(writer);

        // Check for collected errors at end of processing
        validationEngine.throwIfErrorsCollected();

        if (masterOutputPath != null) {
            if (generatedMasters.isEmpty()) {
                throw new ReadException(new IOException("No entry found in the input; cannot write the master entry"));
            }
            try {
                MasterEntryJsonWriter.write(generatedMasters.get(0), masterOutputPath);
            } catch (IOException e) {
                throw new WriteException(e);
            }
        }
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
