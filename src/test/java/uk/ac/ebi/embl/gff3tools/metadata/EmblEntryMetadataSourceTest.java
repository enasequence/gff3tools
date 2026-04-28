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
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.api.entry.feature.FeatureFactory;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.qualifier.QualifierFactory;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;
import uk.ac.ebi.ena.taxonomy.taxon.Taxon;
import uk.ac.ebi.ena.taxonomy.taxon.TaxonFactory;

class EmblEntryMetadataSourceTest {

    private final EntryFactory entryFactory = new EntryFactory();
    private final FeatureFactory featureFactory = new FeatureFactory();
    private final QualifierFactory qualifierFactory = new QualifierFactory();
    private final SequenceFactory sequenceFactory = new SequenceFactory();
    private final TaxonFactory taxonFactory = new TaxonFactory();

    /**
     * Creates an Entry populated with representative field values for testing.
     */
    private Entry createPopulatedEntry() {
        Entry entry = entryFactory.createEntry();
        entry.setPrimaryAccession("AB123456");
        entry.setDescription(new Text("Test genome assembly"));
        entry.setDivision("HUM");
        entry.setDataClass("STD");
        entry.setVersion(2);
        entry.setComment(new Text("Test comment for the entry"));

        // Sequence-level fields
        Sequence sequence = sequenceFactory.createSequence();
        sequence.setMoleculeType("genomic DNA");
        sequence.setTopology(Sequence.Topology.LINEAR);
        entry.setSequence(sequence);

        // Source feature with organism qualifier and taxon
        SourceFeature source = featureFactory.createSourceFeature();
        var orgQualifier = qualifierFactory.createOrganismQualifier("Homo sapiens");
        Taxon taxon = taxonFactory.createTaxon();
        taxon.setTaxId(9606L);
        taxon.setScientificName("Homo sapiens");
        taxon.setCommonName("human");
        taxon.setLineage("Eukaryota; Metazoa; Chordata; Vertebrata;");
        orgQualifier.setTaxon(taxon);
        source.addQualifier(orgQualifier);
        entry.addFeature(source);

        // Keywords
        entry.addKeyword(new Text("WGS"));
        entry.addKeyword(new Text("genome"));

        // Project accession
        entry.addProjectAccession(new Text("PRJEB12345"));

        // Secondary accessions
        entry.addSecondaryAccession(new Text("AB000001"));
        entry.addSecondaryAccession(new Text("AB000002"));

        return entry;
    }

    @Test
    void mapsAllFieldsFromPopulatedEntry() {
        Entry entry = createPopulatedEntry();
        EmblEntryMetadataSource source = new EmblEntryMetadataSource(entry);

        MasterMetadata meta = source.getMetadata();
        assertNotNull(meta);

        assertEquals("AB123456", meta.getAccession());
        assertEquals("Test genome assembly", meta.getDescription());
        assertEquals("HUM", meta.getDivision());
        assertEquals("STD", meta.getDataClass());
        assertEquals(2, meta.getVersion());
        assertEquals("Test comment for the entry", meta.getComment());

        // Sequence-level fields
        assertEquals("genomic DNA", meta.getMoleculeType());
        assertEquals("linear", meta.getTopology());

        // Taxonomy
        assertEquals("Homo sapiens", meta.getScientificName());
        assertEquals("9606", meta.getTaxon());
        assertEquals("human", meta.getCommonName());
        assertEquals("Eukaryota; Metazoa; Chordata; Vertebrata;", meta.getLineage());

        // Keywords
        assertNotNull(meta.getKeywords());
        assertEquals(2, meta.getKeywords().size());
        assertEquals("WGS", meta.getKeywords().get(0));
        assertEquals("genome", meta.getKeywords().get(1));

        // Project
        assertEquals("PRJEB12345", meta.getProject());

        // Secondary accessions
        assertNotNull(meta.getSecondaryAccessions());
        assertEquals(2, meta.getSecondaryAccessions().size());
        assertEquals("AB000001", meta.getSecondaryAccessions().get(0));
        assertEquals("AB000002", meta.getSecondaryAccessions().get(1));
    }

    @Test
    void returnsSameMetadataForAnySeqId() {
        Entry entry = createPopulatedEntry();
        EmblEntryMetadataSource source = new EmblEntryMetadataSource(entry);

        Optional<MasterMetadata> meta1 = source.getMetadata("seq1");
        Optional<MasterMetadata> meta2 = source.getMetadata("anything_else");

        assertTrue(meta1.isPresent());
        assertTrue(meta2.isPresent());
        assertSame(meta1.get(), meta2.get());
    }

    @Test
    void handlesMinimalEntry() {
        Entry entry = entryFactory.createEntry();
        EmblEntryMetadataSource source = new EmblEntryMetadataSource(entry);

        MasterMetadata meta = source.getMetadata();
        assertNotNull(meta);
        assertNull(meta.getAccession());
        assertNull(meta.getDescription());
        assertNull(meta.getLineage());
        assertNull(meta.getScientificName());
        assertNull(meta.getTaxon());
    }

    @Test
    void fallsBackToDbXrefForTaxon() {
        // Entry with a plain organism qualifier (not OrganismQualifier) and a db_xref
        Entry entry = entryFactory.createEntry();
        SourceFeature source = featureFactory.createSourceFeature();
        source.addQualifier(qualifierFactory.createQualifier("organism", "Unknown species"));
        source.addQualifier(qualifierFactory.createQualifier("db_xref", "taxon:12345"));
        entry.addFeature(source);

        EmblEntryMetadataSource metaSource = new EmblEntryMetadataSource(entry);
        MasterMetadata meta = metaSource.getMetadata();

        assertEquals("12345", meta.getTaxon());
    }

    @Test
    void mapsCircularTopology() {
        Entry entry = entryFactory.createEntry();
        Sequence seq = sequenceFactory.createSequence();
        seq.setTopology(Sequence.Topology.CIRCULAR);
        entry.setSequence(seq);

        EmblEntryMetadataSource source = new EmblEntryMetadataSource(entry);
        MasterMetadata meta = source.getMetadata();

        assertEquals("circular", meta.getTopology());
    }
}
