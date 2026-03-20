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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;

/**
 * A {@link SequenceSource} backed by a single file (FASTA or plain sequence).
 *
 * <p>For {@link SubmissionType#PLAIN_SEQUENCE}:
 * <ul>
 *   <li>If a {@code sequenceKey} is set, {@link #hasSequence} matches only that key.</li>
 *   <li>If no key is set, {@link #hasSequence} returns {@code true} for any ID
 *       (single sequence serves all GFF3 seqIds).</li>
 * </ul>
 *
 * <p>For {@link SubmissionType#FASTA}, it checks whether the requested ID exists
 * in the reader's index.
 */
public class FileSequenceProvider implements SequenceSource {

    private SequenceReader sequenceReader;
    private final String sequenceKey;

    public FileSequenceProvider() {
        this.sequenceKey = null;
    }

    public FileSequenceProvider(SequenceReader sequenceReader) {
        this.sequenceReader = sequenceReader;
        this.sequenceKey = null;
    }

    public FileSequenceProvider(SequenceReader sequenceReader, String sequenceKey) {
        this.sequenceReader = sequenceReader;
        this.sequenceKey = sequenceKey;
    }

    public void setSequenceReader(SequenceReader sequenceReader) {
        this.sequenceReader = sequenceReader;
    }

    @Override
    public boolean hasSequence(IdType idType, String id) {
        if (sequenceReader == null) {
            return false;
        }
        if (sequenceReader.submissionType() == SubmissionType.PLAIN_SEQUENCE) {
            // If a key is set, match only that key; otherwise match any ID
            return sequenceKey == null || sequenceKey.equals(id);
        }
        // FASTA: check whether the ID exists
        return sequenceReader.getOrderedIds(idType).contains(id);
    }

    @Override
    public SequenceReader getReader() {
        return sequenceReader;
    }
}
