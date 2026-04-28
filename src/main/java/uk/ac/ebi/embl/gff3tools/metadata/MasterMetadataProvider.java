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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Chain-of-responsibility provider that returns {@link MasterMetadata} from the first
 * {@link MasterMetadataSource} that has data for a given seqId. Sources are queried in
 * registration order (first registered = highest priority). No field-level merging is performed;
 * the first source that returns a non-empty result wins entirely.
 *
 * <p>Registered on the validation engine context as a {@link ContextProvider} so converters
 * and mappers can resolve it via {@code context.get(MasterMetadataProvider.class)}.
 */
public class MasterMetadataProvider implements ContextProvider<MasterMetadataProvider> {

    private final List<MasterMetadataSource> sources;

    public MasterMetadataProvider() {
        this.sources = new ArrayList<>();
    }

    @Override
    public MasterMetadataProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<MasterMetadataProvider> type() {
        return MasterMetadataProvider.class;
    }

    /**
     * Registers a metadata source. Sources are queried in registration order
     * (first registered = highest priority).
     */
    public void addSource(MasterMetadataSource source) {
        this.sources.add(source);
    }

    /**
     * Returns metadata without a specific seqId context. Useful for the EMBL-to-GFF3 direction
     * where metadata is needed before any per-sequence processing. Queries sources with an
     * empty seqId; global sources (MasterEntry, CLI JSON) will respond, per-seqId sources
     * (FASTA-embedded) will not.
     */
    public Optional<MasterMetadata> getGlobalMetadata() {
        return getMetadata("");
    }

    /**
     * Returns the {@link MasterMetadata} from the first source that has data for the
     * given seqId. Sources are queried in registration order; the first non-empty result wins.
     */
    public Optional<MasterMetadata> getMetadata(String seqId) {
        for (MasterMetadataSource source : sources) {
            Optional<MasterMetadata> opt = source.getMetadata(seqId);
            if (opt.isPresent()) {
                return opt;
            }
        }
        return Optional.empty();
    }
}
