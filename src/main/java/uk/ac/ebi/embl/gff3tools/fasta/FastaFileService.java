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
package uk.ac.ebi.embl.gff3tools.fasta;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.ByteSpan;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndex;

/**
 * Owns a SequentialFastaEntryReader, keeps all entries + indexes in memory, supports ID renames,
 * and serves base-range slices by mapping (N..M bases) -> byte span via the cached SequenceIndex,
 * then asking the reader to stream bytes while skipping newlines.
 */
@Getter
@Setter
public final class FastaFileService {

    public List<FastaEntry> fastaEntries = new ArrayList<>();

    private HashMap<String, SequenceIndex> sequenceIndexes = new HashMap<>();
    private File file;
    private SequentialFastaFileReader reader; // owned here

    public FastaFileService() {
        this.file = null;
    }

    // ---------------------------- queries ----------------------------

    public Optional<FastaEntry> setAccessionId(String submissionId, String accessionId) throws FastaFileException {
        Optional<FastaEntry> target = fastaEntries.stream()
                .filter(entry -> entry.getSubmissionId().equals(submissionId))
                .findFirst();
        target.ifPresent(entry -> entry.setAccessionId(accessionId));
        return target;
    }

    public Optional<FastaEntry> getFastaWithSubmissionId(String submissionId) throws FastaFileException {
        return fastaEntries.stream()
                .filter(entry -> entry.getSubmissionId().equals(submissionId))
                .findFirst();
    }

    /** Return a sequence slice as a String (no EOLs) for [fromBase..toBase] inclusive. */
    public String getSequenceSliceString(SequenceRangeOption option, String submissionId, long fromBase, long toBase)
            throws FastaFileException {
        ensureFileReaderOpen();
        SequenceIndex index = sequenceIndexes.get(submissionId);
        if (index == null) {
            throw new FastaFileException("No sequence index found for submissionId " + submissionId);
        }

        final ByteSpan span;
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

        try {
            return reader.getSequenceSliceString(span);
        } catch (IOException ioe) {
            throw new FastaFileException(
                    "I/O while reading slice for " + submissionId + " bytes " + span.start + ".." + (span.endEx - 1),
                    ioe);
        }
    }

    /**
     * Return a sequence slice for reader [fromBase..toBase] (1-based, inclusive) for the given ID.
     * Uses the cached index to translate bases -> bytes, then asks the reader to stream
     * ASCII bytes while skipping '\n' and '\r' on the fly.
     */
    public Reader getSequenceSliceReader(SequenceRangeOption option, String submissionId, long fromBase, long toBase)
            throws FastaFileException {
        ensureFileReaderOpen();
        var index = sequenceIndexes.get(submissionId);
        if (index == null) {
            throw new FastaFileException("No sequence index found for submissionId " + submissionId);
        }

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

        return reader.getSequenceSliceReader(span);
    }

    // ---------------------------- interactions with the reader ----------------------------

    public void openNewFile(File fastaFile) throws FastaFileException, IOException {
        ensureFileReaderClosed(); // if already open, close first
        this.file = Objects.requireNonNull(fastaFile, "file");
        this.fastaEntries.clear();
        this.sequenceIndexes.clear();
        reader = new SequentialFastaFileReader(fastaFile);
        var readEntries = reader.readAll();
        for (var entry : readEntries) {
            FastaEntry fastaEntry = new FastaEntry();
            fastaEntry.setSubmissionId(entry.getSubmissionId());
            fastaEntry.setHeader(entry.getHeader());
            fastaEntry.setTotalBases(entry.sequenceIndex.totalBases());
            fastaEntry.setLeadingNsCount(entry.sequenceIndex.startNBasesCount);
            fastaEntry.setTrailingNsCount(entry.sequenceIndex.endNBasesCount);
            long adjustedBases = entry.sequenceIndex.totalBases()
                    - entry.sequenceIndex.startNBasesCount
                    - entry.sequenceIndex.endNBasesCount;
            fastaEntry.setTotalBasesWithoutNBases(adjustedBases);
            fastaEntries.add(fastaEntry);

            sequenceIndexes.put(entry.getSubmissionId(), entry.sequenceIndex);
        }
    }

    /** Close the reader. Safe to call multiple times. */
    public void close() throws IOException {
        if (reader != null) {
            reader.close();
            reader = null;
        }
    }

    private void ensureFileReaderClosed() throws IOException {
        if (reader != null) close();
    }

    private void ensureFileReaderOpen() {
        if (reader == null || !reader.readingFile())
            throw new IllegalStateException("Service is not open. Call open() first.");
    }
}
