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

import static uk.ac.ebi.embl.fasta.writer.FastaFileWriter.FastaHeaderFormat.TRANSLATION_HEADER_FORMAT;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.fasta.writer.FastaFileWriter;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.*;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.gff3toff.OffsetRange;

public class GFF3File implements IGFF3Feature {

    GFF3Header header;
    GFF3Species species;
    List<GFF3Annotation> annotations;
    Path fastaFilePath;
    List<ValidationException> parsingErrors;
    GFF3TranslationReader translationReader;
    Map<String, OffsetRange> annotationTranslationOffset;

    public GFF3File(
            GFF3Header header,
            GFF3Species species,
            List<GFF3Annotation> annotations,
            GFF3TranslationReader translationReader,
            Map<String, OffsetRange> annotationTranslationOffset,
            List<ValidationException> parsingErrors) {
        this.header = header;
        this.species = species;
        this.annotations = annotations;
        this.translationReader = translationReader;
        this.annotationTranslationOffset = annotationTranslationOffset;
        this.parsingErrors = parsingErrors;
    }

    public GFF3File(
            GFF3Header header,
            GFF3Species species,
            List<GFF3Annotation> annotations,
            Path fastaFilePath,
            List<ValidationException> parsingErrors) {
        this.header = header;
        this.species = species;
        this.annotations = annotations;
        this.fastaFilePath = fastaFilePath;
        this.parsingErrors = parsingErrors;
    }

    @Override
    public void writeGFF3String(Writer writer) throws WriteException {

        // try {
        if (header != null) {
            this.header.writeGFF3String(writer);
        }

        if (this.species != null) {
            this.species.writeGFF3String(writer);
        }
        for (GFF3Annotation annotation : annotations) {
            annotation.writeGFF3String(writer);
        }
        writeTranslation(writer);
    }

    public void writeTranslation(Writer writer) throws WriteException {
        try {
            if (fastaFilePath != null && Files.exists(fastaFilePath) && Files.size(fastaFilePath) > 0) {
                writer.write("##FASTA");
                writer.write('\n');

                try (BufferedReader reader = Files.newBufferedReader(fastaFilePath)) {
                    char[] buffer = new char[8192]; // optimal internal buffer
                    int read;
                    while ((read = reader.read(buffer)) != -1) {
                        writer.write(buffer, 0, read);
                    }
                }
                writer.flush();
            } else if (translationReader != null
                    && annotationTranslationOffset != null
                    && !annotationTranslationOffset.isEmpty()) {
                writer.write("##FASTA");
                writer.write('\n');

                for (String id : annotationTranslationOffset.keySet()) {
                    writeFasta(
                            writer,
                            id,
                            translationReader.readTranslation(
                                    translationReader.readTranslationOffset().get(id)));
                }
                writer.write('\n');
            }

        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    // To write all translation in the end of the GFF3 file
    public void writeTranslation(Writer writer, GFF3TranslationReader translationReader) throws WriteException {

        try {
            if (translationReader != null) {
                if (!translationReader.readTranslationOffset().isEmpty()) {
                    writer.write("##FASTA");
                    writer.write('\n');

                    for (String id : translationReader.readTranslationOffset().keySet()) {
                        String translation = translationReader.readTranslation(
                                translationReader.readTranslationOffset().get(id));
                        writeFasta(writer, id, translation);
                    }
                }
            }
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    private void writeFasta(Writer writer, String id, String translation) throws IOException {
        Entry fastaEntry = new EntryFactory().createEntry();
        fastaEntry.setPrimaryAccession(id);
        Sequence sequence = new SequenceFactory().createSequence();
        sequence.setSequence(ByteBuffer.wrap(translation.getBytes()));
        fastaEntry.setSequence(sequence);
        FastaFileWriter fastWriter = new FastaFileWriter(fastaEntry, writer, TRANSLATION_HEADER_FORMAT);
        fastWriter.write();
        writer.flush();
    }
}
