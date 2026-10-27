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

import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

/**
 * A source of {@link FastaHeader} metadata for a given sequence ID.
 *
 * <p>Used by {@link FastaHeaderProvider} to chain multiple sources
 * in priority order (chain-of-responsibility pattern).
 */
public interface FastaHeaderSource {

    /**
     * Returns the {@link FastaHeader} for the given seqId, or empty if not available.
     */
    Optional<FastaHeader> getHeader(String seqId);

    /**
     * Returns {@code true} when this source can answer for any seqId, e.g. a single CLI-supplied
     * header that acts as a global fallback. Catch-all sources are only consulted after every
     * keyed source has been asked, so a header keyed by a versioned id in a FASTA file is not
     * shadowed by the fallback.
     */
    default boolean isCatchAll() {
        return false;
    }
}
