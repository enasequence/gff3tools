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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

class TaxonProviderTest {

    private final TaxonFactory taxonFactory = new TaxonFactory();

    @Test
    void emptyProviderReturnsEmpty() {
        TaxonProvider provider = new TaxonProvider();

        assertTrue(provider.getTaxonByTaxId(12345L).isEmpty());
        assertTrue(provider.getTaxonByTaxId(null).isEmpty());
    }

    @Test
    void returnsFirstTaxonFromRegisteredSources() {
        Taxon first = taxonFactory.createTaxon();
        first.setTaxId(1L);
        Taxon second = taxonFactory.createTaxon();
        second.setTaxId(2L);

        TaxonProvider provider = new TaxonProvider();
        provider.addSource(taxId -> Optional.empty());
        provider.addSource(taxId -> Optional.of(first));
        provider.addSource(taxId -> Optional.of(second));

        assertSame(first, provider.getTaxonByTaxId(12345L).orElseThrow());
    }
}
