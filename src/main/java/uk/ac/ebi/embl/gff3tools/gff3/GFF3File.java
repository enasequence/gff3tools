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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.gff3.reader.OffsetRange;
import uk.ac.ebi.embl.gff3tools.gff3.writer.TranslationWriter;

@Slf4j
public class GFF3File implements IGFF3Feature {

    GFF3Header header;
    GFF3Species species;
    List<GFF3Annotation> annotations;
    Path fastaFilePath;
    List<ValidationException> parsingErrors;
    GFF3TranslationReader translationReader;
    Map<String, OffsetRange> translationOffsets;

    private static final EntryFactory ENTRY_FACTORY = new EntryFactory();
    private static final SequenceFactory SEQ_FACTORY = new SequenceFactory();

    public GFF3File(
            GFF3Header header,
            GFF3Species species,
            List<GFF3Annotation> annotations,
            GFF3TranslationReader translationReader,
            Map<String, OffsetRange> translationOffsets,
            Path fastaFilePath,
            List<ValidationException> parsingErrors) {

        this.header = header;
        this.species = species;
        this.annotations = annotations;
        this.translationReader = translationReader;
        this.translationOffsets = translationOffsets;
        this.fastaFilePath = fastaFilePath;
        this.parsingErrors = parsingErrors;
    }

    @Override
    public void writeGFF3String(Writer writer) throws WriteException {

        try {
            if (header != null) header.writeGFF3String(writer);
            if (species != null) species.writeGFF3String(writer);

            for (GFF3Annotation ann : annotations) {
                ann.writeGFF3String(writer);
            }

            writeTranslationSection(writer);

        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    private void writeTranslationSection(Writer writer) throws IOException {
        if (fastaFilePath != null) {
            // Write translation from FASTA file to GFF3 file
            writeFastaFromExistingFile(writer);
        } else if (translationReader != null && translationOffsets != null && !translationOffsets.isEmpty()) {
            // Write translation bu offset
            writeFastaFromOffsets(writer);
        }
    }

    private void writeFastaFromExistingFile(Writer writer) throws IOException {

        BasicFileAttributes attrs = Files.readAttributes(fastaFilePath, BasicFileAttributes.class);

        if (!attrs.isRegularFile() || attrs.size() == 0) return;

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

    private void writeFastaFromOffsets(Writer writer) throws IOException {

        writer.write("##FASTA\n");

        for (Map.Entry<String, OffsetRange> entry : translationOffsets.entrySet()) {
            String id = entry.getKey();
            OffsetRange range = entry.getValue();

            String translation = translationReader.readTranslation(range);
            TranslationWriter.writeTranslation(writer, id, translation);
        }
        log.info("Written {} sequences from: ", translationOffsets.entrySet().size());
        writer.write("\n");
    }

    public static class Builder {
        private GFF3Header header;
        private GFF3Species species;
        private List<GFF3Annotation> annotations = new ArrayList<>();
        private GFF3TranslationReader translationReader;
        private Map<String, OffsetRange> translationOffsets = new HashMap<>();
        private Path fastaFilePath;
        private List<ValidationException> parsingErrors = new ArrayList<>();

        public Builder header(GFF3Header header) {
            this.header = header;
            return this;
        }

        public Builder species(GFF3Species species) {
            this.species = species;
            return this;
        }

        public Builder annotations(List<GFF3Annotation> annotations) {
            this.annotations = annotations;
            return this;
        }

        public Builder translationReader(GFF3TranslationReader reader) {
            this.translationReader = reader;
            return this;
        }

        public Builder translationOffsets(Map<String, OffsetRange> offsets) {
            this.translationOffsets = offsets;
            return this;
        }

        public Builder fastaFilePath(Path path) {
            this.fastaFilePath = path;
            return this;
        }

        public Builder parsingErrors(List<ValidationException> errors) {
            this.parsingErrors = errors;
            return this;
        }

        public GFF3File build() {
            return new GFF3File(
                    this.header,
                    this.species,
                    this.annotations,
                    this.translationReader,
                    this.translationOffsets,
                    this.fastaFilePath,
                    this.parsingErrors);
        }
    }
}
