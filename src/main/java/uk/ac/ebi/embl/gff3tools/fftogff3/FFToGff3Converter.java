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
import java.nio.file.Files;
import java.nio.file.Path;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.gff3.*;
import uk.ac.ebi.embl.gff3tools.metadata.AnnotationMetadata;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class FFToGff3Converter implements Converter {

    private final ValidationEngine validationEngine;
    private AnnotationMetadata masterMetadata;

    /** Legacy field: kept only for backward compat with the Path-based constructor. */
    private Path masterFilePath = null;

    public FFToGff3Converter(ValidationEngine validationEngine) {
        this.validationEngine = validationEngine;
    }

    /**
     * Constructor accepting pre-built AnnotationMetadata (from --master-entry).
     */
    public FFToGff3Converter(ValidationEngine validationEngine, AnnotationMetadata masterMetadata) {
        this.validationEngine = validationEngine;
        this.masterMetadata = masterMetadata;
    }

    /**
     * Legacy constructor accepting a raw EMBL flatfile path. Kept for backward compatibility.
     * The EMBL file is parsed into an Entry and then into AnnotationMetadata on convert().
     */
    public FFToGff3Converter(ValidationEngine validationEngine, Path masterFilePath) {
        this.validationEngine = validationEngine;
        this.masterFilePath = masterFilePath;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        // If we have a legacy master file path but no metadata yet, parse it
        if (masterMetadata == null && masterFilePath != null) {
            Entry masterEntry = getMasterEntry(masterFilePath);
            if (masterEntry != null) {
                masterMetadata =
                        new uk.ac.ebi.embl.gff3tools.metadata.EmblEntryMetadataSource(masterEntry).getMetadata();
            }
        }

        EmblEntryReader entryReader =
                new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());

        GFF3FileFactory fftogff3 = new GFF3FileFactory(validationEngine);
        GFF3File file = fftogff3.from(entryReader, masterMetadata);
        file.writeGFF3String(writer);

        // Check for collected errors at end of processing
        validationEngine.throwIfErrorsCollected();
    }

    private ReaderOptions getReaderOptions() {
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        return readerOptions;
    }

    private Entry getMasterEntry(Path masterFilePath) throws ReadException {
        if (masterFilePath == null) {
            return null;
        }
        try (BufferedReader inputReader = Files.newBufferedReader(masterFilePath)) {
            Entry masterEntry = null;
            EmblEntryReader entryReader = new EmblEntryReader(
                    inputReader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());
            while (entryReader.read() != null && entryReader.isEntry()) {
                masterEntry = entryReader.getEntry();
            }
            return masterEntry;
        } catch (IOException e) {
            throw new ReadException("Error opening master file: " + masterFilePath, e);
        }
    }
}
