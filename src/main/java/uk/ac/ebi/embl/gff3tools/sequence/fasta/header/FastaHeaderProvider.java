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

/**
 * Composite that chains multiple {@link FastaHeaderSource} instances in priority order
 * and returns the first non-empty {@link FastaHeader} for a given seqId.
 *
 * <p>Sources are queried in registration order (highest priority first).
 */
public class FastaHeaderProvider {

    private final List<FastaHeaderSource> sources;

    public FastaHeaderProvider() {
        this.sources = new ArrayList<>();
    }

    /**
     * Registers a header source. Sources are queried in registration order.
     */
    public void addSource(FastaHeaderSource source) {
        this.sources.add(source);
    }

    /**
     * Returns the first non-empty header for the given seqId across all registered sources.
     */
    public Optional<FastaHeader> getHeader(String seqId) {
        for (FastaHeaderSource source : sources) {
            Optional<FastaHeader> header = source.getHeader(seqId);
            if (header.isPresent()) {
                return header;
            }
        }
        return Optional.empty();
    }
}
