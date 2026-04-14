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

import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

/**
 * An {@link AnnotationMetadataSource} backed by a CLI-supplied {@link FastaHeader} JSON file.
 * Returns the same metadata for any seqId query, acting as a global fallback.
 *
 * <p>Maps {@link FastaHeader} fields onto {@link AnnotationMetadata} fields by name.
 */
public class CliJsonMetadataSource implements AnnotationMetadataSource {

    private final AnnotationMetadata metadata;

    public CliJsonMetadataSource(FastaHeader header) {
        this.metadata = EmbeddedFastaMetadataSource.fromFastaHeader(header);
    }

    @Override
    public Optional<AnnotationMetadata> getMetadata(String seqId) {
        return Optional.of(metadata);
    }
}
