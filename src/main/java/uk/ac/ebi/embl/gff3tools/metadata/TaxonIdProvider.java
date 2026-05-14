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
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Exposes an optional NCBI taxon ID supplied by the caller (e.g. via {@code --taxon-id})
 * to taxonomy-aware validation rules.
 *
 * <p>Auto-registered with no value via classpath scanning; the CLI overrides this with a
 * populated instance when {@code --taxon-id} is provided.
 */
public class TaxonIdProvider implements ContextProvider<TaxonIdProvider> {

    private final Long taxonId;

    public TaxonIdProvider() {
        this(null);
    }

    public TaxonIdProvider(Long taxonId) {
        this.taxonId = taxonId;
    }

    public Optional<Long> getTaxonId() {
        return Optional.ofNullable(taxonId);
    }

    @Override
    public TaxonIdProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<TaxonIdProvider> type() {
        return TaxonIdProvider.class;
    }
}
