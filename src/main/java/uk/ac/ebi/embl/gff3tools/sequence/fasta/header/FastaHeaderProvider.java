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
package uk.ac.ebi.embl.gff3tools.sequence.fasta.header;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.SeqIdVariants;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Composite that chains multiple {@link FastaHeaderSource} instances in priority order
 * and returns the first non-empty {@link FastaHeader} for a given seqId.
 *
 * <p>Sources are queried in registration order (highest priority first).
 *
 * <p>Registered on the validation engine context as a {@link ContextProvider} so consumers
 * can resolve it via {@code context.get(FastaHeaderProvider.class)}. Currently exposed for
 * downstream use; not consumed during conversion.
 */
public class FastaHeaderProvider implements ContextProvider<FastaHeaderProvider> {

    private final List<FastaHeaderSource> sources;

    public FastaHeaderProvider() {
        this.sources = new ArrayList<>();
    }

    @Override
    public FastaHeaderProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<FastaHeaderProvider> type() {
        return FastaHeaderProvider.class;
    }

    /**
     * A header provider with no sources can never resolve a header, so it is kept off the
     * context entirely. This stops header-aware rules (e.g. FASTA_HEADER_MAPPING) from firing
     * against accessions for which no header was ever supplied.
     */
    @Override
    public boolean isActive() {
        return hasSources();
    }

    /**
     * Registers a header source. Sources are queried in registration order.
     */
    public void addSource(FastaHeaderSource source) {
        this.sources.add(source);
    }

    /**
     * Returns {@code true} if at least one header source is registered. A provider with no sources
     * can never resolve a header, so callers may choose not to register it at all.
     */
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    /**
     * Returns the first non-empty header for the given seqId across all registered sources.
     *
     * <p>The seqId is resolved version-tolerantly in three passes (see {@link SeqIdVariants}):
     * first every source is asked for the seqId as given (declared source priority wins), then
     * for the seqId with its version stripped, and finally for the versioned forms "X.1" to
     * "X.9" — the last pass only resolves when exactly one versioned form matches across all
     * sources, mirroring the reader's handling of ambiguous sequence regions. Catch-all sources
     * (see {@link FastaHeaderSource#isCatchAll()}) are consulted last, after all keyed sources
     * have been asked in every pass.
     */
    public Optional<FastaHeader> getHeader(String seqId) {
        if (seqId == null) {
            return Optional.empty();
        }
        List<FastaHeaderSource> keyed = sources.stream().filter(s -> !s.isCatchAll()).toList();
        List<FastaHeaderSource> catchAlls = sources.stream().filter(FastaHeaderSource::isCatchAll).toList();

        // Pass 1: the seqId as given — declared source priority wins.
        for (FastaHeaderSource source : keyed) {
            Optional<FastaHeader> header = source.getHeader(seqId);
            if (header.isPresent()) {
                return header;
            }
        }
        // Pass 2: the seqId with its version stripped.
        Optional<String> stripped = SeqIdVariants.strippedVariant(seqId);
        if (stripped.isPresent()) {
            for (FastaHeaderSource source : keyed) {
                Optional<FastaHeader> header = source.getHeader(stripped.get());
                if (header.isPresent()) {
                    return header;
                }
            }
        }
        // Pass 3: exactly one of the versioned forms must match across all sources.
        List<FastaHeader> versionedHits = SeqIdVariants.appendedVariants(seqId).stream()
                .flatMap(candidate -> keyed.stream().map(source -> source.getHeader(candidate)))
                .flatMap(Optional::stream)
                .toList();
        if (versionedHits.size() == 1) {
            return Optional.of(versionedHits.get(0));
        }
        for (FastaHeaderSource source : catchAlls) {
            Optional<FastaHeader> header = source.getHeader(seqId);
            if (header.isPresent()) {
                return header;
            }
        }
        return Optional.empty();
    }
}
