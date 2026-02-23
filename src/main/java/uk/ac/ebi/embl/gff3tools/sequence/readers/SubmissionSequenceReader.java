package uk.ac.ebi.embl.gff3tools.sequence.readers;

import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.exception.FastaAccessionAssignmentException;
import uk.ac.ebi.embl.gff3tools.sequence.RecordIdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.readers.headerutils.FastaHeader;

import java.io.Reader;
import java.util.List;
import java.util.Optional;

public interface SubmissionSequenceReader extends AutoCloseable {

    SubmissionType submissionType();

    /** Records in stable order (important for setAccessionIds on FASTA). */
    List<String> orderedRecordIds(RecordIdType idType);

    /** FASTA: maps accessionIds -> submissionIds by order. Plain sequence: validates single id, otherwise errors out. */
    void setAccessionIds(List<String> orderedAccessionIds) throws FastaAccessionAssignmentException;

    /** Optional because plain submissions may not have headers. */
    Optional<FastaHeader> getHeader(RecordIdType idType, String id);

    SequenceStats getStats(RecordIdType idType, String id);

    String getSequenceSlice(
            RecordIdType idType,
            String id,
            long fromBase,
            long toBase,
            SequenceRangeOption option
    ) throws Exception;

    Reader getSequenceSliceReader(
            RecordIdType idType,
            String id,
            long fromBase,
            long toBase,
            SequenceRangeOption option
    ) throws Exception;
}
