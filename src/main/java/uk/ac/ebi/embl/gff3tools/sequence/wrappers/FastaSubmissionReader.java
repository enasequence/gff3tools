package uk.ac.ebi.embl.gff3tools.sequence.wrappers;

import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.fasta.IdType;
import uk.ac.ebi.embl.gff3tools.fasta.JsonHeaderFastaReader;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.sequence.RecordIdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.SubmissionSequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.SubmissionType;

import java.io.File;
import java.io.Reader;
import java.util.List;
import java.util.Optional;

public final class FastaSubmissionReader implements SubmissionSequenceReader {
    private final JsonHeaderFastaReader delegate;

    public FastaSubmissionReader(File fastaFile) throws Exception {
        this.delegate = new JsonHeaderFastaReader(fastaFile);
    }

    @Override public SubmissionType submissionType() { return SubmissionType.FASTA; }

    @Override
    public List<String> orderedRecordIds(RecordIdType idType) {
        // JsonHeaderFastaReader currently stores orderedSubmissionIds internally but doesn't expose it.
        // You should add a method there: List<String> getOrderedSubmissionIds()
        // Then for ACCESSION_ID, you map those via submissionIdToAccessionId if set.
        throw new UnsupportedOperationException("Expose ordered ids in JsonHeaderFastaReader and implement this.");
    }

    @Override
    public void setAccessionIds(List<String> orderedAccessionIds) {
        try {
            delegate.setAccessionIds(orderedAccessionIds);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<FastaHeader> getHeader(RecordIdType idType, String id) {
        var h = delegate.getFastaHeader(
                idType == RecordIdType.ACCESSION_ID ? IdType.ACCESSIONID : IdType.SUBMITTERID,
                id
        );
        return Optional.ofNullable(h);
    }

    @Override
    public SequenceStats getStats(RecordIdType idType, String id) {
        var e = delegate.getFastaEntry(
                idType == RecordIdType.ACCESSION_ID ? IdType.ACCESSIONID : IdType.SUBMITTERID,
                id
        );
        if (e == null) throw new IllegalArgumentException("No record for " + idType + ":" + id);

        return new SequenceStats(
                e.totalBases,
                e.totalBasesWithoutNBases,
                e.leadingNsCount,
                e.trailingNsCount,
                e.baseCount
        );
    }

    @Override
    public String getSequenceSlice(RecordIdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception {
        return delegate.getSequenceSlice(
                idType == RecordIdType.ACCESSION_ID ? IdType.ACCESSIONID : IdType.SUBMITTERID,
                id,
                fromBase,
                toBase,
                option
        );
    }

    @Override
    public Reader getSequenceSliceReader(RecordIdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception {
        return delegate.getSequenceSliceReader(
                idType == RecordIdType.ACCESSION_ID ? IdType.ACCESSIONID : IdType.SUBMITTERID,
                id,
                fromBase,
                toBase,
                option
        );
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
