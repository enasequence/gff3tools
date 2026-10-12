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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.SeqIdVariants;

/**
 * A {@link FastaHeaderSource} backed by the parsed header map from a FASTA file.
 *
 * <p>Returns headers only for seqIds found in the FASTA file; the seqId is resolved
 * version-tolerantly (see {@link SeqIdVariants}), because FASTA headers are usually keyed by the
 * versioned submission id while the mapper and validators query with either id form.
 */
public class FileFastaHeaderSource implements FastaHeaderSource {

    private final Map<String, FastaHeader> seqIdToHeader;

    public FileFastaHeaderSource(Map<String, FastaHeader> seqIdToHeader) {
        this.seqIdToHeader = seqIdToHeader;
    }

    @Override
    public Optional<FastaHeader> getHeader(String seqId) {
        return SeqIdVariants.variants(seqId).stream()
                .map(seqIdToHeader::get)
                .filter(Objects::nonNull)
                .findFirst();
    }
}
