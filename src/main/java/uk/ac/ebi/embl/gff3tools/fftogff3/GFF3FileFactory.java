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
import java.util.Optional;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Builds {@link GFF3File} documents from the two sources gff3tools converts: EMBL flat files
 * ({@link #from}) and already-parsed GFF3 annotations ({@link #fromAnnotationAndReader}).
 */
public class GFF3FileFactory {

    /** The GFF3 specification version written into every document this factory builds. */
    private static final String HEADER_VERSION = "3.1.26";

    private final ValidationEngine engine;

    /**
     * @param engine the validation engine whose context supplies translations and parsing
     *     warnings; used by {@link #from} only
     */
    public GFF3FileFactory(ValidationEngine engine) {
        this.engine = engine;
    }

    /**
     * Converts an EMBL flat file into a GFF3 document, one annotation per entry.
     *
     * <p>Translations come from the {@link TranslationState} that {@code TranslationFix}
     * populates during validation — here, each entry's {@code /translation} qualifier.
     *
     * <p>The {@code ##gff-version} directive is always {@link #HEADER_VERSION}, whatever the
     * source says, and {@code ##species} comes from the first entry that yields one — a
     * mixed-organism flat file loses the rest without a diagnostic.
     *
     * @param entryReader reader over the flat file; consumed to exhaustion
     * @param masterMetadata metadata whose scientific name or taxon takes precedence when deriving
     *     {@code ##species}; may be null, in which case the first entry's source feature is used
     * @return a document containing every entry as an annotation, with its translations
     * @throws ValidationException if an entry fails validation
     * @throws ReadException if the flat file cannot be read
     */
    public GFF3File from(EmblEntryReader entryReader, MasterMetadata masterMetadata)
            throws ValidationException, ReadException {
        GFF3Header header = new GFF3Header(HEADER_VERSION);
        GFF3Species species = null;
        List<GFF3Annotation> annotations = new ArrayList<>();
        GFF3DirectivesFactory directivesFactory = new GFF3DirectivesFactory();
        GFF3AnnotationFactory annotationFactory = new GFF3AnnotationFactory(engine, directivesFactory);
        try {
            while (entryReader.read() != null && entryReader.isEntry()) {
                Entry entry = entryReader.getEntry();
                if (species == null) {
                    species = directivesFactory.createSpecies(entry, masterMetadata);
                }
                annotations.add(annotationFactory.from(entry));
            }
        } catch (IOException e) {
            throw new ReadException(e);
        }

        TranslationState translationState = engine.getContext().contains(TranslationState.class)
                ? engine.getContext().get(TranslationState.class)
                : null;

        return GFF3File.builder()
                .header(header)
                .species(species)
                .annotations(annotations)
                .translationState(translationState)
                .writeAnnotationFasta(true)
                .parsingWarnings(engine.getParsingWarnings())
                .build();
    }

    /**
     * Creates a GFF3 document from already-parsed annotations and the reader they came from.
     *
     * <p>Pass a subset of the reader's annotations to get a document holding just those, with
     * exactly their translations — that is how one submission is split across several documents.
     *
     * <p>Translations, all limited to the accessions of {@code annotations}:
     * <ol>
     *   <li>the source GFF3's own {@code ##FASTA}, merged per feature with the
     *       {@link TranslationState} in the reader's validation context — the state wins where both
     *       have the same feature;
     *   <li>only if that merge is empty, {@code existingTranslationFilePathFallback}.
     * </ol>
     * If neither yields anything, no {@code ##FASTA} section is written.
     *
     * @param annotations the annotations the document contains; may be a subset of the reader's
     * @param gff3FileReader the reader those annotations came from; supplies {@code ##species}, the
     *     validation context, parsing warnings, and the source GFF3's translations
     * @param appendTranslationFasta whether to write translations at all; {@code false} gives a
     *     features-only document
     * @param existingTranslationFilePathFallback a translation FASTA used only when the merge above
     *     is empty; {@link Optional#empty()} for none
     * @return a document holding the given annotations, and their translations when requested
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
}
