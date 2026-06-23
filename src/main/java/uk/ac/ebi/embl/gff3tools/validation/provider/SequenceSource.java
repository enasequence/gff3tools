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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import java.io.Reader;
import java.util.List;
import java.util.Set;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;

/**
 * A source of nucleotide sequences that can be queried by GFF3 seqId.
 *
 * <p>Used by {@link CompositeSequenceProvider} to chain multiple sequence sources
 * (local files, plugins, etc.) in a chain-of-responsibility pattern.
 */
public interface SequenceSource {

    boolean hasSequence(String seqId);

    String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option) throws Exception;

    long getSequenceLength(String seqId, SequenceRangeOption option) throws Exception;

    SequenceStats getSequenceStats(String seqId) throws Exception;

    List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) throws Exception;

    List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;

    Set<String> knownSeqIds();

    Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;

    /** Release any resources held by this source. */
    default void close() {}
}
