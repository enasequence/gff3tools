package uk.ac.ebi.embl.gff3tools.fasta;

import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Owns a SequentialFastaEntryReader, keeps all entries + indexes in memory, supports ID renames,
 * and serves base-range slices by mapping (N..M bases) -> byte span via the cached SequenceIndex,
 * then asking the reader to stream bytes while skipping newlines.
 */
public final class FastaFileService{

    private final File file;
    private SequentialFastaFileReader reader;                 // owned here

    private final List<FastaEntryInternal> entriesArchive;

    public FastaFileService(File file) throws FastaFileException {
        this.file = Objects.requireNonNull(file, "file");
        entriesArchive = new ArrayList<>();
    }

    // ---------------------------- queries ----------------------------

    public List<FastaEntryInternal> getAllReadFastaEntries() {
        return Collections.unmodifiableList(entriesArchive);
    }

    public Optional<FastaEntryInternal> getPreviouslyReadFasta(String accessionId) throws FastaFileException {
        return Optional.empty();
    }

    public Optional<FastaEntryInternal> getNewEntry(String newAccessionId) throws FastaFileException { //TODO it would be better if instead of getting the accessionId here, we can just call the accessionId generator service after (optionally) managing to read the entry
        return Optional.empty();
    }

    /**
     * Return a sequence slice for [fromBase..toBase] (1-based, inclusive) for the given ID.
     * Uses the cached index to translate bases -> bytes, then asks the reader to stream
     * ASCII bytes while skipping '\n' and '\r' on the fly.
     */
    public Optional<String> getSequenceRange(String accessionId, long fromBase, long toBase) throws FastaFileException {
        ensureFileReaderOpen();
        //TODO
        return Optional.empty();
    }

    // ---------------------------- interactions with the reader ----------------------------

    /** Open the underlying reader and scan all entries and indexes into memory. */
    public boolean readNewEntry (String accessionId) throws FastaFileException {
        return false;
    }

    private void open() throws FastaFileException {
        ensureFileReaderClosed(); // if already open, close first
        try {
            reader = new SequentialFastaFileReader(file);
        } catch (IOException ioe) {
            throw new FastaFileException("Failed to open FASTA reader: " + file.getAbsolutePath(), ioe);
        }
    }

    /** Close the reader. Safe to call multiple times. */
    private void close() throws FastaFileException {
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

