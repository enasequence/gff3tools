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
package uk.ac.ebi.embl.gff3tools.sequence.readers;

import java.io.Reader;
import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.readers.headerutils.FastaHeader;

public interface SubmissionSequenceReader extends AutoCloseable {

    SubmissionType submissionType();

    /** Records in stable order (important for setAccessionIds on FASTA). */
    List<String> getOrderedIds(IdType idType);

    /** FASTA: maps accessionIds -> submissionIds by order. Plain sequence: validates single id, otherwise errors out. */
    void setAccessionIds(List<String> orderedAccessionIds) throws Exception;

    /** Optional because plain submissions may not have headers. */
    Optional<FastaHeader> getHeader(IdType idType, String id);

    SequenceStats getStats(IdType idType, String id);

    String getSequenceSlice(IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;

    Reader getSequenceSliceReader(IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;
}
