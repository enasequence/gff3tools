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
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

/**
 * SPI for resolving NCBI taxon IDs to {@link Taxon} objects.
 *
 * <p>Core ships no implementation (offline by default). Online-backed implementations
 * are registered by downstream modules (e.g. gff3-validations) or by the processing /
 * ingestion pipeline at initialization time.
 */
public interface TaxonProvider extends ContextProvider<TaxonProvider> {

    /**
     * Returns the {@link Taxon} for the given taxon ID, or empty if not available.
     */
    Optional<Taxon> resolve(long taxId);

    @Override
    default Class<TaxonProvider> type() {
        return TaxonProvider.class;
    }
}
