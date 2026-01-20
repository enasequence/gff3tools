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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.fasta.writer.FastaFileWriter;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

public class GFF3FileFactory {
    private static final String HEADER_VERSION = "3.1.26";

    ValidationEngine engine;
    Path fastaFilePath = null;

    public GFF3FileFactory(ValidationEngine engine) {
        this.engine = engine;
    }

    public GFF3FileFactory(ValidationEngine engine, Path fastaFilePath) {
        this.engine = engine;
        this.fastaFilePath = fastaFilePath;
    }

    public GFF3File from(EmblEntryReader entryReader, Entry masterEntry)
            throws ValidationException, ReadException, WriteException {
        return from(entryReader, masterEntry, null);
    }

    /**
     * Converts EMBL entries to GFF3, optionally writing nucleotide sequences to a FASTA file.
     *
     * @param entryReader the EMBL entry reader
     * @param masterEntry optional master entry for reduced flatfile conversion
     * @param nucleotideFastaWriter optional writer for nucleotide sequences; if provided, sequences
     *     are written as each entry is processed (streaming). Caller is responsible for closing.
     * @return the GFF3 file
     */
    public GFF3File from(EmblEntryReader entryReader, Entry masterEntry, BufferedWriter nucleotideFastaWriter)
            throws ValidationException, ReadException, WriteException {
        GFF3Header header = new GFF3Header(HEADER_VERSION);
        GFF3Species species = null;
        List<GFF3Annotation> annotations = new ArrayList<>();
        GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
        GFF3AnnotationFactory annotationFactory = new GFF3AnnotationFactory(engine, directivesFactory, fastaFilePath);

        try {
            while (entryReader.read() != null && entryReader.isEntry()) {
                Entry entry = entryReader.getEntry();

                // Write nucleotide sequence to FASTA if writer is provided
                if (nucleotideFastaWriter != null) {
                    writeNucleotideSequence(entry, nucleotideFastaWriter);
                }

                if (species == null) {
                    Entry sourceEntry = masterEntry != null ? masterEntry : entry;
                    species = directivesFactory.createSpecies(sourceEntry, (MasterMetadata) null);
                }
                annotations.add(annotationFactory.from(entry));
            }
        } catch (IOException e) {
            throw new ReadException(e);
        } finally {
            // Close the nucleotide FASTA writer if provided
            if (nucleotideFastaWriter != null) {
                try {
                    nucleotideFastaWriter.close();
                } catch (IOException e) {
                    // Best effort cleanup
                }
            }
        }

        TranslationState translationState = engine.getContext().contains(TranslationState.class)
                ? engine.getContext().get(TranslationState.class)
                : null;

        return GFF3File.builder()
                .header(header)
                .species(species)
                .annotations(annotations)
                .fastaFilePath(fastaFilePath)
                .translationState(translationState)
                .parsingWarnings(engine.getParsingWarnings())
                .build();
    }

    public GFF3File from(List<Entry> entries, Entry masterEntry) throws ValidationException {
        GFF3Header header = new GFF3Header(HEADER_VERSION);
        GFF3Species species = null;
        List<GFF3Annotation> annotations = new ArrayList<>();
        GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
        GFF3AnnotationFactory annotationFactory = new GFF3AnnotationFactory(engine, directivesFactory, fastaFilePath);

        for (Entry entry : entries) {
            if (species == null) {
                species = directivesFactory.createSpecies(entry, masterEntry);
            }
            annotations.add(annotationFactory.from(entry));
        }

        return GFF3File.builder()
                .header(header)
                .species(species)
                .annotations(annotations)
                .fastaFilePath(fastaFilePath)
                .parsingWarnings(engine.getParsingWarnings())
                .build();
    }

    /**
     * Creates a GFF3File from pre-built annotations and an existing GFF3 reader.
     *
     * @param annotations list of already constructed GFF3 annotations
     * @param gff3FileReader reader providing species, validation context, and warnings
     * @param appendTranslationFasta flag indicating whether to append annotation FASTA output
     * @param existingTranslationFilePathFallback optional path to an existing translation FASTA file, which will be defaulted to if the {@link TranslationState} is not available }
     * @return a GFF3File populated with provided annotations, reader context, optional FASTA path, and warnings
     */
    public static GFF3File fromAnnotationAndReader(
            List<GFF3Annotation> annotations,
            GFF3FileReader gff3FileReader,
            boolean appendTranslationFasta,
            Optional<Path> existingTranslationFilePathFallback) {

        TranslationState translationState =
                gff3FileReader.getValidationEngine().getContext().contains(TranslationState.class)
                        ? gff3FileReader.getValidationEngine().getContext().get(TranslationState.class)
                        : null;

        return GFF3File.builder()
                .header(new GFF3Header(HEADER_VERSION))
                .species(gff3FileReader.gff3Species)
                .annotations(annotations)
                .gff3Reader(gff3FileReader)
                .fastaFilePath(
                        existingTranslationFilePathFallback.isPresent()
                                ? existingTranslationFilePathFallback.get()
                                : null)
                .writeAnnotationFasta(appendTranslationFasta)
                .parsingWarnings(gff3FileReader.getValidationEngine().getParsingWarnings())
                .translationState(translationState)
                .build();
    }

    /**
     * Writes nucleotide sequence from an entry to the FASTA writer.
     */
    private void writeNucleotideSequence(Entry entry, BufferedWriter fastaWriter) throws WriteException {
        if (entry.getSequence() != null && entry.getSequence().getLength() > 0) {
            try {
                new FastaFileWriter(entry, fastaWriter).write();
            } catch (IOException e) {
                throw new WriteException("Error writing nucleotide sequence to FASTA", e);
            }
        }
    }
}
