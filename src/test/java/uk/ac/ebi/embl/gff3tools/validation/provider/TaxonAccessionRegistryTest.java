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

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class TaxonAccessionRegistryTest {

    @Test
    void find_returnsEmptyForUnknownAccession() {
        TaxonAccessionRegistry registry = new TaxonAccessionRegistry();
        assertTrue(registry.find("seq1").isEmpty());
    }

    @Test
    void record_andFind_byTaxId() {
        TaxonAccessionRegistry registry = new TaxonAccessionRegistry();
        registry.record("seq1", new TaxonomyIdentifier.ByTaxId(9606L));

        TaxonomyIdentifier identifier = registry.find("seq1").orElseThrow();
        assertInstanceOf(TaxonomyIdentifier.ByTaxId.class, identifier);
        assertEquals(9606L, ((TaxonomyIdentifier.ByTaxId) identifier).taxId());
    }

    @Test
    void record_andFind_byScientificName() {
        TaxonAccessionRegistry registry = new TaxonAccessionRegistry();
        registry.record("seq1", new TaxonomyIdentifier.ByScientificName("Homo sapiens"));

        TaxonomyIdentifier identifier = registry.find("seq1").orElseThrow();
        assertInstanceOf(TaxonomyIdentifier.ByScientificName.class, identifier);
        assertEquals("Homo sapiens", ((TaxonomyIdentifier.ByScientificName) identifier).scientificName());
    }

    @Test
    void differentAccessionsCarryIndependentIdentifiers() {
        TaxonAccessionRegistry registry = new TaxonAccessionRegistry();
        registry.record("seq1", new TaxonomyIdentifier.ByTaxId(9606L));
        registry.record("seq2", new TaxonomyIdentifier.ByScientificName("Mus musculus"));

        assertEquals(9606L, ((TaxonomyIdentifier.ByTaxId) registry.find("seq1").orElseThrow()).taxId());
        assertEquals(
                "Mus musculus",
                ((TaxonomyIdentifier.ByScientificName) registry.find("seq2").orElseThrow()).scientificName());
    }

    @Test
    void record_ignoresNullAccessionAndNullIdentifier() {
        TaxonAccessionRegistry registry = new TaxonAccessionRegistry();
        registry.record(null, new TaxonomyIdentifier.ByTaxId(9606L));
        registry.record("seq1", null);

        assertTrue(registry.find("seq1").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void typeIsTaxonAccessionRegistry() {
        assertEquals(TaxonAccessionRegistry.class, new TaxonAccessionRegistry().type());
    }

    @Test
    void getReturnsSelf() {
        TaxonAccessionRegistry registry = new TaxonAccessionRegistry();
        assertSame(registry, registry.get(new ValidationContext()));
    }
}
