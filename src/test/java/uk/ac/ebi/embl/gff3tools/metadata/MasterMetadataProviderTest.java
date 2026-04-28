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
