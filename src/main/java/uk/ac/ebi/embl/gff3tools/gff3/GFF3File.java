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
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
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
     * @param gff3FileReader reader over the source GFF3; its {@code ##FASTA} is copied only when
     *     {@code translationState} is null. May be null
     * @param fastaFilePath a translation FASTA, used only when the other sources give nothing for
     *     this file. May be null
     * @param writeAnnotationFasta whether to write translations at all; when false no
     *     {@code ##FASTA} section is written even if a source could supply one
     * @param parsingWarnings warnings collected while parsing the source; carried for the caller,
     *     never written to the document
     * @param translationState translations from validation (submitted and generated). When set,
     *     these are the only translations written. May be null
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
     * Writes the document: header, species, annotations, then one {@code ##FASTA} section.
     *
     * <p>Copying the source {@code ##FASTA} reads the source file. A read failure is thrown as a
     * {@link WriteException} with the {@link ReadException} as its cause.
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
        } catch (ReadException e) {
            throw new WriteException(e);
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    /**
     * Writes the translations of this file's accessions, from the first source that has any:
     * <ol>
     *   <li>{@code translationState}, when set. Validation already holds every submitted and
     *       generated translation, so the source file is not read again;
     *   <li>the source GFF3's {@code ##FASTA}, when there is no state (e.g. a read-only engine);
     *   <li>{@code fastaFilePath}.
     * </ol>
     */
    private void writeTranslationSection(Writer writer) throws IOException, ReadException {
        Set<String> accessions =
                annotations.stream().map(GFF3Annotation::getAccession).collect(Collectors.toSet());

        // fallback: if there is no translation state, read from gff3 ##FASTA section
        Map<String, String> translations = translationState != null
                ? getTranslationsFromTranslationState(accessions)
                : getTranslationsFromSourceFasta();

        // fallback: if there is no translation state or ##FASTA, read from additional .fasta file
        if (translations.isEmpty()) {
            writeFastaFromExistingFile(writer, accessions);
            return;
        }

        writeFastaFromMap(writer, translations);
    }

    /** The source GFF3's {@code ##FASTA} records for this file's annotations, in their order. */
    private Map<String, String> getTranslationsFromSourceFasta() throws ReadException {
        Map<String, String> translations = new LinkedHashMap<>();
        if (gff3Reader == null) {
            return translations;
        }
        for (GFF3Annotation annotation : annotations) {
            for (Map.Entry<String, Long> record :
                    gff3Reader.getTranslationOffsetForAnnotation(annotation).entrySet()) {
                translations.put(record.getKey(), gff3Reader.getTranslation(record.getValue()));
            }
        }
        return translations;
    }

    /** The resolved translations in {@code TranslationState} for these accessions, in recording order. */
    private Map<String, String> getTranslationsFromTranslationState(Set<String> accessions) {
        Map<String, String> translations = new LinkedHashMap<>();
        translationState.forEachResolved((key, translation) -> {
            if (TranslationKey.belongsToAny(key, accessions)) {
                translations.put(key, translation);
            }
        });
        return translations;
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
     * Copies the records of {@code fastaFilePath} whose {@code >accession|featureId} header names
     * one of these accessions. Other records are dropped and logged. A missing or empty file writes
     * no {@code ##FASTA} section.
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
