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
package uk.ac.ebi.embl.gff3tools.gff3.writer;

import static uk.ac.ebi.embl.fasta.writer.FastaFileWriter.FastaHeaderFormat.TRANSLATION_HEADER_FORMAT;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.embl.fasta.writer.FastaFileWriter;

public class TranslationWriter {

    private static final EntryFactory ENTRY_FACTORY = new EntryFactory();
    private static final SequenceFactory SEQ_FACTORY = new SequenceFactory();

    public static String getTranslationKey(String accession, String featureId) {
        return String.format("%s|%s", accession, featureId);
    }

    public static void writeTranslation(Writer writer, String featureId, String translation) {

        if (writer != null && !translation.isEmpty()) {
            try {
                // Write using FastaWriter
                Entry fastaEntry = ENTRY_FACTORY.createEntry();
                fastaEntry.setPrimaryAccession(featureId);
                Sequence sequence = SEQ_FACTORY.createSequence();
                sequence.setSequence(ByteBuffer.wrap(translation.getBytes()));
                fastaEntry.setSequence(sequence);
                FastaFileWriter fastWriter = new FastaFileWriter(fastaEntry, writer, TRANSLATION_HEADER_FORMAT);
                fastWriter.write();

            } catch (IOException e) {
                new RuntimeException(e);
            }
        }
    }
}
