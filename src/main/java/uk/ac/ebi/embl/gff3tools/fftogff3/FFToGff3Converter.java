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
import uk.ac.ebi.embl.gff3tools.validation.*;

public class FFToGff3Converter implements Converter {
    // MasterFile will be used when converting reduced flatfile to GFF3
    private final Path masterFilePath;
    // Optional output path for FASTA sequences; if null, sequences are discarded
    private final Path fastaOutputPath;
    private final ValidationEngine validationEngine;

    public FFToGff3Converter(ValidationEngine validationEngine) {
        this(validationEngine, null, null);
    }

    public FFToGff3Converter(ValidationEngine validationEngine, Path masterFilePath) {
        this(validationEngine, masterFilePath, null);
    }

    /**
     * Creates a new EMBL to GFF3 converter.
     *
     * @param validationEngine the validation engine to use
     * @param masterFilePath optional master file path for reduced flatfile conversion
     * @param fastaOutputPath optional output path for FASTA sequences; if null, sequences are discarded
     */
    public FFToGff3Converter(ValidationEngine validationEngine, Path masterFilePath, Path fastaOutputPath) {
        this.validationEngine = validationEngine;
        this.masterFilePath = masterFilePath;
        this.fastaOutputPath = fastaOutputPath;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        // Translation sequences go to a temp file (used internally for GFF3 output)
        Path translationFastaPath = createTempFastaPath();

        try {
            // Read sequences only if we need to write them to FASTA output
            boolean readSequences = (fastaOutputPath != null);
            EmblEntryReader entryReader = new EmblEntryReader(
                    reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions(readSequences));

            // Process entries with optional FASTA writing (streaming, one entry at a time)
            GFF3FileFactory fftogff3 = new GFF3FileFactory(validationEngine, translationFastaPath);
            GFF3File file = fftogff3.from(entryReader, getMasterEntry(masterFilePath), createNucleotideFastaWriter());
            file.writeGFF3String(writer);

            // Check for collected errors at end of processing
            validationEngine.throwIfErrorsCollected();
        } finally {
            deleteFastaFile(translationFastaPath);
        }
    }

    /**
     * Creates a BufferedWriter for nucleotide FASTA output if fastaOutputPath is set.
     *
     * @return BufferedWriter for FASTA output, or null if no FASTA output path was specified
     */
    private BufferedWriter createNucleotideFastaWriter() throws WriteException {
        if (fastaOutputPath == null) {
            return null;
        }
        try {
            return Files.newBufferedWriter(fastaOutputPath);
        } catch (IOException e) {
            throw new WriteException("Error creating FASTA output file: " + fastaOutputPath, e);
        }
    }

    private ReaderOptions getReaderOptions(boolean readSequences) {
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(!readSequences);
        return readerOptions;
    }

    private Entry getMasterEntry(Path masterFilePath) throws ReadException {
        if (masterFilePath == null) {
            return null;
        }
        try (BufferedReader inputReader = Files.newBufferedReader(masterFilePath)) {
            Entry masterEntry = null;
            EmblEntryReader entryReader = new EmblEntryReader(
                    inputReader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions(false));
            while (entryReader.read() != null && entryReader.isEntry()) {
                masterEntry = entryReader.getEntry();
            }
            return masterEntry;
        } catch (IOException e) {
            throw new ReadException("Error opening master file: " + masterFilePath, e);
        }
    }

    /**
     * Creates a temporary FASTA file in the system temp directory.
     */
    private Path createTempFastaPath() {
        try {
            return Files.createTempFile("gff3-translation", ".fasta");
        } catch (Exception e) {
            throw new RuntimeException("Unable to create temp fasta file.", e);
        }
    }

    /**
     * Deletes the temporary FASTA file.
     */
    private void deleteFastaFile(Path fastaPath) {
        try {
            Files.deleteIfExists(fastaPath);
        } catch (Exception e) {
            // Log warning but don't fail - temp file cleanup is best effort
        }
    }
}
