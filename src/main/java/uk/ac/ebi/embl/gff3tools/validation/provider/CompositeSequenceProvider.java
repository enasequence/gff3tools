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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * A {@link ContextProvider} that aggregates multiple {@link SequenceSource} instances
 * and exposes them as a single {@link SequenceLookup} using chain-of-responsibility delegation.
 *
 * <p>Returns {@code null} from {@link #get} when no sources have been added,
 * allowing downstream consumers (e.g. TranslationFix) to skip gracefully.
 */
public class CompositeSequenceProvider implements ContextProvider<SequenceLookup> {

    @Getter
    private final List<SequenceSource> sources = new ArrayList<>();

    private SequenceLookup cachedLookup;

    /**
     * Registers a sequence source. Sources are queried in registration order.
     */
    public void addSource(SequenceSource source) {
        Objects.requireNonNull(source, "source must not be null");
        this.sources.add(source);
        // Invalidate cached lookup so the new source is visible via get().
        this.cachedLookup = null;
    }

    /**
     * Returns {@code true} if at least one source has been registered.
     */
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public SequenceLookup get(ValidationContext context) {
        if (sources.isEmpty()) {
            return null;
        }
        if (cachedLookup == null) {
            cachedLookup = new SequenceLookup() {

                private SequenceSource sourceFor(String seqId) {
                    for (SequenceSource source : sources) {
                        if (source.hasSequence(seqId)) return source;
                    }
                    throw new IllegalArgumentException("No sequence source found for seqId: " + seqId);
                }

                @Override
                public String getSequenceSlice(String seqId, long fromBase, long toBase) throws Exception {
                    return sourceFor(seqId).getSequenceSlice(seqId, fromBase, toBase);
                }

                @Override
                public long getSequenceLength(String seqId) throws Exception {
                    return sourceFor(seqId).getSequenceLength(seqId);
                }

                @Override
                public SequenceStats getSequenceStats(String seqId) throws Exception {
                    return sourceFor(seqId).getSequenceStats(seqId);
                }

                @Override
                public List<GapRegion> getGapRegions(String seqId) throws Exception {
                    return sourceFor(seqId).getGapRegions(seqId);
                }

                @Override
                public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase) throws Exception {
                    return sourceFor(seqId).getGapRegions(seqId, fromBase, toBase);
                }

                @Override
                public Set<String> knownSeqIds() {
                    Set<String> all = new LinkedHashSet<>();
                    for (SequenceSource source : sources) {
                        all.addAll(source.knownSeqIds());
                    }
                    return Collections.unmodifiableSet(all);
                }

                @Override
                public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase) throws Exception {
                    return sourceFor(seqId).getSequenceSliceReader(seqId, fromBase, toBase);
                }
            };
        }
        return cachedLookup;
    }

    @Override
    public Class<SequenceLookup> type() {
        return SequenceLookup.class;
    }

    @Override
    public void close() {
        for (SequenceSource source : sources) {
            source.close();
        }
    }
}
