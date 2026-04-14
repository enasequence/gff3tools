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
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

class AnnotationMetadataProviderTest {

    @Test
    void emptyProviderReturnsEmpty() {
        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        assertTrue(provider.getMetadata("seq1").isEmpty());
    }

    @Test
    void singleSourceReturnsMetadata() {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setDescription("Test description");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        Optional<AnnotationMetadata> result = provider.getMetadata("seq1");
        assertTrue(result.isPresent());
        assertEquals("Test description", result.get().getDescription());
    }

    @Test
    void fieldLevelMerging_firstNonNullWins() {
        AnnotationMetadata source1 = new AnnotationMetadata();
        source1.setDescription("From source 1");
        // taxon is null

        AnnotationMetadata source2 = new AnnotationMetadata();
        source2.setDescription("From source 2"); // should be shadowed
        source2.setTaxon("9606"); // should fill the gap

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        Optional<AnnotationMetadata> result = provider.getMetadata("seq1");
        assertTrue(result.isPresent());
        assertEquals("From source 1", result.get().getDescription());
        assertEquals("9606", result.get().getTaxon());
    }

    @Test
    void nullFieldDoesNotShadow() {
        AnnotationMetadata source1 = new AnnotationMetadata();
        source1.setDescription(null); // null should not shadow source2

        AnnotationMetadata source2 = new AnnotationMetadata();
        source2.setDescription("Filled by source 2");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        Optional<AnnotationMetadata> result = provider.getMetadata("seq1");
        assertTrue(result.isPresent());
        assertEquals("Filled by source 2", result.get().getDescription());
    }

    @Test
    void emptySourceSkipped() {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setDescription("From second source");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.empty()); // this source has nothing
        provider.addSource(seqId -> Optional.of(meta));

        Optional<AnnotationMetadata> result = provider.getMetadata("seq1");
        assertTrue(result.isPresent());
        assertEquals("From second source", result.get().getDescription());
    }

    @Test
    void allFieldsMergedCorrectly() {
        AnnotationMetadata source1 = new AnnotationMetadata();
        source1.setDescription("desc");
        source1.setMoleculeType("genomic DNA");

        AnnotationMetadata source2 = new AnnotationMetadata();
        source2.setTaxon("9606");
        source2.setScientificName("Homo sapiens");
        source2.setKeywords(List.of("WGS"));
        source2.setMoleculeType("should be shadowed");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        AnnotationMetadata result = provider.getMetadata("seq1").get();
        assertEquals("desc", result.getDescription());
        assertEquals("genomic DNA", result.getMoleculeType()); // from source1
        assertEquals("9606", result.getTaxon()); // from source2
        assertEquals("Homo sapiens", result.getScientificName()); // from source2
        assertEquals(List.of("WGS"), result.getKeywords()); // from source2
    }

    @Test
    void embeddedFastaMetadataSource() {
        FastaHeader h = new FastaHeader();
        h.setDescription("From FASTA");
        h.setMoleculeType("genomic DNA");
        h.setTopology("linear");

        EmbeddedFastaMetadataSource source = new EmbeddedFastaMetadataSource(Map.of("seq1", h));

        Optional<AnnotationMetadata> meta = source.getMetadata("seq1");
        assertTrue(meta.isPresent());
        assertEquals("From FASTA", meta.get().getDescription());
        assertEquals("genomic DNA", meta.get().getMoleculeType());
        assertEquals("linear", meta.get().getTopology());

        assertTrue(source.getMetadata("seq2").isEmpty());
    }

    @Test
    void cliJsonMetadataSource() {
        FastaHeader h = new FastaHeader();
        h.setDescription("CLI header");
        h.setTopology("circular");

        CliJsonMetadataSource source = new CliJsonMetadataSource(h);

        // Returns same metadata for any seqId
        Optional<AnnotationMetadata> meta1 = source.getMetadata("seq1");
        Optional<AnnotationMetadata> meta2 = source.getMetadata("seq99");

        assertTrue(meta1.isPresent());
        assertTrue(meta2.isPresent());
        assertEquals("CLI header", meta1.get().getDescription());
        assertEquals("circular", meta2.get().getTopology());
    }

    @Test
    void masterEntryJsonMetadataSource() {
        AnnotationMetadata metadata = new AnnotationMetadata();
        metadata.setId("GCA_001");
        metadata.setTaxon("9606");

        MasterEntryJsonMetadataSource source = new MasterEntryJsonMetadataSource(metadata);

        // Returns same metadata for any seqId
        Optional<AnnotationMetadata> result = source.getMetadata("any_seq_id");
        assertTrue(result.isPresent());
        assertEquals("GCA_001", result.get().getId());
        assertEquals("9606", result.get().getTaxon());
    }

    @Test
    void fastaHeadersTakePrecedenceOverMasterEntry() {
        // FASTA-embedded header for seq1
        FastaHeader fastaHeader = new FastaHeader();
        fastaHeader.setDescription("From FASTA");
        fastaHeader.setMoleculeType("genomic DNA");
        fastaHeader.setTopology("circular");

        // MasterEntry JSON metadata (global fallback)
        AnnotationMetadata masterMeta = new AnnotationMetadata();
        masterMeta.setDescription("From MasterEntry");
        masterMeta.setMoleculeType("mRNA");
        masterMeta.setTaxon("9606");
        masterMeta.setScientificName("Homo sapiens");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(new EmbeddedFastaMetadataSource(Map.of("seq1", fastaHeader)));
        provider.addSource(new MasterEntryJsonMetadataSource(masterMeta));

        // seq1: FASTA fields win, MasterEntry fills the rest
        AnnotationMetadata result1 = provider.getMetadata("seq1").get();
        assertEquals("From FASTA", result1.getDescription()); // from FASTA
        assertEquals("genomic DNA", result1.getMoleculeType()); // from FASTA
        assertEquals("circular", result1.getTopology()); // from FASTA
        assertEquals("9606", result1.getTaxon()); // from MasterEntry (not in FASTA)
        assertEquals("Homo sapiens", result1.getScientificName()); // from MasterEntry

        // seq2: only MasterEntry
        AnnotationMetadata result2 = provider.getMetadata("seq2").get();
        assertEquals("From MasterEntry", result2.getDescription());
        assertEquals("mRNA", result2.getMoleculeType());
    }
}
