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
package uk.ac.ebi.embl.gff3tools.tsvconverter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.*;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3AnnotationFactory;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3DirectivesFactory;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.SourceFeatureDTO;
import uk.ac.ebi.embl.gff3tools.utils.SourceFeatureUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Converts TSV files to GFF3 format.
 *
 * <p>This converter uses sequencetools' template processing to read TSV files and convert
 * them to Entry objects, then uses the existing GFF3 conversion infrastructure to produce
 * GFF3 output.
 *
 * <p>The TSV file must contain a template ID line (e.g., "Checklist ERT000002") in the
 * first 10 lines, followed by a header row and data rows.
 */
public class TSVToGFF3Converter implements Converter {
    private static final Logger LOG = LoggerFactory.getLogger(TSVToGFF3Converter.class);

    private final ValidationEngine validationEngine;
    // Optional output path for FASTA sequences; if null, sequences are discarded
    private final Path fastaOutputPath;
    private final Path sourceOutputPath;

    /**
     * Creates a new TSV to GFF3 converter.
     *
     * @param validationEngine the validation engine to use
     */
    public TSVToGFF3Converter(ValidationEngine validationEngine) {
        this(validationEngine, null);
    }

    /**
     * Creates a new TSV to GFF3 converter.
     *
     * @param validationEngine the validation engine to use
     * @param fastaOutputPath optional output path for FASTA sequences; if null, sequences are discarded
     */
    public TSVToGFF3Converter(ValidationEngine validationEngine, Path fastaOutputPath) {
        this.validationEngine = validationEngine;
        this.fastaOutputPath = fastaOutputPath;
        this.sourceOutputPath = null;
    }

    /**
     * Creates a new TSV to GFF3 converter.
     *
     * @param validationEngine the validation engine to use
     * @param fastaOutputPath optional output path for FASTA sequences; if null, sequences are discarded
     *
     */
    public TSVToGFF3Converter(ValidationEngine validationEngine, Path fastaOutputPath, Path sourceOutputPath) {
        this.validationEngine = validationEngine;
        this.fastaOutputPath = fastaOutputPath;
        this.sourceOutputPath = sourceOutputPath;
    }

    @Override
    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        GFF3Header header = new GFF3Header(GFF3Header.DEFAULT_VERSION);
        GFF3Species species = null;
        List<GFF3Annotation> annotations = new ArrayList<>();

        GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
        GFF3AnnotationFactory annotationFactory = new GFF3AnnotationFactory(validationEngine, directivesFactory);

        try (TSVEntryReader entryReader = new TSVEntryReader(reader);
                BufferedWriter nucleotideFastaWriter = createNucleotideFastaWriter()) {
            LOG.info(
                    "Converting TSV file using template: {}",
                    entryReader.getTemplateInfo().getName());

            Entry entry;
            List<SourceFeatureDTO> sourceFeatures = new ArrayList<>();
            int entryCount = 0;
            boolean wroteSource = false;
            while ((entry = entryReader.read()) != null) {
                entryCount++;

                if (sourceOutputPath != null) {
                    SourceFeatureDTO sourceFeature = new SourceFeatureDTO(entry.getPrimarySourceFeature());
                    sourceFeatures.add(sourceFeature);
                }

                // Write nucleotide sequence to FASTA if writer is provided (streaming)
                if (nucleotideFastaWriter != null) {
                    ConversionUtils.writeNucleotideSequence(entry, nucleotideFastaWriter);
                }

                if (species == null) {
                    species = directivesFactory.createSpecies(entry, null);
                }

                annotations.add(annotationFactory.from(entry));
            }

            LOG.info("Converted {} entries from TSV", entryCount);
            if (nucleotideFastaWriter != null) {
                LOG.info("Wrote nucleotide sequences to FASTA file: {}", fastaOutputPath);
            }
            if (sourceOutputPath != null) {
                SourceFeatureUtils.dumpSourceFeatureDto(sourceFeatures, sourceOutputPath);
                LOG.info("Wrote nucleotide sequences to FASTA file: {}", fastaOutputPath);
            }
        } catch (IOException e) {
            throw new ReadException("Error reading TSV file", e);
        }

        TranslationState translationState = validationEngine.getContext().contains(TranslationState.class)
                ? validationEngine.getContext().get(TranslationState.class)
                : null;

        GFF3File file = GFF3File.builder()
                .header(header)
                .species(species)
                .annotations(annotations)
                .translationState(translationState)
                .parsingWarnings(validationEngine.getParsingWarnings())
                .build();

        file.writeGFF3String(writer);

        validationEngine.throwIfErrorsCollected();
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
}
