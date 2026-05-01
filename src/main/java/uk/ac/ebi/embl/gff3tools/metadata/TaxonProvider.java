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
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;

/**
 * Chain-of-responsibility provider that returns taxonomy data from the first
 * {@link TaxonSource} that has data for a given taxon ID.
 *
 * <p>The default provider has no sources and is offline-only. Network-backed sources
 * should be provided by environment-specific extensions and registered explicitly.
 */
public class TaxonProvider {

    private final List<TaxonSource> sources = new ArrayList<>();

    /**
     * Registers a taxon source. Sources are queried in registration order.
     */
    public void addSource(TaxonSource source) {
        sources.add(source);
    }

    /**
     * Returns the {@link Taxon} from the first source that has data for the given taxon ID.
     */
    public Optional<Taxon> getTaxonByTaxId(Long taxId) {
        if (taxId == null) {
            return Optional.empty();
        }
        for (TaxonSource source : sources) {
            Optional<Taxon> taxon = source.getTaxonByTaxId(taxId);
            if (taxon.isPresent()) {
                return taxon;
            }
        }
        return Optional.empty();
    }
}
