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
 * An {@link MasterMetadataSource} backed by a MasterEntry JSON document deserialized
 * directly into {@link MasterMetadata}. Acts as a global fallback (returns the same
 * metadata for any seqId query).
 */
public class MasterEntryJsonMetadataSource implements MasterMetadataSource {

    private final MasterMetadata metadata;

    public MasterEntryJsonMetadataSource(MasterMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public Optional<MasterMetadata> getMetadata(String seqId) {
        return Optional.of(metadata);
    }
}
