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
package uk.ac.ebi.embl.gff3tools.sequence;

import java.io.Reader;
import java.util.List;
import java.util.Set;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;

/**
 * Interface for looking up nucleotide sequence data by GFF3 seqId.
 *
 * <p>Backed by the library's {@code SequenceFormatReader}; the string-to-ordinal
 * ID mapping is handled by the implementing provider layer.
 */
public interface SequenceLookup {

    /**
     * Returns a nucleotide subsequence for the given GFF3 sequence ID.
     *
     * @param seqId the GFF3 seqId (e.g. chromosome name)
     * @param fromBase 1-based start position (inclusive)
     * @param toBase 1-based end position (inclusive)
     * @param option controls which portion of the sequence is considered
     * @return the nucleotide string
     */
    String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option) throws Exception;

    /**
     * Total length of the sequence in bases.
     * The last base index should therefore be sequenceLength, as the bases are indexed 1...sequenceLength.
     *
     * @param option controls which portion of the sequence is considered
     */
    long getSequenceLength(String seqId, SequenceRangeOption option) throws Exception;

    /** Full stats for the sequence (base counts, N-counts, edge Ns). */
    SequenceStats getSequenceStats(String seqId) throws Exception;

    /**
     * All contiguous N-runs in the whole sequence.
     *
     * @param option controls which portion of the sequence is considered
     */
    List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) throws Exception;

    /**
     * N-runs overlapping [fromBase, toBase] (1-based inclusive).
     * Returned regions are not clipped to the range.
     *
     * @param option controls which portion of the sequence is considered
     */
    List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;

    /** All seqIds currently known to this lookup. */
    Set<String> knownSeqIds();

    /**
     * Returns {@code true} if this lookup can serve the given GFF3 seqId.
     *
     * <p>This is the authoritative "can serve" predicate — unlike {@link #knownSeqIds()}, it
     * correctly reports {@code true} for sources that match any seqId (e.g. a plain-sequence
     * source supplied without a key), which {@link #knownSeqIds()} cannot enumerate.
     */
    boolean hasSequence(String seqId);

    /**
     * Streaming reader over a sequence slice. Caller must close the returned Reader.
     *
     * @param option controls which portion of the sequence is considered
     */
    Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;
}
