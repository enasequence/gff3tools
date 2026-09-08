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
package uk.ac.ebi.embl.gff3tools.gff3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
import uk.ac.ebi.embl.gff3tools.gff3.writer.TranslationWriter;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * An in-memory GFF3 document, ready to be written.
 *
 * <p>A file holds whichever annotations it was given — every annotation of a submission, or a
 * subset of them — and writes a document of the shape:
 *
 * <pre>
 * ##gff-version …        (header, when set)
 * ##species …            (when set)
 * …features…             (every annotation, in order)
 * ##FASTA                (once, only when translations are written)
 * &gt;accession|featureId
 * …
 * </pre>
 *
 * <p>The {@code ##FASTA} directive terminates the feature section in the GFF3 specification, so it
 * is written once, after the last annotation, and never between annotations. Translations are
 * scoped to the annotations this file contains: a file holding one annotation carries that
 * annotation's translations and no others.
 *
 * <p>Instances are built with the generated builder. Fields:
 *
 * <ul>
 *   <li>{@code header}, {@code species} — optional directives, omitted when null.
 *   <li>{@code annotations} — the annotations this file contains; also the scope for translations.
 *   <li>{@code writeAnnotationFasta} — whether to write translations at all. Defaults to
 *       {@code false}, so a caller that supplies a translation source must also opt in.
 *   <li>{@code translationState}, {@code fastaFilePath}, {@code gff3Reader} — translation sources,
 *       consulted in that order; see {@link #writeGFF3String(Writer)}.
 *   <li>{@code parsingWarnings} — carried for the caller's benefit; not written to the document.
 * </ul>
 */
@Slf4j
@Builder
public class GFF3File implements IGFF3Feature {

    GFF3Header header;
    GFF3Species species;
    List<GFF3Annotation> annotations;
    GFF3FileReader gff3Reader;
    Path fastaFilePath;
    boolean writeAnnotationFasta;
    List<ValidationException> parsingWarnings;
    TranslationState translationState;

    /**
     * Creates a GFF3 document. Prefer the generated builder.
     *
     * @param header the {@code ##gff-version} directive, or null to omit it
     * @param species the {@code ##species} directive, or null to omit it
     * @param annotations the annotations this file contains; also the scope for its translations
     * @param gff3FileReader reader over a source GFF3, used as the last translation source and to
     *     read translations lazily by offset; may be null when no source GFF3 exists
     * @param fastaFilePath an existing translation FASTA copied verbatim, used when
     *     {@code translationState} yields nothing; may be null. Cannot be scoped to a subset of
     *     annotations, so do not combine it with a file holding only some of them
     * @param writeAnnotationFasta whether to write translations at all; when false no
     *     {@code ##FASTA} section is written even if a source could supply one
     * @param parsingWarnings warnings collected while parsing the source; carried for the caller,
     *     never written to the document
     * @param translationState the preferred translation source, normally populated by
     *     {@code TranslationFix} during validation. Supplied by an auto-discovered provider, so it
     *     is typically non-null but may hold nothing — selection is by content, not by presence
     */
    public GFF3File(
            GFF3Header header,
            GFF3Species species,
            List<GFF3Annotation> annotations,
            GFF3FileReader gff3FileReader,
            Path fastaFilePath,
            boolean writeAnnotationFasta,
            List<ValidationException> parsingWarnings,
            TranslationState translationState) {

        this.header = header;
        this.species = species;
        this.annotations = annotations;
        this.fastaFilePath = fastaFilePath;
        this.parsingWarnings = parsingWarnings;
        this.gff3Reader = gff3FileReader;
        this.writeAnnotationFasta = writeAnnotationFasta;
        this.translationState = translationState;
    }

    /**
     * Writes this document: header, species, every annotation's features, then at most one
     * {@code ##FASTA} section.
     *
     * <p>Translations are written only when {@code writeAnnotationFasta} is set, and are taken
     * from the first source that actually yields any — {@code translationState}, then
     * {@code fastaFilePath}, then the reader's offset map. Selection is by content rather than by
     * presence: an empty source falls through to the next one instead of ending the chain. When no
     * source yields a translation, no {@code ##FASTA} directive is written at all.
     *
     * <p>Except for {@code fastaFilePath}, which is copied verbatim, translations are filtered to
     * those belonging to this file's annotations.
     *
     * @param writer destination; not closed by this method
     * @throws WriteException if the underlying writer fails
     */
    @Override
    public void writeGFF3String(Writer writer) throws WriteException {

        try {
            if (header != null) {
                header.writeGFF3String(writer);
            }

            if (species != null) {
                species.writeGFF3String(writer);
            }

            for (GFF3Annotation ann : annotations) {
                ann.writeGFF3String(writer);
            }

            // ##FASTA terminates the feature section, so it is written once, after all annotations
            if (writeAnnotationFasta) {
                writeTranslationSection(writer);
            }
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    /**
     * Translation offsets belonging to this file's annotations, gathered in annotation order.
     *
     * <p>Scoping to {@code annotations} rather than taking the reader's whole map is what lets a
     * file hold a subset of a submission's annotations and carry exactly that subset's
     * translations.
     */
    private Map<String, OffsetRange> translationOffsetsForAnnotations() {
        Map<String, OffsetRange> offsets = new LinkedHashMap<>();
        if (gff3Reader == null) {
            return offsets;
        }
        for (GFF3Annotation ann : annotations) {
            offsets.putAll(gff3Reader.getTranslationOffsetForAnnotation(ann));
        }
        return offsets;
    }

    /**
     * Writes this file's translations, from the first source that actually yields any.
     *
     * <p>Selection is by content, not by presence: {@code TranslationState} is supplied by an
     * auto-discovered provider and so is never null, and a fallback that only fires on null could
     * never fire at all.
     */
    private void writeTranslationSection(Writer writer) throws IOException {
        Set<String> accessions =
                annotations.stream().map(GFF3Annotation::getAccession).collect(Collectors.toSet());

        if (writeFastaFromTranslationState(writer, accessions)) {
            return;
        }
        if (writeFastaFromExistingFile(writer)) {
            return;
        }
        writeFastaFromOffsets(writer, translationOffsetsForAnnotations());
    }

    /** True when a translation key belongs to one of this file's annotations. */
    private static boolean belongsTo(String translationKey, Set<String> accessions) {
        String accession = TranslationKey.accessionOf(translationKey);
        return accession != null && accessions.contains(accession);
    }

    private boolean writeFastaFromTranslationState(Writer writer, Set<String> accessions) throws IOException {
        if (translationState == null) {
            return false;
        }
        List<Map.Entry<String, String>> toWrite = new java.util.ArrayList<>();
        translationState.forEachResolved((key, translation) -> {
            if (belongsTo(key, accessions)) {
                toWrite.add(Map.entry(key, translation));
            }
        });

        if (toWrite.isEmpty()) {
            return false;
        }

        writer.write("##FASTA\n");
        for (Map.Entry<String, String> e : toWrite) {
            TranslationWriter.writeTranslation(writer, e.getKey(), e.getValue());
        }
        log.info("Written {} translation sequences from TranslationState", toWrite.size());
        writer.write("\n");
        return true;
    }

    private boolean writeFastaFromExistingFile(Writer writer) throws IOException {
        if (fastaFilePath == null) {
            return false;
        }

        BasicFileAttributes attrs = Files.readAttributes(fastaFilePath, BasicFileAttributes.class);

        if (!attrs.isRegularFile() || attrs.size() == 0) {
            return false;
        }

        writer.write("##FASTA\n");

        try (BufferedReader br = Files.newBufferedReader(fastaFilePath)) {
            char[] buffer = new char[8192];
            int n;
            while ((n = br.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        }
        log.info("Write translation sequences from: " + fastaFilePath);
        return true;
    }

    private void writeFastaFromOffsets(Writer writer, Map<String, OffsetRange> translationOffsetMap)
            throws IOException {

        if (translationOffsetMap.isEmpty()) {
            return;
        }

        writer.write("##FASTA\n");

        for (Map.Entry<String, OffsetRange> entry : translationOffsetMap.entrySet()) {
            String id = entry.getKey();
            OffsetRange range = entry.getValue();

            String translation = gff3Reader.getTranslation(range);
            TranslationWriter.writeTranslation(writer, id, translation);
        }
        log.info("Written {} sequences from: ", translationOffsetMap.entrySet().size());
        writer.write("\n");
    }
}
