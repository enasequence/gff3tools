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
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.SeqIdVariants;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * A {@link ContextProvider} that aggregates multiple {@link SequenceSource} instances
 * and exposes them as a single {@link SequenceLookup} using chain-of-responsibility delegation.
 *
 * <p>Sequence ids are resolved version-tolerantly across the sources (see
 * {@link SeqIdVariants}): sources are keyed by the FASTA submission id, which usually carries a
 * version, while callers query with either id form.
 *
 * <p>Returns {@code null} from {@link #get} when no sources have been added,
 * allowing downstream consumers (e.g. TranslationFix) to skip gracefully.
 */
public class CompositeSequenceProvider implements ContextProvider<SequenceLookup> {

    /** A call against a resolved source and id. */
    @FunctionalInterface
    private interface LookupCall<T> {
        T apply(SequenceSource source, String seqId) throws Exception;
    }

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

                /**
                 * Resolves the seqId version-tolerantly to the id form a source holds, in three
                 * passes (see {@link SeqIdVariants}): the seqId as given (declared source
                 * priority wins), the seqId with its version stripped, and finally the versioned
                 * forms "X.1" to "X.9" — the last pass only resolves when exactly one versioned
                 * form matches across all sources, mirroring the reader's handling of ambiguous
                 * sequence regions. Returns the seqId as given when no source holds any form, so
                 * the source produces its native error mentioning the requested id.
                 */
                private String resolve(String seqId) {
                    if (seqId == null) {
                        return null;
                    }
                    // Pass 1: the seqId as given — any source holding it wins.
                    if (sources.stream().anyMatch(source -> source.hasSequence(seqId))) {
                        return seqId;
                    }
                    // Pass 2: the seqId with its version stripped.
                    Optional<String> stripped = SeqIdVariants.strippedVariant(seqId);
                    if (stripped.isPresent()
                            && sources.stream().anyMatch(source -> source.hasSequence(stripped.get()))) {
                        return stripped.get();
                    }
                    // Pass 3: exactly one of the versioned forms must match across all sources.
                    List<String> versionedHits = SeqIdVariants.appendedVariants(seqId).stream()
                            .filter(candidate -> sources.stream().anyMatch(source -> source.hasSequence(candidate)))
                            .toList();
                    return versionedHits.size() == 1 ? versionedHits.get(0) : seqId;
                }

                private SequenceSource sourceFor(String seqId) {
                    String resolved = resolve(seqId);
                    for (SequenceSource source : sources) {
                        if (source.hasSequence(resolved)) return source;
                    }
                    throw new IllegalArgumentException("No sequence source found for seqId: " + seqId);
                }

                /** Resolves the seqId and delegates to the holding source with the resolved id. */
                private <T> T withSource(String seqId, LookupCall<T> call) throws Exception {
                    return call.apply(sourceFor(seqId), resolve(seqId));
                }

                @Override
                public String getSequenceSlice(String seqId, long fromBase, long toBase) throws Exception {
                    return withSource(
                            seqId,
                            (source, id) ->
                                    source.getSequenceSlice(id, fromBase, toBase, SequenceRangeOption.WHOLE_SEQUENCE));
                }

                @Override
                public long getSequenceLength(String seqId) throws Exception {
                    return withSource(
                            seqId, (source, id) -> source.getSequenceLength(id, SequenceRangeOption.WHOLE_SEQUENCE));
                }

                @Override
                public SequenceStats getSequenceStats(String seqId) throws Exception {
                    return withSource(seqId, SequenceSource::getSequenceStats);
                }

                @Override
                public List<GapRegion> getGapRegions(String seqId) throws Exception {
                    return withSource(
                            seqId, (source, id) -> source.getGapRegions(id, SequenceRangeOption.WHOLE_SEQUENCE));
                }

                @Override
                public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase) throws Exception {
                    return withSource(
                            seqId,
                            (source, id) ->
                                    source.getGapRegions(id, fromBase, toBase, SequenceRangeOption.WHOLE_SEQUENCE));
                }

                @Override
                public boolean hasSequence(String seqId) {
                    for (SequenceSource source : sources) {
                        if (source.hasSequence(seqId)) return true;
                    }
                    return false;
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
                    return withSource(
                            seqId,
                            (source, id) -> source.getSequenceSliceReader(
                                    id, fromBase, toBase, SequenceRangeOption.WHOLE_SEQUENCE));
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
