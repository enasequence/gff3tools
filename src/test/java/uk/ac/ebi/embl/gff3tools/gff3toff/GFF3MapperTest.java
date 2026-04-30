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
import uk.ac.ebi.embl.api.entry.XRef;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.entry.reference.Person;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.metadata.AuthorData;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.metadata.ReferenceData;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

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

    private MasterMetadata createFullMetadata() {
        MasterMetadata m = new MasterMetadata();
        m.setDescription("Homo sapiens genome assembly");
        m.setMoleculeType("genomic DNA");
        m.setTopology("linear");
        return m;
    }

    private MasterMetadata createMetadataWithChromosome(String type, String location, String name) {
        MasterMetadata m = createFullMetadata();
        m.setChromosomeType(type);
        m.setChromosomeLocation(location);
        m.setChromosomeName(name);
        return m;
    }

    /**
     * Helper to create an MasterMetadataProvider from a per-seqId metadata map.
     */
    private MasterMetadataProvider providerFromMetadata(Map<String, MasterMetadata> metadataMap) {
        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.ofNullable(metadataMap.get(seqId)));
        return provider;
    }

    /**
     * Wraps a MasterMetadataProvider in a ValidationContext for the GFF3Mapper constructor.
     */
    private ValidationContext contextWith(MasterMetadataProvider provider) {
        ValidationContext ctx = new ValidationContext();
        ctx.register(MasterMetadataProvider.class, provider);
        return ctx;
    }

    @Test
    void appliesDescriptionFromHeader() throws Exception {
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", createFullMetadata()));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertNotNull(entry.getDescription());
        assertEquals("Homo sapiens genome assembly", entry.getDescription().getText());
    }

    @Test
    void appliesMoleculeTypeToSequenceAndQualifier() throws Exception {
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", createFullMetadata()));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("genomic DNA", entry.getSequence().getMoleculeType());

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> molTypeQuals = source.getQualifiers("mol_type");
        assertFalse(molTypeQuals.isEmpty());
        assertEquals("genomic DNA", molTypeQuals.get(0).getValue());
    }

    @Test
    void appliesLinearTopology() throws Exception {
        MasterMetadata h = createFullMetadata();
        h.setTopology("linear");
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.LINEAR, entry.getSequence().getTopology());
    }

    @Test
    void appliesCircularTopology() throws Exception {
        MasterMetadata h = createFullMetadata();
        h.setTopology("CIRCULAR");
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.CIRCULAR, entry.getSequence().getTopology());
    }

    @Test
    void unrecognisedTopologyIsSkipped() throws Exception {
        MasterMetadata h = createFullMetadata();
        h.setTopology("tangled");
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        // Should not throw
        Entry entry = assertDoesNotThrow(() -> mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000)));
        // Topology should remain at its default (null)
        assertNull(entry.getSequence().getTopology());
    }

    @Test
    void nullHeaderProviderSkipsGracefully() throws Exception {
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), new ValidationContext());
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // Should produce default entry with no description set
        assertNotNull(entry);
    }

    @Test
    void noHeaderForSeqIdSkipsGracefully() throws Exception {
        MasterMetadataProvider provider = new MasterMetadataProvider();
        // Empty provider - no sources
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertNotNull(entry);
    }

    @Test
    void nullSequenceRegionSkipsHeaderApplication() throws Exception {
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", createFullMetadata()));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));

        // GFF3Annotation with a null sequenceRegion -- applyMasterMetadata() must return early
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
        MasterMetadata h = new MasterMetadata();
        h.setDescription(null);
        h.setMoleculeType("genomic DNA");
        h.setTopology(null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // molecule_type should still be applied
        assertEquals("genomic DNA", entry.getSequence().getMoleculeType());
    }

    @Test
    void perSeqIdSourceTakesPrecedenceOverGlobal() throws Exception {
        MasterMetadata perSeq = createFullMetadata();
        perSeq.setDescription("From per-seqId source");
        MasterMetadata global = createFullMetadata();
        global.setDescription("From global source");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        Map<String, MasterMetadata> perSeqMap = Map.of("seq1", perSeq);
        provider.addSource(seqId -> Optional.ofNullable(perSeqMap.get(seqId)));
        provider.addSource(seqId -> Optional.of(global));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));

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
        MasterMetadata h = createMetadataWithChromosome(null, null, "chr1");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> chromQuals = source.getQualifiers("chromosome");
        assertFalse(chromQuals.isEmpty(), "Expected /chromosome qualifier");
        assertEquals("chr1", chromQuals.get(0).getValue());
    }

    @Test
    void nullChromosomeFieldsAreSkipped() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, null, null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
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
        MasterMetadata h = createMetadataWithChromosome("Chromosome", null, "chr1");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("chromosome");
        assertFalse(quals.isEmpty(), "Expected /chromosome qualifier");
        assertEquals("chr1", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypePlasmidAddsPlasmidQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome("Plasmid", null, "pBR322");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier");
        assertEquals("pBR322", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeSegmentAddsSegmentQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome("Segment", null, "L");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("segment");
        assertFalse(quals.isEmpty(), "Expected /segment qualifier");
        assertEquals("L", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeLinkageGroupAddsLinkageGroupQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome("Linkage Group", null, "LG1");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("linkage_group");
        assertFalse(quals.isEmpty(), "Expected /linkage_group qualifier");
        assertEquals("LG1", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeMonopartiteAddsNoQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome("Monopartite", null, "seg1");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
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
        MasterMetadata h = createMetadataWithChromosome("Unknown", null, "x");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = assertDoesNotThrow(() -> mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000)));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("chromosome").isEmpty());
        assertTrue(source.getQualifiers("plasmid").isEmpty());
        assertTrue(source.getQualifiers("segment").isEmpty());
        assertTrue(source.getQualifiers("linkage_group").isEmpty());
    }

    @Test
    void chromosomeTypeCaseInsensitive() throws Exception {
        MasterMetadata h = createMetadataWithChromosome("PLASMID", null, "pX");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier for uppercase type");
        assertEquals("pX", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeWithoutNameAddsQualifierWithoutValue() throws Exception {
        MasterMetadata h = createMetadataWithChromosome("Plasmid", null, null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier even without name");
        assertNull(quals.get(0).getValue(), "Qualifier should have no value when name is null");
    }

    @Test
    void chromosomeTypeOverridesStandaloneChromosomeName() throws Exception {
        // When type is Plasmid and name is pX, should add /plasmid="pX" NOT /chromosome="pX"
        MasterMetadata h = createMetadataWithChromosome("Plasmid", null, "pX");

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        // Should have /plasmid, NOT /chromosome
        assertFalse(source.getQualifiers("plasmid").isEmpty(), "Expected /plasmid qualifier");
        assertTrue(source.getQualifiers("chromosome").isEmpty(), "Should NOT have /chromosome when type is Plasmid");
    }

    // -- Step 2.6: Chromosome Location Mapping Tests --

    @Test
    void chromosomeLocationMitochondrionAddsOrganelleQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, "Mitochondrion", null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("mitochondrion", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationChloroplastAddsOrganelleQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, "Chloroplast", null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("chloroplast", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationPlastidAddsOrganelleQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, "Plastid", null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("plastid", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationKinetoplastAddsOrganelleQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, "Kinetoplast", null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("kinetoplast", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationNuclearAddsNoQualifier() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, "Nuclear", null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("organelle").isEmpty(), "Nuclear should not produce /organelle qualifier");
    }

    @Test
    void unrecognisedChromosomeLocationIsPassedThrough() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, "Unknown", null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier for unrecognised location");
        assertEquals("unknown", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationCaseInsensitive() throws Exception {
        MasterMetadata h = createMetadataWithChromosome(null, "MITOCHONDRION", null);

        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier for uppercase location");
        assertEquals("mitochondrion", quals.get(0).getValue());
    }

    // -- New MasterMetadata field mapping tests --

    @Test
    void appliesTitleAsDescriptionFallback() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setTitle("Title as fallback");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("Title as fallback", entry.getDescription().getText());
    }

    @Test
    void descriptionTakesPriorityOverTitle() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setDescription("The description");
        meta.setTitle("The title");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("The description", entry.getDescription().getText());
    }

    @Test
    void appliesDivisionAndDataClass() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setDivision("VRT");
        meta.setDataClass("STD");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals("VRT", entry.getDivision());
        assertEquals("STD", entry.getDataClass());
    }

    @Test
    void appliesTaxonAndScientificName() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setTaxon("9606");
        meta.setScientificName("Homo sapiens");
        meta.setCommonName("human");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
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
        MasterMetadata meta = new MasterMetadata();
        meta.setKeywords(List.of("WGS", "genome"));
        meta.setComment("This is a test comment");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(2, entry.getKeywords().size());
        assertEquals("WGS", entry.getKeywords().get(0).getText());
        assertEquals("genome", entry.getKeywords().get(1).getText());
        assertNotNull(entry.getComment());
        assertEquals("This is a test comment", entry.getComment().getText());
    }

    @Test
    void appliesProjectAccession() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setProject("PRJEB12345");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(1, entry.getProjectAccessions().size());
        assertEquals("PRJEB12345", entry.getProjectAccessions().get(0).getText());
    }

    @Test
    void firstMatchingSourceWinsEntirely() throws Exception {
        // Source 1: has description but no taxon
        MasterMetadata source1 = new MasterMetadata();
        source1.setDescription("From source 1");

        // Source 2: has description AND taxon — should not be reached
        MasterMetadata source2 = new MasterMetadata();
        source2.setDescription("From source 2");
        source2.setTaxon("9606");

        MasterMetadataProvider provider = new MasterMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // Description should come from source 1 (first match wins)
        assertEquals("From source 1", entry.getDescription().getText());
        // Taxon should NOT be set — source 2 is never consulted
        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(
                source.getQualifiers("db_xref").isEmpty(),
                "Source 2 should not contribute fields when source 1 matched");
    }

    // ── Regression tests for the four converter bugs surfaced by the manual-test harness ──

    /**
     * Regression: master.json `runAccessions` (note plural — matches the JSON key)
     * must produce one `DR ENA; <run>.` cross-reference per element. Previously the
     * field was named `runAccession` (singular), so Jackson silently dropped it and
     * no DR lines were ever emitted.
     */
    @Test
    void runAccessionsAreEmittedAsDrEnaXrefs() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setRunAccessions(List.of("ERR10879942", "ERR10890754"));
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        List<XRef> enaRuns = entry.getXRefs().stream()
                .filter(x -> "ENA".equals(x.getDatabase()))
                .toList();
        assertEquals(2, enaRuns.size());
        assertEquals("ERR10879942", enaRuns.get(0).getPrimaryAccession());
        assertEquals("ERR10890754", enaRuns.get(1).getPrimaryAccession());
    }

    /**
     * Regression: a long unwrapped comment in master.json must be word-wrapped to
     * ~75 cols before being handed to {@code Entry.setComment}. CCWriter would
     * otherwise emit one ~250-char `CC` line.
     */
    @Test
    void commentIsWrappedAtSeventyFiveColumns() throws Exception {
        String longComment = "The assembly icPycFuli2.1 is based on 59x PacBio data and Arima2 Hi-C "
                + "data generated by the Darwin Tree of Life Project. The assembly process included "
                + "initial PacBio assembly generation with Hifiasm, retained haplotig separation with "
                + "purge_dups, and Hi-C based scaffolding with YaHS.";
        MasterMetadata meta = new MasterMetadata();
        meta.setComment(longComment);
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        String wrapped = entry.getComment().getText();
        assertTrue(wrapped.contains("\n"), "comment should contain wrapped line breaks");
        for (String line : wrapped.split("\n")) {
            assertTrue(line.length() <= 75, "wrapped CC line must be ≤75 cols, got " + line.length() + ": " + line);
        }
    }

    /**
     * Regression: when both `chromosomeName` (top level) and `searchFields.chromosome`
     * carry the same value, the source feature must emit `/chromosome` exactly once.
     * Previously both code paths fired and the qualifier was duplicated.
     */
    @Test
    void chromosomeQualifierEmittedOnceWhenSetInBothFields() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setChromosomeType("chromosome");
        meta.setChromosomeName("4");
        meta.setSearchFields(Map.of("chromosome", "4"));
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> chromosomeQuals = source.getQualifiers("chromosome");
        assertEquals(1, chromosomeQuals.size(), "chromosome qualifier should appear exactly once");
        assertEquals("4", chromosomeQuals.get(0).getValue());
    }

    /**
     * Regression: when both `moleculeType` (top level) and `searchFields.mol_type`
     * carry a value, only the dedicated path should emit `/mol_type` so the
     * source feature contains the qualifier exactly once.
     */
    @Test
    void molTypeQualifierEmittedOnceWhenSetInBothFields() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setMoleculeType("genomic DNA");
        meta.setSearchFields(Map.of("mol_type", "genomic DNA"));
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> molTypeQuals = source.getQualifiers("mol_type");
        assertEquals(1, molTypeQuals.size(), "mol_type qualifier should appear exactly once");
        assertEquals("genomic DNA", molTypeQuals.get(0).getValue());
    }

    /**
     * Regression: when `taxon` (top level) emits a `db_xref="taxon:N"` qualifier and
     * `searchFields.db_xref` carries the same value, the source feature must emit it once.
     * Non-taxon db_xref values in searchFields should still pass through.
     */
    @Test
    void taxonDbXrefQualifierEmittedOnceWhenSetInBothFields() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setTaxon("9606");
        meta.setSearchFields(Map.of("db_xref", "taxon:9606"));
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> dbXrefQuals = source.getQualifiers("db_xref");
        assertEquals(1, dbXrefQuals.size(), "taxon db_xref qualifier should appear exactly once");
        assertEquals("taxon:9606", dbXrefQuals.get(0).getValue());
    }

    @Test
    void nonTaxonDbXrefInSearchFieldsIsPreserved() throws Exception {
        MasterMetadata meta = new MasterMetadata();
        meta.setTaxon("9606");
        meta.setSearchFields(Map.of("db_xref", "FLYBASE:FBgn0000001"));
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<String> values = source.getQualifiers("db_xref").stream()
                .map(Qualifier::getValue)
                .toList();
        assertEquals(2, values.size());
        assertTrue(values.contains("taxon:9606"));
        assertTrue(values.contains("FLYBASE:FBgn0000001"));
    }

    /**
     * master.json now ships authors as a list of {firstName, middleName, surname}
     * objects. The converter must build a {@link Person} per entry, with the
     * combined firstName + middleName components reduced to compact EMBL initials
     * (e.g. firstName "Eleanor" + middleName "P." → "E.P."). The downstream
     * {@code RAWriter} renders `Person` as `surname + " " + firstName`, so the
     * EMBL `RA` line ends up as `Surname E.P.`.
     */
    @Test
    void structuredAuthorsAreEmittedWithCompactInitials() throws Exception {
        ReferenceData ref = new ReferenceData();
        ref.setReferenceNumber(1);
        AuthorData a1 = new AuthorData();
        a1.setSurname("Ferreira");
        a1.setFirstName("B.");
        AuthorData a2 = new AuthorData();
        a2.setSurname("Salcher");
        a2.setFirstName("Eleanor");
        a2.setMiddleName("P.");
        AuthorData a3 = new AuthorData();
        a3.setSurname("Doe");
        a3.setFirstName("E P");
        ref.setAuthors(List.of(a1, a2, a3));

        MasterMetadata meta = new MasterMetadata();
        meta.setReferences(List.of(ref));
        MasterMetadataProvider provider = providerFromMetadata(Map.of("seq1", meta));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), contextWith(provider));
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(1, entry.getReferences().size());
        List<Person> authors = entry.getReferences().get(0).getPublication().getAuthors();
        assertEquals(3, authors.size());
        assertEquals("Ferreira", authors.get(0).getSurname());
        assertEquals("B.", authors.get(0).getFirstName());
        assertEquals("Salcher", authors.get(1).getSurname());
        assertEquals("E.P.", authors.get(1).getFirstName());
        assertEquals("Doe", authors.get(2).getSurname());
        assertEquals("E.P.", authors.get(2).getFirstName());
    }

    /**
     * {@code toInitials} must accept every name-component shape master.json may
     * emit and reduce it to compact "X.Y." initials with no internal separators.
     */
    @Test
    void toInitialsHandlesAllNameComponentShapes() {
        // null / blank
        assertEquals("", GFF3Mapper.toInitials(null));
        assertEquals("", GFF3Mapper.toInitials(""));
        assertEquals("", GFF3Mapper.toInitials("   "));
        // full name
        assertEquals("E.", GFF3Mapper.toInitials("Eleanor"));
        // single initial, with and without period
        assertEquals("E.", GFF3Mapper.toInitials("E."));
        assertEquals("E.", GFF3Mapper.toInitials("E"));
        // multiple initials, periods + spaces
        assertEquals("E.P.", GFF3Mapper.toInitials("E. P."));
        assertEquals("E.P.", GFF3Mapper.toInitials("E P"));
        assertEquals("E.P.", GFF3Mapper.toInitials("E.P."));
        // doubled whitespace and lowercase
        assertEquals("E.P.", GFF3Mapper.toInitials("  e.   p.  "));
        // compound first name "Mary Anne"
        assertEquals("M.A.", GFF3Mapper.toInitials("Mary Anne"));
    }
}
