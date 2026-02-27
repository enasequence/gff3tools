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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

public class GFF3FileFactory {
    ValidationEngine engine;
    Path fastaFilePath = null;

    public GFF3FileFactory(ValidationEngine engine, Path fastaFilePath) {
        this.engine = engine;
        this.fastaFilePath = fastaFilePath;
    }

    public GFF3File from(EmblEntryReader entryReader, Entry masterEntry) throws ValidationException, ReadException {
        GFF3Header header = new GFF3Header("3.1.26");
        GFF3Species species = null;
        List<GFF3Annotation> annotations = new ArrayList<>();
        GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
        GFF3AnnotationFactory annotationFactory = new GFF3AnnotationFactory(engine, directivesFactory, fastaFilePath);
        try {
            while (entryReader.read() != null && entryReader.isEntry()) {
                Entry entry = entryReader.getEntry();
                if (species == null) {
                    species = directivesFactory.createSpecies(entry, masterEntry);
                }
                annotations.add(annotationFactory.from(entry));
            }
        } catch (IOException e) {
            throw new ReadException(e);
        }

        return GFF3File.builder()
                .header(header)
                .species(species)
                .annotations(annotations)
                .fastaFilePath(fastaFilePath)
                .parsingWarnings(engine.getParsingWarnings())
                .build();
    }

    public GFF3File from(List<Entry> entries, Entry masterEntry) throws ValidationException {
        GFF3Header header = new GFF3Header("3.1.26");
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
}
