package uk.ac.ebi.embl.gff3tools.sequence.wrappers;

import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.headerutils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.sequence.RecordIdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;

import java.io.File;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlainSequenceSubmissionReader implements SubmissionSequenceReader {
    private final SequenceReader sequenceReader;
    private final String accessionId;
    private final FastaHeader header; // nullable

    public PlainSequenceSubmissionReader(File sequenceFile, String accessionId, FastaHeader optionalHeader) throws Exception {
        this.sequenceReader = new SequenceReader(sequenceFile);
        this.accessionId = Objects.requireNonNull(accessionId, "accessionId");
        this.header = optionalHeader; // may be null
    }

    @Override public SubmissionType submissionType() { return SubmissionType.PLAIN_SEQUENCE; }

    @Override
    public List<String> orderedRecordIds(RecordIdType idType) {
        // Only ACCESSION_ID makes sense here.
        return idType == RecordIdType.ACCESSION_ID ? List.of(accessionId) : List.of(accessionId);
    }

    @Override
    public void setAccessionIds(List<String> orderedAccessionIds) {
        // “still must be able to be set” => accept it, but validate it matches the one record.
        if (orderedAccessionIds == null || orderedAccessionIds.size() != 1)
            throw new IllegalArgumentException("Plain sequence expects exactly 1 accessionId");
        if (!accessionId.equals(orderedAccessionIds.get(0)))
            throw new IllegalArgumentException("AccessionId mismatch. Expected " + accessionId + " got " + orderedAccessionIds.get(0));
    }

    @Override
    public Optional<FastaHeader> getHeader(RecordIdType idType, String id) {
        validateId(idType, id);
        return Optional.ofNullable(header);
    }

    @Override
    public SequenceStats getStats(RecordIdType idType, String id) {
        validateId(idType, id);
        var e = sequenceReader.getSequenceEntry();
        return new SequenceStats(
                e.getTotalBases(),
                e.getTotalBasesWithoutNBases(),
                e.getLeadingNsCount(),
                e.getTrailingNsCount(),
                e.getBaseCount()
        );
    }

    @Override
    public String getSequenceSlice(RecordIdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception {
        validateId(idType, id);
        return sequenceReader.getSequenceSliceString(fromBase, toBase, option);
    }

    @Override
    public Reader getSequenceSliceReader(RecordIdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception {
        validateId(idType, id);
        return sequenceReader.getSequenceSliceReader(fromBase, toBase, option);
    }

    private void validateId(RecordIdType idType, String id) {
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
