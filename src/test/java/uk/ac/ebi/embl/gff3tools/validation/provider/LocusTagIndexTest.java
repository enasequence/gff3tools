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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;

/**
 * SC7: LocusTagIndex built by LocusTagIndexProvider contains correct mappings
 * for a test annotation.
 */
class LocusTagIndexTest {

    private static OntologyClient ontology;

    @BeforeAll
    static void initOntology() {
        ontology = ConversionUtils.getOntologyClient();
    }

    // ------------------------------------------------------------------
    // geneToLocusTag
    // ------------------------------------------------------------------

    @Test
    @DisplayName("geneToLocusTag maps gene name to locus_tag (first-seen wins)")
    void geneToLocusTag_mapsGeneNameToLocusTag() {
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.GENE, List.of("geneA"), GFF3Attributes.LOCUS_TAG, List.of("LT_001")));
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "CDS",
                "CDS",
                Map.of(GFF3Attributes.GENE, List.of("geneA"), GFF3Attributes.LOCUS_TAG, List.of("LT_002")));

        LocusTagIndex index = LocusTagIndex.build(List.of(gene, cds), ontology);

        assertEquals("LT_001", index.getGeneToLocusTag().get("geneA"), "First-seen locus_tag should win");
        assertEquals(1, index.getGeneToLocusTag().size());
    }

    @Test
    @DisplayName("geneToLocusTag ignores features without gene attribute")
    void geneToLocusTag_ignoresFeaturesWithoutGene() {
        GFF3Feature cds =
                TestUtils.createGFF3Feature("CDS", "CDS", Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001")));

        LocusTagIndex index = LocusTagIndex.build(List.of(cds), ontology);

        assertTrue(index.getGeneToLocusTag().isEmpty());
    }

    // ------------------------------------------------------------------
    // locusTagToGeneFeature
    // ------------------------------------------------------------------

    @Test
    @DisplayName("locusTagToGeneFeature maps locus_tag to gene/pseudogene feature")
    void locusTagToGeneFeature_mapsLocusTagToGeneFeature() {
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "CDS",
                "CDS",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));

        LocusTagIndex index = LocusTagIndex.build(List.of(gene, cds), ontology);

        assertSame(gene, index.getLocusTagToGeneFeature().get("LT_001"));
        assertEquals(1, index.getLocusTagToGeneFeature().size(), "CDS should not appear in locusTagToGeneFeature");
    }

    @Test
    @DisplayName("locusTagToGeneFeature includes pseudogene types")
    void locusTagToGeneFeature_includesPseudogene() {
        GFF3Feature pseudogene = TestUtils.createGFF3Feature(
                "pseudogene",
                "pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_002"), GFF3Attributes.PSEUDOGENE, List.of("processed")));

        LocusTagIndex index = LocusTagIndex.build(List.of(pseudogene), ontology);

        assertSame(pseudogene, index.getLocusTagToGeneFeature().get("LT_002"));
    }

    // ------------------------------------------------------------------
    // locusTagToGene
    // ------------------------------------------------------------------

    @Test
    @DisplayName("locusTagToGene maps locus_tag to gene name from gene/CDS features")
    void locusTagToGene_mapsLocusTagToGeneName() {
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "CDS",
                "CDS",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneB")));

        LocusTagIndex index = LocusTagIndex.build(List.of(gene, cds), ontology);

        assertEquals("geneA", index.getLocusTagToGene().get("LT_001"), "First-seen gene should win");
    }

    @Test
    @DisplayName("locusTagToGene skips non-gene/CDS features")
    void locusTagToGene_skipsNonGeneOrCds() {
        // signal_peptide is not a gene or CDS
        GFF3Feature peptide = TestUtils.createGFF3Feature(
                "signal_peptide",
                "signal_peptide",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));

        LocusTagIndex index = LocusTagIndex.build(List.of(peptide), ontology);

        assertTrue(index.getLocusTagToGene().isEmpty(), "signal_peptide should not populate locusTagToGene");
    }

    // ------------------------------------------------------------------
    // locusTagToSynonyms
    // ------------------------------------------------------------------

    @Test
    @DisplayName("locusTagToSynonyms maps locus_tag to parsed synonym list")
    void locusTagToSynonyms_parsesCommaSeparatedSynonyms() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "CDS",
                "CDS",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("LT_001"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("syn1,syn2,syn3")));

        LocusTagIndex index = LocusTagIndex.build(List.of(cds), ontology);

        assertEquals(
                List.of("syn1", "syn2", "syn3"), index.getLocusTagToSynonyms().get("LT_001"));
    }

    @Test
    @DisplayName("locusTagToSynonyms uses first-seen synonyms per locus_tag")
    void locusTagToSynonyms_firstSeenWins() {
        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                "CDS",
                "CDS",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE_SYNONYM, List.of("synA")));
        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                "CDS",
                "CDS",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE_SYNONYM, List.of("synB")));

        LocusTagIndex index = LocusTagIndex.build(List.of(cds1, cds2), ontology);

        assertEquals(List.of("synA"), index.getLocusTagToSynonyms().get("LT_001"), "First-seen synonyms should win");
    }

    // ------------------------------------------------------------------
    // locusTagToPeptides
    // ------------------------------------------------------------------

    @Test
    @DisplayName("locusTagToPeptides collects polypeptide_region descendants")
    void locusTagToPeptides_collectsPeptides() {
        GFF3Feature peptide1 = TestUtils.createGFF3Feature(
                "signal_peptide", "signal_peptide", Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001")));
        GFF3Feature peptide2 = TestUtils.createGFF3Feature(
                "transit_peptide", "transit_peptide", Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001")));
        GFF3Feature cds =
                TestUtils.createGFF3Feature("CDS", "CDS", Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001")));

        LocusTagIndex index = LocusTagIndex.build(List.of(peptide1, peptide2, cds), ontology);

        List<GFF3Feature> peptides = index.getLocusTagToPeptides().get("LT_001");
        assertNotNull(peptides);
        assertEquals(2, peptides.size());
        assertTrue(peptides.contains(peptide1));
        assertTrue(peptides.contains(peptide2));
    }

    @Test
    @DisplayName("locusTagToPeptides does not include non-peptide features")
    void locusTagToPeptides_excludesNonPeptides() {
        GFF3Feature cds =
                TestUtils.createGFF3Feature("CDS", "CDS", Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001")));
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));

        LocusTagIndex index = LocusTagIndex.build(List.of(cds, gene), ontology);

        assertTrue(index.getLocusTagToPeptides().isEmpty(), "CDS and gene should not appear in locusTagToPeptides");
    }

    // ------------------------------------------------------------------
    // Combined / edge cases
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SC7: Full annotation produces correct mappings across all five maps")
    void build_fullAnnotation_correctMappings() {
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("LT_001"),
                        GFF3Attributes.GENE,
                        List.of("geneA"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("syn1,syn2")));
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "CDS",
                "CDS",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));
        GFF3Feature peptide = TestUtils.createGFF3Feature(
                "signal_peptide",
                "signal_peptide",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));

        LocusTagIndex index = LocusTagIndex.build(List.of(gene, cds, peptide), ontology);

        // geneToLocusTag
        assertEquals("LT_001", index.getGeneToLocusTag().get("geneA"));

        // locusTagToGeneFeature
        assertSame(gene, index.getLocusTagToGeneFeature().get("LT_001"));

        // locusTagToGene
        assertEquals("geneA", index.getLocusTagToGene().get("LT_001"));

        // locusTagToSynonyms
        assertEquals(List.of("syn1", "syn2"), index.getLocusTagToSynonyms().get("LT_001"));

        // locusTagToPeptides
        assertEquals(1, index.getLocusTagToPeptides().get("LT_001").size());
        assertSame(peptide, index.getLocusTagToPeptides().get("LT_001").get(0));
    }

    @Test
    @DisplayName("build() with empty feature list produces empty maps")
    void build_emptyFeatureList_producesEmptyMaps() {
        LocusTagIndex index = LocusTagIndex.build(List.of(), ontology);

        assertTrue(index.getGeneToLocusTag().isEmpty());
        assertTrue(index.getLocusTagToGeneFeature().isEmpty());
        assertTrue(index.getLocusTagToGene().isEmpty());
        assertTrue(index.getLocusTagToSynonyms().isEmpty());
        assertTrue(index.getLocusTagToPeptides().isEmpty());
    }

    @Test
    @DisplayName("build() skips null features")
    void build_nullFeatures_areSkipped() {
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));

        LocusTagIndex index = LocusTagIndex.build(java.util.Arrays.asList(null, gene, null), ontology);

        assertEquals("LT_001", index.getGeneToLocusTag().get("geneA"));
    }

    @Test
    @DisplayName("Maps returned by getters are unmodifiable")
    void build_mapsAreUnmodifiable() {
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));

        LocusTagIndex index = LocusTagIndex.build(List.of(gene), ontology);

        assertThrows(UnsupportedOperationException.class, () -> index.getGeneToLocusTag()
                .put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> index.getLocusTagToGeneFeature()
                .put("x", gene));
        assertThrows(UnsupportedOperationException.class, () -> index.getLocusTagToGene()
                .put("x", "y"));
        assertThrows(UnsupportedOperationException.class, () -> index.getLocusTagToSynonyms()
                .put("x", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> index.getLocusTagToPeptides()
                .put("x", List.of()));
    }
}
