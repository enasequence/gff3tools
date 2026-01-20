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
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

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
    private final Path inputFilePath;

    /**
     * Creates a new TSV to GFF3 converter.
     *
     * @param validationEngine the validation engine to use
     * @param inputFilePath path to the input TSV file (needed to detect template ID)
     */
    public TSVToGFF3Converter(ValidationEngine validationEngine, Path inputFilePath) {
        this.validationEngine = validationEngine;
        this.inputFilePath = inputFilePath;
    }

    @Override
    public void convert(BufferedReader reader, BufferedWriter writer)
            throws ReadException, WriteException, ValidationException {

        Path fastaPath = createTempFastaPath();
        try {
            GFF3Header header = new GFF3Header("3.1.26");
            GFF3Species species = null;
            List<GFF3Annotation> annotations = new ArrayList<>();

            GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
            GFF3AnnotationFactory annotationFactory =
                    new GFF3AnnotationFactory(validationEngine, directivesFactory, fastaPath);

            // Note: We use inputFilePath rather than the reader because:
            // 1. We need to read the file twice (once for template ID, once for data)
            // 2. TSVEntryReader handles gzip detection internally
            try (TSVEntryReader entryReader = new TSVEntryReader(inputFilePath)) {
                LOG.info(
                        "Converting TSV file using template: {}",
                        entryReader.getTemplateInfo().getName());

                Entry entry;
                int entryCount = 0;
                while ((entry = entryReader.read()) != null) {
                    entryCount++;

                    if (species == null) {
                        species = directivesFactory.createSpecies(entry, null);
                    }

                    annotations.add(annotationFactory.from(entry));
                }

                LOG.info("Converted {} entries from TSV", entryCount);
            } catch (ReadException e) {
                throw e;
            } catch (IOException e) {
                throw new ReadException("Error reading TSV file", e);
            }

            GFF3File file = GFF3File.builder()
                    .header(header)
                    .species(species)
                    .annotations(annotations)
                    .fastaFilePath(fastaPath)
                    .parsingWarnings(validationEngine.getParsingWarnings())
                    .build();

            file.writeGFF3String(writer);

        } finally {
            deleteTempFile(fastaPath);
        }
    }

    /**
     * Creates a temporary FASTA file for storing translation sequences.
     */
    private Path createTempFastaPath() {
        try {
            return Files.createTempFile("gff3-translation-tsv", ".fasta");
        } catch (Exception e) {
            throw new RuntimeException("Unable to create temp fasta file.", e);
        }
    }

    /**
     * Deletes the temporary FASTA file.
     */
    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            LOG.warn("Unable to delete temp file: {}", path, e);
        }
    }
}
