package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;

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

    private File file;
    private SequentialFastaFileReader reader;                 // owned here
    public List<FastaEntry> entriesArchive;
    private List<FastaEntryInternal> entriesInternal;

    public FastaFileService(){
        entriesArchive = new ArrayList<>();
        this.file = null;
    }

    // ---------------------------- queries ----------------------------

    public List<FastaEntry> getAllReadFastaEntries() {
        return new ArrayList<>();
    }

    public Optional<FastaEntry> getFasta(String submissionId) throws FastaFileException {
        return Optional.empty();
    }

    /**
     * Return a sequence slice for [fromBase..toBase] (1-based, inclusive) for the given ID.
     * Uses the cached index to translate bases -> bytes, then asks the reader to stream
     * ASCII bytes while skipping '\n' and '\r' on the fly.
     */
    public Optional<String> getSequenceRange(SequenceRangeOption option, String accessionId, long fromBase, long toBase) throws FastaFileException {
        ensureFileReaderOpen();
        //TODO
        return Optional.empty();
    }

    // ---------------------------- interactions with the reader ----------------------------

    public void openNewFile(File fastaFile) throws FastaFileException {
        ensureFileReaderClosed(); // if already open, close first
        this.file = Objects.requireNonNull(file, "file");
        this.entriesArchive.clear();
        try {
            reader = new SequentialFastaFileReader(fastaFile);
            var readEntries = reader.readAll();
            //TODO assign
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
        if (reader == null) throw new IllegalStateException("Service is not open. Call open() first.");
    }
}

