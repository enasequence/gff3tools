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
package uk.ac.ebi.embl.gff3tools.metadata;

import java.util.Map;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

/**
 * An {@link AnnotationMetadataSource} backed by FASTA-embedded headers parsed from
 * sequence files. Returns metadata only for seqIds present in the header map.
 *
 * <p>Maps {@link FastaHeader} fields onto {@link AnnotationMetadata} fields by name.
 */
public class EmbeddedFastaMetadataSource implements AnnotationMetadataSource {

    private final Map<String, FastaHeader> seqIdToHeader;

    public EmbeddedFastaMetadataSource(Map<String, FastaHeader> seqIdToHeader) {
        this.seqIdToHeader = seqIdToHeader;
    }

    @Override
    public Optional<AnnotationMetadata> getMetadata(String seqId) {
        FastaHeader header = seqIdToHeader.get(seqId);
        if (header == null) {
            return Optional.empty();
        }
        return Optional.of(fromFastaHeader(header));
    }

    static AnnotationMetadata fromFastaHeader(FastaHeader header) {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setDescription(header.getDescription());
        meta.setMoleculeType(header.getMoleculeType());
        meta.setTopology(header.getTopology());
        meta.setChromosomeType(header.getChromosomeType());
        meta.setChromosomeLocation(header.getChromosomeLocation());
        meta.setChromosomeName(header.getChromosomeName());
        return meta;
    }
}
