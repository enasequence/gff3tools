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

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
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

import static uk.ac.ebi.embl.fasta.writer.FastaFileWriter.FastaHeaderFormat.TRANSLATION_HEADER_FORMAT;

public record GFF3File(
        GFF3Header header,
        GFF3Species species,
        List<GFF3Annotation> annotations,
        Map<String, String> cdsTranslationMap,
        List<ValidationException> parsingErrors
        )
        implements IGFF3Feature {
    @Override
    public void writeGFF3String(Writer writer) throws WriteException {

        try {
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
        } catch (IOException e) {
            throw new WriteException(e);
        }
    }

    private void  writeTranslation(Writer writer) throws IOException {
        if(!cdsTranslationMap.isEmpty()) {
            writer.write("##FASTA");
            writer.write("\n");
            for (Map.Entry<String, String> entry : cdsTranslationMap.entrySet()) {
                String featureId = entry.getKey();
                String translation = entry.getValue();

                // Write using FastaWriter
                Entry fastaEntry = new EntryFactory().createEntry();
                fastaEntry.setPrimaryAccession(featureId);
                Sequence sequence = new SequenceFactory().createSequence();
                sequence.setSequence(ByteBuffer.wrap(translation.getBytes()));
                fastaEntry.setSequence(sequence);
                FastaFileWriter fasteWriter = new FastaFileWriter(fastaEntry, writer, TRANSLATION_HEADER_FORMAT);
                fasteWriter.write();
            }
        }
    }
}
