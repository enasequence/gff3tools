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

/**
 * A source of {@link AnnotationMetadata} for a given sequence ID.
 *
 * <p>Used by {@link AnnotationMetadataProvider} to chain multiple sources
 * in priority order with field-level merging.
 */
public interface AnnotationMetadataSource {

    /**
     * Returns the {@link AnnotationMetadata} for the given seqId, or empty if not available.
     */
    Optional<AnnotationMetadata> getMetadata(String seqId);
}
