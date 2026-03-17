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
 * <p>For {@link SubmissionType#PLAIN_SEQUENCE}, {@link #hasSequence} returns {@code true}
 * for any ID since the single sequence serves all GFF3 seqIds. For {@link SubmissionType#FASTA},
 * it checks whether the requested ID exists in the reader's index.
 */
public class FileSequenceProvider implements SequenceSource {

    private SequenceReader sequenceReader;

    public FileSequenceProvider() {}

    public FileSequenceProvider(SequenceReader sequenceReader) {
        this.sequenceReader = sequenceReader;
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
            return true;
        }
        // FASTA: check whether the ID exists
        return sequenceReader.getOrderedIds(idType).contains(id);
    }

    @Override
    public SequenceReader getReader() {
        return sequenceReader;
    }
}
