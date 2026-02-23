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

import java.io.File;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.readers.headerutils.FastaHeader;

public final class PlainSequenceSubmissionReader implements SubmissionSequenceReader {
    private final SequenceReader sequenceReader;
    private final String accessionId;
    private final FastaHeader header; // nullable

    public PlainSequenceSubmissionReader(File sequenceFile, String accessionId, FastaHeader optionalHeader)
            throws Exception {
        this.sequenceReader = new SequenceReader(sequenceFile);
        this.accessionId = Objects.requireNonNull(accessionId, "accessionId");
        this.header = optionalHeader; // may be null
    }

    @Override
    public SubmissionType submissionType() {
        return SubmissionType.PLAIN_SEQUENCE;
    }

    @Override
    public List<String> getOrderedIds(IdType idType) {
        // Only ACCESSION_ID makes sense here.
        return idType == IdType.ACCESSION_ID ? List.of(accessionId) : List.of(accessionId);
    }

    @Override
    public void setAccessionIds(List<String> orderedAccessionIds) {
        // “still must be able to be set” => accept it, but validate it matches the one record.
        if (orderedAccessionIds == null || orderedAccessionIds.size() != 1)
            throw new IllegalArgumentException("Plain sequence expects exactly 1 accessionId");
        if (!accessionId.equals(orderedAccessionIds.get(0)))
            throw new IllegalArgumentException(
                    "AccessionId mismatch. Expected " + accessionId + " got " + orderedAccessionIds.get(0));
    }

    @Override
    public Optional<FastaHeader> getHeader(IdType idType, String id) {
        validateId(idType, id);
        return Optional.ofNullable(header);
    }

    @Override
    public SequenceStats getStats(IdType idType, String id) {
        validateId(idType, id);
        var e = sequenceReader.getSequenceEntry();
        return new SequenceStats(
                e.getTotalBases(),
                e.getTotalBasesWithoutNBases(),
                e.getLeadingNsCount(),
                e.getTrailingNsCount(),
                e.getBaseCount());
    }

    @Override
    public String getSequenceSlice(IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception {
        validateId(idType, id);
        return sequenceReader.getSequenceSliceString(fromBase, toBase, option);
    }

    @Override
    public Reader getSequenceSliceReader(
            IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option) throws Exception {
        validateId(idType, id);
        return sequenceReader.getSequenceSliceReader(fromBase, toBase, option);
    }

    private void validateId(IdType idType, String id) {
        // treat submission id == accession id for plain submissions (simplest + least surprising)
        if (!accessionId.equals(id)) {
            throw new IllegalArgumentException("No record for " + idType + ":" + id);
        }
    }

    @Override
    public void close() throws Exception {
        sequenceReader.close();
    }
}
