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

class TaxonIdMetadataSourceTest {

    private final TaxonFactory taxonFactory = new TaxonFactory();

    @Test
    void resolvesTaxonFieldsViaProvider() {
        Taxon resolved = taxonFactory.createTaxon();
        resolved.setTaxId(9606L);
        resolved.setScientificName("Homo sapiens");
        resolved.setCommonName("human");
        resolved.setLineage("Eukaryota; Metazoa; Chordata; Mammalia;");

        TaxonProvider provider = new TaxonProvider();
        provider.addSource(taxId -> taxId.equals(9606L) ? Optional.of(resolved) : Optional.empty());

        TaxonIdMetadataSource source = new TaxonIdMetadataSource(9606L, provider);
        MasterMetadata meta = source.getMetadata("any").orElseThrow();

        assertEquals("9606", meta.getTaxon());
        assertEquals("Homo sapiens", meta.getScientificName());
        assertEquals("human", meta.getCommonName());
        assertEquals("Eukaryota; Metazoa; Chordata; Mammalia;", meta.getLineage());
    }

    @Test
    void unresolvedTaxonStillCarriesId() {
        TaxonIdMetadataSource source = new TaxonIdMetadataSource(42L, new TaxonProvider());
        MasterMetadata meta = source.getMetadata("any").orElseThrow();

        assertEquals("42", meta.getTaxon());
        assertNull(meta.getScientificName());
        assertNull(meta.getCommonName());
        assertNull(meta.getLineage());
    }

    @Test
    void sameMetadataReturnedForAnySeqId() {
        TaxonIdMetadataSource source = new TaxonIdMetadataSource(9606L, new TaxonProvider());

        MasterMetadata first = source.getMetadata("seq1").orElseThrow();
        MasterMetadata second = source.getMetadata("seq2").orElseThrow();

        assertSame(first, second);
    }
}
