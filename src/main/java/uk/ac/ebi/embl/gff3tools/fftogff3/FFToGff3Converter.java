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
    // MasterFile will be used when converting reduced flatfile tto GFF3
    Path masterFilePath = null;
    Path nucleotideSequenceOutputPath = null;
    ValidationEngine validationEngine;

    public FFToGff3Converter(ValidationEngine validationEngine) {
        this.validationEngine = validationEngine;
    }

    // Constructor to be used only by the processing pipeline which converts reduced flatfile
    public FFToGff3Converter(ValidationEngine validationEngine, Path masterFilePath) {
        this.validationEngine = validationEngine;
        this.masterFilePath = masterFilePath;
    }

    // Constructor with nucleotide sequence output path
    public FFToGff3Converter(
            ValidationEngine validationEngine, Path masterFilePath, Path nucleotideSequenceOutputPath) {
        this.validationEngine = validationEngine;
        this.masterFilePath = masterFilePath;
        this.nucleotideSequenceOutputPath = nucleotideSequenceOutputPath;
    }

    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        Path fastaPath = getFastaPath();
        BufferedWriter nucleotideWriter = null;
        try {
            EmblEntryReader entryReader =
                    new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "embl_reader", getReaderOptions());

            GFF3FileFactory fftogff3 = new GFF3FileFactory(validationEngine, fastaPath);
            GFF3File file;

            // If we need to write nucleotide sequences, we must read entries into memory
            if (nucleotideSequenceOutputPath != null) {
                nucleotideWriter = Files.newBufferedWriter(nucleotideSequenceOutputPath);

                // Collect all entries
                java.util.List<Entry> entries = new java.util.ArrayList<>();
                try {
                    while (entryReader.read() != null && entryReader.isEntry()) {
                        entries.add(entryReader.getEntry());
                    }
                } catch (IOException e) {
                    throw new ReadException(e);
                }

                // Write nucleotide sequences
                writeNucleotideSequences(entries, nucleotideWriter);

                // Create GFF3File from collected entries
                file = fftogff3.from(entries, getMasterEntry(masterFilePath));
            } else {
                // Normal path - stream processing without collecting entries
                file = fftogff3.from(entryReader, getMasterEntry(masterFilePath));
            }

            file.writeGFF3String(writer);

            // Check for collected errors at end of processing
            validationEngine.throwIfErrorsCollected();
        } catch (IOException e) {
            throw new WriteException("Error writing nucleotide sequences", e);
        } finally {
            if (nucleotideWriter != null) {
                try {
                    nucleotideWriter.close();
                } catch (IOException ignored) {
                }
            }
            deleteFastaFile(fastaPath);
        }
    }

    private void writeNucleotideSequences(java.util.List<Entry> entries, BufferedWriter writer) throws IOException {
        for (Entry entry : entries) {
            if (entry.getSequence() != null && entry.getSequence().getLength() > 0) {
                // Write FASTA header
                String accession = entry.getPrimaryAccession();
                if (accession == null && entry.getSubmitterAccession() != null) {
                    accession = entry.getSubmitterAccession();
                }
                if (accession == null) {
                    accession = "sequence";
                }

                writer.write(">");
                writer.write(accession);
                writer.newLine();

                // Write sequence in 60-character lines
                byte[] seqBytes = entry.getSequence().getSequenceByte();
                if (seqBytes != null && seqBytes.length > 0) {
                    String seq = new String(seqBytes);
                    for (int i = 0; i < seq.length(); i += 60) {
                        writer.write(seq.substring(i, Math.min(i + 60, seq.length())));
                        writer.newLine();
                    }
                }
            }
        }
    }

    private ReaderOptions getReaderOptions() {
        ReaderOptions readerOptions = new ReaderOptions();
        // Only ignore sequences if we don't need to output them
        readerOptions.setIgnoreSequence(nucleotideSequenceOutputPath == null);
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

    /**
     * Create  FASTA in the  system temp directory.
     */
    private Path getFastaPath() {
        try {
            return Files.createTempFile("gff3-translation", ".fasta");
        } catch (Exception e) {
            throw new RuntimeException("Unable to create temp fasta file.", e);
        }
    }

    /**
     * Delete FASTA file in the  system temp directory.
     */
    private void deleteFastaFile(Path fastaPath) {
        try {
            Files.deleteIfExists(fastaPath);
        } catch (Exception e) {
            throw new RuntimeException("Unable to create temp fasta file.", e);
        }
    }
}
