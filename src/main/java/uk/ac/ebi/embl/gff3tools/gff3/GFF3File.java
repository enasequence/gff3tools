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
 * An in-memory GFF3 document, ready to be written. It holds whichever annotations it was given —
 * every annotation of a submission, or a subset of them — and is built with the generated builder.
 *
 * <p>{@code ##FASTA} terminates the feature section, so it is written once, after the last
 * annotation, never between them. Translations are scoped to the relevant annotations.
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
     * @param gff3FileReader reader over a source GFF3, whose own {@code ##FASTA} section supplies
     *     the translations {@code translationState} does not override; may be null when no source
     *     GFF3 exists or it cannot be re-read (e.g. stdin)
     * @param fastaFilePath an existing translation FASTA, used only when neither
     *     {@code gff3FileReader} nor {@code translationState} yields a translation for this file;
     *     its records are filtered to this file's accessions. May be null
     * @param writeAnnotationFasta whether to write translations at all; when false no
     *     {@code ##FASTA} section is written even if a source could supply one
     * @param parsingWarnings warnings collected while parsing the source; carried for the caller,
     *     never written to the document
     * @param translationState translations captured or computed during validation, normally by
     *     {@code TranslationFix}; each entry wins over the source GFF3's translation for the same
     *     feature. May hold nothing
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
     * Writes this document: header, species, every annotation's features, then the translations of
     * those annotations in at most one {@code ##FASTA} section — see
     * {@link #writeTranslationSection(Writer)}.
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

            if (writeAnnotationFasta) {
                writeTranslationSection(writer);
            }
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    /**
     * Writes this file's translations, scoped to its accessions.
     *
     * <p>The source GFF3's {@code ##FASTA} and {@code TranslationState} are merged per feature,
     * not chosen between: a feature this validation run never touched has no state entry, and
     * must keep its original translation even when other features in the same file do have one.
     * The state is overlaid last, so it wins where both hold the same feature.
     *
     * <p>The existing translation FASTA is used only when that merge is empty. Selection is by
     * content, not by presence: {@code TranslationState} is supplied by an auto-discovered provider
     * and so is never null, and a fallback that only fires on null could never fire at all.
     */
    private void writeTranslationSection(Writer writer) throws IOException {
        Set<String> accessions =
                annotations.stream().map(GFF3Annotation::getAccession).collect(Collectors.toSet());

        Map<String, String> translations = new LinkedHashMap<>();
        if (gff3Reader != null) {
            for (GFF3Annotation ann : annotations) {
                for (Map.Entry<String, OffsetRange> entry :
                        gff3Reader.getTranslationOffsetForAnnotation(ann).entrySet()) {
                    translations.put(entry.getKey(), gff3Reader.getTranslation(entry.getValue()));
                }
            }
        }
        if (translationState != null) {
            translationState.forEachResolved((key, translation) -> {
                if (TranslationKey.belongsToAny(key, accessions)) {
                    translations.put(key, translation);
                }
            });
        }

        if (!translations.isEmpty()) {
            writeFastaFromMap(writer, translations);
            return;
        }
        writeFastaFromExistingFile(writer, accessions);
    }

    private void writeFastaFromMap(Writer writer, Map<String, String> translations) throws IOException {
        writer.write("##FASTA\n");
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            TranslationWriter.writeTranslation(writer, entry.getKey(), entry.getValue());
        }
        log.info("Written {} translation sequences", translations.size());
        writer.write("\n");
    }

    /**
     * Copies the records of an existing translation FASTA whose {@code >accession|featureId}
     * header names one of this document's accessions. A header in any other shape is dropped —
     * keeping it is the misattribution this scoping prevents — and dropped records are logged.
     *
     * <p>This is the last translation source. An absent, empty or wholly foreign file yields no
     * {@code ##FASTA} section rather than failing the write, since reaching it is normal, not an
     * error.
     */
    private void writeFastaFromExistingFile(Writer writer, Set<String> accessions) throws IOException {
        if (fastaFilePath == null) {
            return;
        }

        if (!Files.isRegularFile(fastaFilePath) || Files.size(fastaFilePath) == 0) {
            log.warn("No translations taken from {}: missing, not a regular file, or empty", fastaFilePath);
            return;
        }

        boolean fastaSectionStartWritten = false;
        boolean keepingRecord = false;
        boolean unreadableHeaderLogged = false;
        int kept = 0;

        try (BufferedReader br = Files.newBufferedReader(fastaFilePath)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(">")) {
                    String key = line.substring(1).trim();
                    keepingRecord = TranslationKey.belongsToAny(key, accessions);
                    if (keepingRecord) {
                        kept++;
                    } else if (TranslationKey.accessionOf(key) == null && !unreadableHeaderLogged) {
                        log.warn("Ignoring records of {}: header {} is not accession|featureId", fastaFilePath, line);
                        unreadableHeaderLogged = true;
                    }
                }

                if (keepingRecord) {
                    if (!fastaSectionStartWritten) {
                        writer.write("##FASTA\n");
                        fastaSectionStartWritten = true;
                    }
                    writer.write(line);
                    writer.write("\n");
                }
            }
        }

        if (!fastaSectionStartWritten) {
            log.warn("No translations in {} belong to this document's accessions {}", fastaFilePath, accessions);
            return;
        }
        log.info("Written {} translation sequences from: {}", kept, fastaFilePath);
    }
}
