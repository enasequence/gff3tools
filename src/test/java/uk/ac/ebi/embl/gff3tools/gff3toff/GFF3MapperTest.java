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
package uk.ac.ebi.embl.gff3tools.gff3toff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.metadata.AnnotationMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.AnnotationMetadataProvider;

class GFF3MapperTest {

    private GFF3FileReader mockReader() {
        GFF3FileReader reader = mock(GFF3FileReader.class);
        when(reader.getTranslationOffsetMap()).thenReturn(Map.of());
        return reader;
    }

    private GFF3Annotation createAnnotation(String accession, long start, long end) {
        GFF3SequenceRegion region = new GFF3SequenceRegion(accession, Optional.empty(), start, end);
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(region);
        return annotation;
    }

    private AnnotationMetadata createFullMetadata() {
        AnnotationMetadata m = new AnnotationMetadata();
        m.setDescription("Homo sapiens genome assembly");
        m.setMoleculeType("genomic DNA");
        m.setTopology("linear");
        return m;
    }

    private AnnotationMetadata createMetadataWithChromosome(String type, String location, String name) {
        AnnotationMetadata m = createFullMetadata();
        m.setChromosomeType(type);
        m.setChromosomeLocation(location);
        m.setChromosomeName(name);
        return m;
    }

    /**
     * Helper to create an AnnotationMetadataProvider from a per-seqId metadata map.
     */
    private AnnotationMetadataProvider providerFromMetadata(Map<String, AnnotationMetadata> metadataMap) {
        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.ofNullable(metadataMap.get(seqId)));
        return provider;
    }

    @Test
    void appliesDescriptionFromHeader() throws Exception {
        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", createFullMetadata()));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertNotNull(entry.getDescription());
        assertEquals("Homo sapiens genome assembly", entry.getDescription().getText());
    }

    @Test
    void appliesMoleculeTypeToSequenceAndQualifier() throws Exception {
        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", createFullMetadata()));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("genomic DNA", entry.getSequence().getMoleculeType());

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> molTypeQuals = source.getQualifiers("mol_type");
        assertFalse(molTypeQuals.isEmpty());
        assertEquals("genomic DNA", molTypeQuals.get(0).getValue());
    }

    @Test
    void appliesLinearTopology() throws Exception {
        AnnotationMetadata h = createFullMetadata();
        h.setTopology("linear");
        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.LINEAR, entry.getSequence().getTopology());
    }

    @Test
    void appliesCircularTopology() throws Exception {
        AnnotationMetadata h = createFullMetadata();
        h.setTopology("CIRCULAR");
        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.CIRCULAR, entry.getSequence().getTopology());
    }

    @Test
    void unrecognisedTopologyIsSkipped() throws Exception {
        AnnotationMetadata h = createFullMetadata();
        h.setTopology("tangled");
        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        // Should not throw
        Entry entry = assertDoesNotThrow(() -> mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000)));
        // Topology should remain at its default (null)
        assertNull(entry.getSequence().getTopology());
    }

    @Test
    void nullHeaderProviderSkipsGracefully() throws Exception {
        GFF3Mapper mapper = new GFF3Mapper(mockReader());
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // Should produce default entry with no description set
        assertNotNull(entry);
    }

    @Test
    void noHeaderForSeqIdSkipsGracefully() throws Exception {
        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        // Empty provider - no sources
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertNotNull(entry);
    }

    @Test
    void nullSequenceRegionSkipsHeaderApplication() throws Exception {
        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", createFullMetadata()));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);

        // GFF3Annotation with a null sequenceRegion -- applyAnnotationMetadata() must return early
        GFF3Annotation annotation = new GFF3Annotation();
        assertNull(annotation.getSequenceRegion());

        Entry entry = assertDoesNotThrow(() -> mapper.mapGFF3ToEntry(annotation));
        assertNotNull(entry);
        // No header data should be applied when sequenceRegion is null;
        // Entry always initialises description to an empty Text object, so we guard
        // with assertNotNull (matching the style at line 63) and then check the text.
        assertNotNull(entry.getDescription());
        assertNull(entry.getDescription().getText());
    }

    @Test
    void nullFieldsInHeaderAreSkippedIndividually() throws Exception {
        AnnotationMetadata h = new AnnotationMetadata();
        h.setDescription(null);
        h.setMoleculeType("genomic DNA");
        h.setTopology(null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // molecule_type should still be applied
        assertEquals("genomic DNA", entry.getSequence().getMoleculeType());
    }

    @Test
    void perSeqIdSourceTakesPrecedenceOverGlobal() throws Exception {
        AnnotationMetadata perSeq = createFullMetadata();
        perSeq.setDescription("From per-seqId source");
        AnnotationMetadata global = createFullMetadata();
        global.setDescription("From global source");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        Map<String, AnnotationMetadata> perSeqMap = Map.of("seq1", perSeq);
        provider.addSource(seqId -> Optional.ofNullable(perSeqMap.get(seqId)));
        provider.addSource(seqId -> Optional.of(global));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);

        // seq1 matches per-seqId source first
        Entry entry1 = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));
        assertEquals("From per-seqId source", entry1.getDescription().getText());

        // seq2 falls through to the global source
        Entry entry2 = mapper.mapGFF3ToEntry(createAnnotation("seq2", 1, 500));
        assertEquals("From global source", entry2.getDescription().getText());
    }

    // -- Step 2.4: Chromosome Name Mapping Tests --

    @Test
    void appliesChromosomeNameAsStandaloneQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, null, "chr1");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> chromQuals = source.getQualifiers("chromosome");
        assertFalse(chromQuals.isEmpty(), "Expected /chromosome qualifier");
        assertEquals("chr1", chromQuals.get(0).getValue());
    }

    @Test
    void nullChromosomeFieldsAreSkipped() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, null, null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("chromosome").isEmpty());
        assertTrue(source.getQualifiers("plasmid").isEmpty());
        assertTrue(source.getQualifiers("segment").isEmpty());
        assertTrue(source.getQualifiers("linkage_group").isEmpty());
        assertTrue(source.getQualifiers("organelle").isEmpty());
    }

    // -- Step 2.5: Chromosome Type Mapping Tests --

    @Test
    void chromosomeTypeChromosomeAddsChromosomeQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("Chromosome", null, "chr1");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("chromosome");
        assertFalse(quals.isEmpty(), "Expected /chromosome qualifier");
        assertEquals("chr1", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypePlasmidAddsPlasmidQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("Plasmid", null, "pBR322");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier");
        assertEquals("pBR322", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeSegmentAddsSegmentQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("Segment", null, "L");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("segment");
        assertFalse(quals.isEmpty(), "Expected /segment qualifier");
        assertEquals("L", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeLinkageGroupAddsLinkageGroupQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("Linkage Group", null, "LG1");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("linkage_group");
        assertFalse(quals.isEmpty(), "Expected /linkage_group qualifier");
        assertEquals("LG1", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeMonopartiteAddsNoQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("Monopartite", null, "seg1");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        // Monopartite produces no qualifier at all
        assertTrue(source.getQualifiers("chromosome").isEmpty());
        assertTrue(source.getQualifiers("plasmid").isEmpty());
        assertTrue(source.getQualifiers("segment").isEmpty());
        assertTrue(source.getQualifiers("linkage_group").isEmpty());
    }

    @Test
    void unrecognisedChromosomeTypeIsSkipped() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("Unknown", null, "x");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = assertDoesNotThrow(() -> mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000)));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("chromosome").isEmpty());
        assertTrue(source.getQualifiers("plasmid").isEmpty());
        assertTrue(source.getQualifiers("segment").isEmpty());
        assertTrue(source.getQualifiers("linkage_group").isEmpty());
    }

    @Test
    void chromosomeTypeCaseInsensitive() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("PLASMID", null, "pX");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier for uppercase type");
        assertEquals("pX", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeWithoutNameAddsQualifierWithoutValue() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome("Plasmid", null, null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier even without name");
        assertNull(quals.get(0).getValue(), "Qualifier should have no value when name is null");
    }

    @Test
    void chromosomeTypeOverridesStandaloneChromosomeName() throws Exception {
        // When type is Plasmid and name is pX, should add /plasmid="pX" NOT /chromosome="pX"
        AnnotationMetadata h = createMetadataWithChromosome("Plasmid", null, "pX");

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        // Should have /plasmid, NOT /chromosome
        assertFalse(source.getQualifiers("plasmid").isEmpty(), "Expected /plasmid qualifier");
        assertTrue(source.getQualifiers("chromosome").isEmpty(), "Should NOT have /chromosome when type is Plasmid");
    }

    // -- Step 2.6: Chromosome Location Mapping Tests --

    @Test
    void chromosomeLocationMitochondrionAddsOrganelleQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, "Mitochondrion", null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("mitochondrion", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationChloroplastAddsOrganelleQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, "Chloroplast", null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("chloroplast", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationPlastidAddsOrganelleQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, "Plastid", null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("plastid", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationKinetoplastAddsOrganelleQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, "Kinetoplast", null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("kinetoplast", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationNuclearAddsNoQualifier() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, "Nuclear", null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("organelle").isEmpty(), "Nuclear should not produce /organelle qualifier");
    }

    @Test
    void unrecognisedChromosomeLocationIsPassedThrough() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, "Unknown", null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier for unrecognised location");
        assertEquals("unknown", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationCaseInsensitive() throws Exception {
        AnnotationMetadata h = createMetadataWithChromosome(null, "MITOCHONDRION", null);

        AnnotationMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier for uppercase location");
        assertEquals("mitochondrion", quals.get(0).getValue());
    }

    // -- New AnnotationMetadata field mapping tests --

    @Test
    void appliesTitleAsDescriptionFallback() throws Exception {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setTitle("Title as fallback");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("Title as fallback", entry.getDescription().getText());
    }

    @Test
    void descriptionTakesPriorityOverTitle() throws Exception {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setDescription("The description");
        meta.setTitle("The title");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("The description", entry.getDescription().getText());
    }

    @Test
    void appliesDivisionAndDataClass() throws Exception {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setDivision("VRT");
        meta.setDataClass("STD");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("VRT", entry.getDivision());
        assertEquals("STD", entry.getDataClass());
    }

    @Test
    void appliesTaxonAndScientificName() throws Exception {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setTaxon("9606");
        meta.setScientificName("Homo sapiens");
        meta.setCommonName("human");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertFalse(source.getQualifiers("db_xref").isEmpty());
        assertEquals("taxon:9606", source.getQualifiers("db_xref").get(0).getValue());
        assertFalse(source.getQualifiers("organism").isEmpty());
        assertEquals("Homo sapiens", source.getQualifiers("organism").get(0).getValue());
        assertFalse(source.getQualifiers("note").isEmpty());
        assertEquals("common name: human", source.getQualifiers("note").get(0).getValue());
    }

    @Test
    void appliesKeywordsAndComment() throws Exception {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setKeywords(List.of("WGS", "genome"));
        meta.setComment("This is a test comment");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(2, entry.getKeywords().size());
        assertEquals("WGS", entry.getKeywords().get(0).getText());
        assertEquals("genome", entry.getKeywords().get(1).getText());
        assertNotNull(entry.getComment());
        assertEquals("This is a test comment", entry.getComment().getText());
    }

    @Test
    void appliesProjectAccession() throws Exception {
        AnnotationMetadata meta = new AnnotationMetadata();
        meta.setProject("PRJEB12345");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(1, entry.getProjectAccessions().size());
        assertEquals("PRJEB12345", entry.getProjectAccessions().get(0).getText());
    }

    @Test
    void firstMatchingSourceWinsEntirely() throws Exception {
        // Source 1: has description but no taxon
        AnnotationMetadata source1 = new AnnotationMetadata();
        source1.setDescription("From source 1");

        // Source 2: has description AND taxon — should not be reached
        AnnotationMetadata source2 = new AnnotationMetadata();
        source2.setDescription("From source 2");
        source2.setTaxon("9606");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // Description should come from source 1 (first match wins)
        assertEquals("From source 1", entry.getDescription().getText());
        // Taxon should NOT be set — source 2 is never consulted
        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(
                source.getQualifiers("db_xref").isEmpty(),
                "Source 2 should not contribute fields when source 1 matched");
    }
}
