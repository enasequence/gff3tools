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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

class TaxonProviderTest {

    private final TaxonFactory taxonFactory = new TaxonFactory();

    private static TaxonProvider providerReturning(Optional<Taxon> value) {
        return new TaxonProvider() {
            @Override
            public Optional<Taxon> resolve(String accession) {
                return value;
            }

            @Override
            public TaxonProvider get(ValidationContext context) {
                return this;
            }
        };
    }

    @Test
    void typeIsAlwaysTaxonProvider() {
        assertEquals(TaxonProvider.class, providerReturning(Optional.empty()).type());
    }

    @Test
    void getReturnsSelf() {
        TaxonProvider provider = providerReturning(Optional.empty());
        assertSame(provider, provider.get(new ValidationContext()));
    }

    @Test
    void resolveReturnsValueFromImplementation() {
        Taxon taxon = taxonFactory.createTaxon();
        taxon.setTaxId(9606L);
        taxon.setScientificName("Homo sapiens");

        TaxonProvider provider = new TaxonProvider() {
            @Override
            public Optional<Taxon> resolve(String accession) {
                return "seq1".equals(accession) ? Optional.of(taxon) : Optional.empty();
            }

            @Override
            public TaxonProvider get(ValidationContext context) {
                return this;
            }
        };

        assertSame(taxon, provider.resolve("seq1").orElseThrow());
        assertTrue(provider.resolve("seq2").isEmpty());
    }
}
