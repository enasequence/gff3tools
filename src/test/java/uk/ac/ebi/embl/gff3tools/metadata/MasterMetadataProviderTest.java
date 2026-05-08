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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

class MasterMetadataProviderTest {

    @Test
    void emptyProviderReturnsEmpty() {
        MasterMetadataProvider provider = new MasterMetadataProvider();
        assertTrue(provider.getMetadata("seq1").isEmpty());
    }

    @Test
    void singleSourceReturnsMetadata() {
        MasterMetadata meta = new MasterMetadata();
        meta.setDescription("Test description");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        Optional<MasterMetadata> result = provider.getMetadata("seq1");
        assertTrue(result.isPresent());
        assertEquals("Test description", result.get().getDescription());
    }

    @Test
    void firstSourceWinsEntirely() {
        MasterMetadata source1 = new MasterMetadata();
        source1.setDescription("From source 1");
        // taxon is null — but source 1 still wins entirely

        MasterMetadata source2 = new MasterMetadata();
        source2.setDescription("From source 2");
        source2.setTaxon("9606");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        Optional<MasterMetadata> result = provider.getMetadata("seq1");
        assertTrue(result.isPresent());
        assertEquals("From source 1", result.get().getDescription());
        assertNull(result.get().getTaxon(), "Source 2 should not contribute fields when source 1 matched");
    }

    @Test
    void emptySourceSkipped() {
        MasterMetadata meta = new MasterMetadata();
        meta.setDescription("From second source");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.empty()); // this source has nothing
        provider.addSource(seqId -> Optional.of(meta));

        Optional<MasterMetadata> result = provider.getMetadata("seq1");
        assertTrue(result.isPresent());
        assertEquals("From second source", result.get().getDescription());
    }

    @Test
    void firstMatchingSourceReturnedAsIs() {
        MasterMetadata source1 = new MasterMetadata();
        source1.setDescription("desc");
        source1.setMoleculeType("genomic DNA");

        MasterMetadata source2 = new MasterMetadata();
        source2.setTaxon("9606");
        source2.setScientificName("Homo sapiens");
        source2.setKeywords(List.of("WGS"));
        source2.setMoleculeType("should never be reached");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        MasterMetadata result = provider.getMetadata("seq1").get();
        assertSame(source1, result, "Should return the exact object from the first matching source");
        assertEquals("desc", result.getDescription());
        assertEquals("genomic DNA", result.getMoleculeType());
        assertNull(result.getTaxon(), "Fields from source2 should not be present");
    }

    @Test
    void emblMasterEntryMaterializedLazilyOnContextGet() {
        EntryFactory entryFactory = new EntryFactory();
        Entry entry = entryFactory.createEntry();
        entry.setPrimaryAccession("AB123456");
        entry.setDescription(new Text("Lazy materialization"));

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.setEmblMasterEntry(entry);

        // Before context resolution, no source has been materialized.
        assertTrue(provider.getMetadata("any").isEmpty());

        // Resolving via the context registers the EmblEntryMetadataSource.
        ValidationContext ctx = new ValidationContext();
        ctx.register(TaxonProvider.class, new TaxonProvider());
        ctx.register(MasterMetadataProvider.class, provider);
        provider.get(ctx);

        Optional<MasterMetadata> meta = provider.getMetadata("any");
        assertTrue(meta.isPresent());
        assertEquals("AB123456", meta.get().getAccession());
        assertEquals("Lazy materialization", meta.get().getDescription());
    }

    @Test
    void taxonIdMaterializedLazilyOnContextGet() {
        TaxonFactory taxonFactory = new TaxonFactory();
        Taxon resolved = taxonFactory.createTaxon();
        resolved.setTaxId(9606L);
        resolved.setScientificName("Homo sapiens");

        TaxonProvider taxonProvider = new TaxonProvider();
        taxonProvider.addSource(taxId -> taxId.equals(9606L) ? Optional.of(resolved) : Optional.empty());

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.setTaxonId(9606L);

        // Before context resolution, no source has been materialized.
        assertTrue(provider.getMetadata("any").isEmpty());

        ValidationContext ctx = new ValidationContext();
        ctx.register(TaxonProvider.class, taxonProvider);
        ctx.register(MasterMetadataProvider.class, provider);
        provider.get(ctx);

        MasterMetadata meta = provider.getMetadata("any").orElseThrow();
        assertEquals("9606", meta.getTaxon());
        assertEquals("Homo sapiens", meta.getScientificName());
    }

    @Test
    void masterEntryJsonMetadataSource() {
        MasterMetadata metadata = new MasterMetadata();
        metadata.setId("GCA_001");
        metadata.setTaxon("9606");

        MasterEntryJsonMetadataSource source = new MasterEntryJsonMetadataSource(metadata);

        // Returns same metadata for any seqId
        Optional<MasterMetadata> result = source.getMetadata("any_seq_id");
        assertTrue(result.isPresent());
        assertEquals("GCA_001", result.get().getId());
        assertEquals("9606", result.get().getTaxon());
    }
}
