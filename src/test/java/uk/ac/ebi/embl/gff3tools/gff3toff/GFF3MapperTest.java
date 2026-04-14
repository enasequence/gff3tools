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
import uk.ac.ebi.embl.gff3tools.metadata.EmbeddedFastaMetadataSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

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

    private FastaHeader createFullHeader() {
        FastaHeader h = new FastaHeader();
        h.setDescription("Homo sapiens genome assembly");
        h.setMoleculeType("genomic DNA");
        h.setTopology("linear");
        return h;
    }

    private FastaHeader createHeaderWithChromosome(String type, String location, String name) {
        FastaHeader h = createFullHeader();
        h.setChromosomeType(type);
        h.setChromosomeLocation(location);
        h.setChromosomeName(name);
        return h;
    }

    /**
     * Helper to create an AnnotationMetadataProvider from a per-seqId FastaHeader map.
     */
    private AnnotationMetadataProvider providerFromFastaHeaders(Map<String, FastaHeader> headerMap) {
        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(new EmbeddedFastaMetadataSource(headerMap));
        return provider;
    }

    /**
     * Helper to create a global fallback AnnotationMetadataProvider from a single FastaHeader.
     */
    private AnnotationMetadataProvider globalProvider(FastaHeader header) {
        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(new uk.ac.ebi.embl.gff3tools.metadata.CliJsonMetadataSource(header));
        return provider;
    }

    @Test
    void appliesDescriptionFromHeader() throws Exception {
        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", createFullHeader()));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertNotNull(entry.getDescription());
        assertEquals("Homo sapiens genome assembly", entry.getDescription().getText());
    }

    @Test
    void appliesMoleculeTypeToSequenceAndQualifier() throws Exception {
        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", createFullHeader()));

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
        FastaHeader h = createFullHeader();
        h.setTopology("linear");
        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.LINEAR, entry.getSequence().getTopology());
    }

    @Test
    void appliesCircularTopology() throws Exception {
        FastaHeader h = createFullHeader();
        h.setTopology("CIRCULAR");
        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.CIRCULAR, entry.getSequence().getTopology());
    }

    @Test
    void unrecognisedTopologyIsSkipped() throws Exception {
        FastaHeader h = createFullHeader();
        h.setTopology("tangled");
        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

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
        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", createFullHeader()));

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
        FastaHeader h = new FastaHeader();
        h.setDescription(null);
        h.setMoleculeType("genomic DNA");
        h.setTopology(null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // molecule_type should still be applied
        assertEquals("genomic DNA", entry.getSequence().getMoleculeType());
    }

    @Test
    void precedenceFileOverCli() throws Exception {
        FastaHeader fileHeader = createFullHeader();
        fileHeader.setDescription("From FASTA file");
        FastaHeader cliHeader = createFullHeader();
        cliHeader.setDescription("From CLI");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(new EmbeddedFastaMetadataSource(Map.of("seq1", fileHeader)));
        provider.addSource(new uk.ac.ebi.embl.gff3tools.metadata.CliJsonMetadataSource(cliHeader));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);

        // seq1 is covered by FASTA source
        Entry entry1 = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));
        assertEquals("From FASTA file", entry1.getDescription().getText());

        // seq2 falls through to CLI source
        Entry entry2 = mapper.mapGFF3ToEntry(createAnnotation("seq2", 1, 500));
        assertEquals("From CLI", entry2.getDescription().getText());
    }

    // -- Step 2.4: Chromosome Name Mapping Tests --

    @Test
    void appliesChromosomeNameAsStandaloneQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, null, "chr1");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> chromQuals = source.getQualifiers("chromosome");
        assertFalse(chromQuals.isEmpty(), "Expected /chromosome qualifier");
        assertEquals("chr1", chromQuals.get(0).getValue());
    }

    @Test
    void nullChromosomeFieldsAreSkipped() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, null, null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

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
        FastaHeader h = createHeaderWithChromosome("Chromosome", null, "chr1");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("chromosome");
        assertFalse(quals.isEmpty(), "Expected /chromosome qualifier");
        assertEquals("chr1", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypePlasmidAddsPlasmidQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome("Plasmid", null, "pBR322");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier");
        assertEquals("pBR322", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeSegmentAddsSegmentQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome("Segment", null, "L");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("segment");
        assertFalse(quals.isEmpty(), "Expected /segment qualifier");
        assertEquals("L", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeLinkageGroupAddsLinkageGroupQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome("Linkage Group", null, "LG1");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("linkage_group");
        assertFalse(quals.isEmpty(), "Expected /linkage_group qualifier");
        assertEquals("LG1", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeMonopartiteAddsNoQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome("Monopartite", null, "seg1");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

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
        FastaHeader h = createHeaderWithChromosome("Unknown", null, "x");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

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
        FastaHeader h = createHeaderWithChromosome("PLASMID", null, "pX");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("plasmid");
        assertFalse(quals.isEmpty(), "Expected /plasmid qualifier for uppercase type");
        assertEquals("pX", quals.get(0).getValue());
    }

    @Test
    void chromosomeTypeWithoutNameAddsQualifierWithoutValue() throws Exception {
        FastaHeader h = createHeaderWithChromosome("Plasmid", null, null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

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
        FastaHeader h = createHeaderWithChromosome("Plasmid", null, "pX");

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

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
        FastaHeader h = createHeaderWithChromosome(null, "Mitochondrion", null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("mitochondrion", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationChloroplastAddsOrganelleQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "Chloroplast", null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("chloroplast", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationPlastidAddsOrganelleQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "Plastid", null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("plastid", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationKinetoplastAddsOrganelleQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "Kinetoplast", null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier");
        assertEquals("kinetoplast", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationNuclearAddsNoQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "Nuclear", null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("organelle").isEmpty(), "Nuclear should not produce /organelle qualifier");
    }

    @Test
    void unrecognisedChromosomeLocationIsPassedThrough() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "Unknown", null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier for unrecognised location");
        assertEquals("unknown", quals.get(0).getValue());
    }

    @Test
    void chromosomeLocationCaseInsensitive() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "MITOCHONDRION", null);

        AnnotationMetadataProvider provider = providerFromFastaHeaders(Map.of("seq1", h));

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
    void fieldLevelMergingAcrossSources() throws Exception {
        // Source 1: has description but no taxon
        AnnotationMetadata source1 = new AnnotationMetadata();
        source1.setDescription("From source 1");

        // Source 2: has description AND taxon
        AnnotationMetadata source2 = new AnnotationMetadata();
        source2.setDescription("From source 2");
        source2.setTaxon("9606");

        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();
        provider.addSource(seqId -> Optional.of(source1));
        provider.addSource(seqId -> Optional.of(source2));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        // Description should come from source 1 (highest priority)
        assertEquals("From source 1", entry.getDescription().getText());
        // Taxon should come from source 2 (source 1 had null)
        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertFalse(source.getQualifiers("db_xref").isEmpty());
        assertEquals("taxon:9606", source.getQualifiers("db_xref").get(0).getValue());
    }
}
