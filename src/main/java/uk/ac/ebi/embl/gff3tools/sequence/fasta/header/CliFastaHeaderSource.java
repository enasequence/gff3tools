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
 * A {@link FastaHeaderSource} backed by a single CLI-supplied {@link FastaHeader}.
 *
 * <p>Returns the same header for any seqId query, acting as a global fallback.
 */
public class CliFastaHeaderSource implements FastaHeaderSource {

    private final FastaHeader header;

    public CliFastaHeaderSource(FastaHeader header) {
        this.header = header;
    }

    @Override
    public Optional<FastaHeader> getHeader(String seqId) {
        return Optional.of(header);
    }
}
