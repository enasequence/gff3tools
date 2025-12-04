package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.ByteSpan;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndex;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Owns a SequentialFastaEntryReader, keeps all entries + indexes in memory, supports ID renames,
 * and serves base-range slices by mapping (N..M bases) -> byte span via the cached SequenceIndex,
 * then asking the reader to stream bytes while skipping newlines.
 */
@Getter
@Setter
public final class FastaFileService{

    public List<FastaEntry> fastaEntries;
    private HashMap<String, SequenceIndex> sequenceIndexes;
    private File file;
    private SequentialFastaFileReader reader;                 // owned here

    public FastaFileService(){
        fastaEntries = new ArrayList<>();
        this.file = null;
    }

    // ---------------------------- queries ----------------------------

    public Optional<FastaEntry> setAccessionId(String submissionId, String accessionId) throws FastaFileException {
        Optional<FastaEntry> target = fastaEntries.stream().filter(entry -> entry.getSubmissionId().equals(submissionId)).findFirst();
        target.ifPresent(entry -> entry.setAccessionId(accessionId));
        return target;
    }

    public Optional<FastaEntry> getFasta(String submissionId) throws FastaFileException {
        return fastaEntries.stream().filter(entry -> entry.getSubmissionId().equals(submissionId)).findFirst();
    }

    /**
     * Return a sequence slice for [fromBase..toBase] (1-based, inclusive) for the given ID.
     * Uses the cached index to translate bases -> bytes, then asks the reader to stream
     * ASCII bytes while skipping '\n' and '\r' on the fly.
     */
    public String getSequenceRange(SequenceRangeOption option, String submissionId, long fromBase, long toBase) throws FastaFileException {
        ensureFileReaderOpen();
        var index = sequenceIndexes.get(submissionId);
        if (index == null) { throw new FastaFileException("No sequence index found for submissionId " + submissionId); }

        ByteSpan span;
        switch (option) {
            case WHOLE_SEQUENCE:
                span = index.byteSpanForBaseRangeIncludingEdgeNBases(fromBase, toBase);
                break;
            case WITHOUT_N_BASES:
                span = index.byteSpanForBaseRange(fromBase, toBase);
                break;
            default:
                throw new IllegalStateException("Unknown option " + option);
        }

        var result = reader.getSequenceSlice(span);
        return result;
    }

    // ---------------------------- interactions with the reader ----------------------------

    public void openNewFile(File fastaFile) throws FastaFileException {
        ensureFileReaderClosed(); // if already open, close first
        this.file = Objects.requireNonNull(file, "file");
        this.fastaEntries.clear();
        this.sequenceIndexes.clear();
        try {
            reader = new SequentialFastaFileReader(fastaFile);
            var readEntries = reader.readAll();
            for (var entry : readEntries) {
                FastaEntry fastaEntry = new FastaEntry();
                fastaEntry.setSubmissionId(entry.getSubmissionId());
                fastaEntry.setHeader(entry.getHeader());
                fastaEntry.setTotalBases(entry.sequenceIndex.totalBases());
                fastaEntry.setStartCountNs(entry.sequenceIndex.startNBasesCount);
                fastaEntry.setEndCountNs(entry.sequenceIndex.endNBasesCount);
                fastaEntries.add(fastaEntry);

                sequenceIndexes.put(entry.getSubmissionId(), entry.sequenceIndex);
            }
        } catch (IOException ioe) {
            throw new FastaFileException("Failed to open FASTA reader: " + file.getAbsolutePath(), ioe);
        }
    }

    /** Close the reader. Safe to call multiple times. */
    public void close() throws FastaFileException {
        if (reader != null) {
            try { reader.close(); }
            catch (IOException ioe) {
                throw new FastaFileException("Failed to close FASTA reader: " + file.getAbsolutePath(), ioe);
            }
            reader = null;
        }
    }

    private void ensureFileReaderClosed() throws FastaFileException {
        if (reader != null) close();
    }

    private void ensureFileReaderOpen() {
        if (reader == null || !reader.readingFile()) throw new IllegalStateException("Service is not open. Call open() first.");
    }
}

