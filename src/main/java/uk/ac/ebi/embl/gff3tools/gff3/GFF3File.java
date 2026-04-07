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
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
import uk.ac.ebi.embl.gff3tools.gff3.writer.TranslationWriter;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

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

                if (writeAnnotationFasta) {
                    Map<String, OffsetRange> annOffserMap = gff3Reader.getTranslationOffsetForAnnotation(ann);
                    // Write translation by annnotation offset map
                    writeFastaFromOffsets(writer, annOffserMap);
                }
            }

            if (!writeAnnotationFasta) {
                writeTranslationSection(writer);
            }
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    private void writeTranslationSection(Writer writer) throws IOException {
        if (translationState != null) {
            writeFastaFromTranslationState(writer);
        } else if (fastaFilePath != null) {
            writeFastaFromExistingFile(writer);
        } else if (gff3Reader != null
                && gff3Reader.getTranslationOffsetMap() != null
                && !gff3Reader.getTranslationOffsetMap().isEmpty()) {
            writeFastaFromOffsets(writer, gff3Reader.getTranslationOffsetMap());
        }
    }

    private void writeFastaFromTranslationState(Writer writer) throws IOException {
        List<Map.Entry<String, String>> toWrite = new java.util.ArrayList<>();
        translationState.forEach((key, entry) -> {
            // Prefer new translation; fall back to old (e.g. FF→GFF3 path where no
            // re-translation occurs because no sequence source is available).
            String translation = entry.newTranslation();
            if (translation == null || translation.isEmpty()) {
                translation = entry.oldTranslation();
            }
            if (translation != null && !translation.isEmpty()) {
                toWrite.add(Map.entry(key, translation));
            }
        });

        if (toWrite.isEmpty()) {
            return;
        }

        writer.write("##FASTA\n");
        for (Map.Entry<String, String> e : toWrite) {
            TranslationWriter.writeTranslation(writer, e.getKey(), e.getValue());
        }
        log.info("Written {} translation sequences from TranslationState", toWrite.size());
        writer.write("\n");
    }

    private void writeFastaFromExistingFile(Writer writer) throws IOException {

        BasicFileAttributes attrs = Files.readAttributes(fastaFilePath, BasicFileAttributes.class);

        if (!attrs.isRegularFile() || attrs.size() == 0) {
            return;
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
