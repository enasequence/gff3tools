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
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.CliFastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FileFastaHeaderSource;
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

    @Test
    void appliesDescriptionFromHeader() throws Exception {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", createFullHeader())));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertNotNull(entry.getDescription());
        assertEquals("Homo sapiens genome assembly", entry.getDescription().getText());
    }

    @Test
    void appliesMoleculeTypeToSequenceAndQualifier() throws Exception {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", createFullHeader())));

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
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.LINEAR, entry.getSequence().getTopology());
    }

    @Test
    void appliesCircularTopology() throws Exception {
        FastaHeader h = createFullHeader();
        h.setTopology("CIRCULAR");
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertEquals(Sequence.Topology.CIRCULAR, entry.getSequence().getTopology());
    }

    @Test
    void unrecognisedTopologyIsSkipped() throws Exception {
        FastaHeader h = createFullHeader();
        h.setTopology("tangled");
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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
        FastaHeaderProvider provider = new FastaHeaderProvider();
        // Empty provider - no sources
        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        assertNotNull(entry);
    }

    @Test
    void nullSequenceRegionSkipsHeaderApplication() throws Exception {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", createFullHeader())));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);

        // GFF3Annotation with a null sequenceRegion — applyFastaHeader() must return early
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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", fileHeader)));
        provider.addSource(new CliFastaHeaderSource(cliHeader));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);

        // seq1 is covered by FASTA source
        Entry entry1 = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));
        assertEquals("From FASTA file", entry1.getDescription().getText());

        // seq2 falls through to CLI source
        Entry entry2 = mapper.mapGFF3ToEntry(createAnnotation("seq2", 1, 500));
        assertEquals("From CLI", entry2.getDescription().getText());
    }

    // ── Step 2.4: Chromosome Name Mapping Tests ──

    @Test
    void appliesChromosomeNameAsStandaloneQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, null, "chr1");

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("chromosome").isEmpty());
        assertTrue(source.getQualifiers("plasmid").isEmpty());
        assertTrue(source.getQualifiers("segment").isEmpty());
        assertTrue(source.getQualifiers("linkage_group").isEmpty());
        assertTrue(source.getQualifiers("organelle").isEmpty());
    }

    // ── Step 2.5: Chromosome Type Mapping Tests ──

    @Test
    void chromosomeTypeChromosomeAddsChromosomeQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome("Chromosome", null, "chr1");

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        // Should have /plasmid, NOT /chromosome
        assertFalse(source.getQualifiers("plasmid").isEmpty(), "Expected /plasmid qualifier");
        assertTrue(source.getQualifiers("chromosome").isEmpty(), "Should NOT have /chromosome when type is Plasmid");
    }

    // ── Step 2.6: Chromosome Location Mapping Tests ──

    @Test
    void chromosomeLocationMitochondrionAddsOrganelleQualifier() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "Mitochondrion", null);

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        assertTrue(source.getQualifiers("organelle").isEmpty(), "Nuclear should not produce /organelle qualifier");
    }

    @Test
    void unrecognisedChromosomeLocationIsPassedThrough() throws Exception {
        FastaHeader h = createHeaderWithChromosome(null, "Unknown", null);

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

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

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

        GFF3Mapper mapper = new GFF3Mapper(mockReader(), provider);
        Entry entry = mapper.mapGFF3ToEntry(createAnnotation("seq1", 1, 1000));

        SourceFeature source = (SourceFeature) entry.getFeatures().get(0);
        List<Qualifier> quals = source.getQualifiers("organelle");
        assertFalse(quals.isEmpty(), "Expected /organelle qualifier for uppercase location");
        assertEquals("mitochondrion", quals.get(0).getValue());
    }
}
