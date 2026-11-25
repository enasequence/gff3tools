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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class GeneAssociatedFeatureRemovalTest {

    GFF3Annotation gff3Annotation;

    private GeneAssociatedFeatureRemoval geneAssociatedFeatureRemoval;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        geneAssociatedFeatureRemoval = new GeneAssociatedFeatureRemoval();
        TestUtils.injectContext(geneAssociatedFeatureRemoval);
    }

    @Test
    public void testFixAnnotationWithoutGene() {
        List<GFF3Feature> geneFeatureList = new ArrayList<>();
        geneFeatureList.add(TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 100L, 200L));
        geneFeatureList.add(TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), 101L, 300L));
        geneFeatureList.add(TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), 301L, 400L));
        gff3Annotation.setFeatures(geneFeatureList);

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);
        assertEquals(3, gff3Annotation.getFeatures().size());
    }

    @Test
    public void testFixAnnotationWithGeneOnly() {
        List<GFF3Feature> geneFeatureList = new ArrayList<>();
        GFF3Feature f1 = TestUtils.createGFF3Feature("intron", 201L, 300L);

        GFF3Feature f2 = TestUtils.createGFF3Feature("gene", 301L, 400L);
        geneFeatureList.add(f1);
        geneFeatureList.add(f2);
        gff3Annotation.setFeatures(geneFeatureList);

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);
        assertEquals(2, gff3Annotation.getFeatures().size());
        assertTrue(gff3Annotation.getFeatures().contains(f2));
    }

    @Test
    public void testFixAnnotationWithGeneDifferentLocation() {
        List<GFF3Feature> geneFeatureList = new ArrayList<>();
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 100L, 200L);
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), 201L, 300L);
        GFF3Feature f3 = TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), 301L, 400L);
        GFF3Feature f4 = TestUtils.createGFF3Feature("gene", 401L, 500L);
        geneFeatureList.add(f1);
        geneFeatureList.add(f2);
        geneFeatureList.add(f3);
        geneFeatureList.add(f4);

        gff3Annotation.setFeatures(geneFeatureList);

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);
        assertEquals(4, gff3Annotation.getFeatures().size());
        assertTrue(gff3Annotation.getFeatures().contains(f4));
    }

    @Test
    public void testFixAnnotationWithGeneSameLocation() {
        List<GFF3Feature> geneFeatureList = new ArrayList<>();
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 100L, 200L);
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), 201L, 300L);
        GFF3Feature f3 = TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), 301L, 400L);
        GFF3Feature f4 = TestUtils.createGFF3Feature("gene", 301L, 400L);
        geneFeatureList.add(f1);
        geneFeatureList.add(f2);
        geneFeatureList.add(f3);
        geneFeatureList.add(f4);

        gff3Annotation.setFeatures(geneFeatureList);

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);
        assertEquals(3, gff3Annotation.getFeatures().size());
        assertFalse(gff3Annotation.getFeatures().contains(f4));
    }

    // Parent-clearing tests

    @Test
    public void geneWithNoId_childParentReferenceNotCleared() {
        GFF3Feature gene = new GFF3Feature(
                Optional.empty(),
                Optional.empty(),
                TestUtils.DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                "gene",
                100L,
                200L,
                ".",
                "+",
                "");
        GFF3Feature cds = cdsWithParent("gene_1", 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertFalse(gff3Annotation.getFeatures().contains(gene));
        assertEquals(Optional.of("gene_1"), cds.getParentId());
        assertEquals("gene_1", cds.getAttribute(GFF3Attributes.ATTRIBUTE_PARENT).orElse(null));
    }

    @Test
    public void multipleChildrenSameRemovedGene_allParentReferencesCleared() {
        GFF3Feature gene = geneWithId("gene_1", 100L, 200L);
        GFF3Feature cds1 = cdsWithParent("gene_1", 100L, 200L);
        GFF3Feature cds2 = new GFF3Feature(
                Optional.of("cds_2"),
                Optional.of("gene_1"),
                TestUtils.DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                OntologyTerm.CDS.name(),
                100L,
                200L,
                ".",
                "+",
                "");
        cds2.addAttribute(GFF3Attributes.ATTRIBUTE_PARENT, "gene_1");
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds1, cds2)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertFalse(gff3Annotation.getFeatures().contains(gene));
        assertTrue(cds1.getParentId().isEmpty());
        assertTrue(cds2.getParentId().isEmpty());
        assertTrue(cds1.getAttribute(GFF3Attributes.ATTRIBUTE_PARENT).isEmpty());
        assertTrue(cds2.getAttribute(GFF3Attributes.ATTRIBUTE_PARENT).isEmpty());
    }

    @Test
    public void childOfKeptGene_parentReferenceUnchanged() {
        GFF3Feature removedGene = geneWithId("gene_1", 100L, 200L);
        GFF3Feature keptGene = geneWithId("gene_2", 300L, 400L);
        GFF3Feature cdsMatchingRemovedGene = cdsWithParent("gene_1", 100L, 200L);
        GFF3Feature cdsChildOfKeptGene = new GFF3Feature(
                Optional.of("cds_2"),
                Optional.of("gene_2"),
                TestUtils.DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                OntologyTerm.CDS.name(),
                300L,
                500L,
                ".",
                "+",
                "");
        cdsChildOfKeptGene.addAttribute(GFF3Attributes.ATTRIBUTE_PARENT, "gene_2");
        gff3Annotation.setFeatures(
                new ArrayList<>(List.of(removedGene, keptGene, cdsMatchingRemovedGene, cdsChildOfKeptGene)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertFalse(gff3Annotation.getFeatures().contains(removedGene));
        assertTrue(gff3Annotation.getFeatures().contains(keptGene));
        assertTrue(cdsMatchingRemovedGene.getParentId().isEmpty());
        assertEquals(Optional.of("gene_2"), cdsChildOfKeptGene.getParentId());
        assertEquals(
                "gene_2",
                cdsChildOfKeptGene.getAttribute(GFF3Attributes.ATTRIBUTE_PARENT).orElse(null));
    }

    // Pseudo-copying tests

    @Test
    public void removedGeneWithPseudo_childGetsPseudo() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene_1", null, "gene", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "gene_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertFalse(gff3Annotation.getFeatures().contains(gene));
        assertEquals(Optional.of("true"), cds.getAttribute(GFF3Attributes.PSEUDO));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDOGENE));
    }

    @Test
    public void removedGeneWithPseudogene_childGetsPseudogeneValue() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene_1", null, "gene", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gene.addAttribute(GFF3Attributes.PSEUDOGENE, "processed");
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "gene_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertEquals(Optional.of(List.of("processed")), cds.getAttributeList(GFF3Attributes.PSEUDOGENE));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDO));
    }

    @Test
    public void removedGeneWithPseudoAndPseudogene_childGetsPseudogeneOnly() {
        // pseudo and pseudogene are mutually exclusive, so only the more specific one is copied.
        GFF3Feature gene = TestUtils.createGFF3Feature("gene_1", null, "gene", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        gene.addAttribute(GFF3Attributes.PSEUDOGENE, "unitary");
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "gene_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertEquals(Optional.of(List.of("unitary")), cds.getAttributeList(GFF3Attributes.PSEUDOGENE));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDO));
    }

    @Test
    public void removedPseudogeneType_childGetsPseudo() {
        GFF3Feature gene =
                TestUtils.createGFF3Feature("gene_1", null, "pseudogene", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "gene_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertFalse(gff3Annotation.getFeatures().contains(gene));
        assertEquals(Optional.of("true"), cds.getAttribute(GFF3Attributes.PSEUDO));
    }

    @Test
    public void removedPseudoGene_mrnaChildGetsPseudoAndCdsKeepsItsParent() {
        // Only the unlinked child gets it; the CDS still reaches it through the mRNA.
        GFF3Feature gene = TestUtils.createGFF3Feature("gene_1", null, "gene", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature mrna =
                TestUtils.createGFF3Feature("mrna_1", "gene_1", "mRNA", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "mrna_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, mrna, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertFalse(gff3Annotation.getFeatures().contains(gene));
        assertEquals(Optional.of("true"), mrna.getAttribute(GFF3Attributes.PSEUDO));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDO));
        assertEquals(Optional.of("mrna_1"), cds.getParentId());
    }

    @Test
    public void removedPseudoGene_childWithOwnPseudoKeepsIt() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene_1", null, "gene", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gene.addAttribute(GFF3Attributes.PSEUDOGENE, "processed");
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "gene_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        cds.addAttribute(GFF3Attributes.PSEUDO, "true");
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertEquals(Optional.of(List.of("true")), cds.getAttributeList(GFF3Attributes.PSEUDO));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDOGENE));
    }

    @Test
    public void removedGeneNotPseudo_childGetsNoPseudo() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene_1", null, "gene", TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "gene_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertFalse(gff3Annotation.getFeatures().contains(gene));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDO));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDOGENE));
    }

    @Test
    public void keptPseudoGene_childUnchanged() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene_1", null, "gene", TestUtils.DEFAULT_ACCESSION, 100L, 300L);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature cds = TestUtils.createGFF3Feature(
                "cds_1", "gene_1", OntologyTerm.CDS.name(), TestUtils.DEFAULT_ACCESSION, 100L, 200L);
        gff3Annotation.setFeatures(new ArrayList<>(List.of(gene, cds)));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation, 1);

        assertTrue(gff3Annotation.getFeatures().contains(gene));
        assertFalse(cds.hasAttribute(GFF3Attributes.PSEUDO));
        assertEquals(Optional.of("gene_1"), cds.getParentId());
    }

    private GFF3Feature geneWithId(String id, long start, long end) {
        GFF3Feature gene = new GFF3Feature(
                Optional.of(id),
                Optional.empty(),
                TestUtils.DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                "gene",
                start,
                end,
                ".",
                "+",
                "");
        gene.addAttribute("ID", id);
        return gene;
    }

    private GFF3Feature cdsWithParent(String parentId, long start, long end) {
        GFF3Feature cds = new GFF3Feature(
                Optional.of("cds_1"),
                Optional.of(parentId),
                TestUtils.DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                OntologyTerm.CDS.name(),
                start,
                end,
                ".",
                "+",
                "");
        cds.addAttribute(GFF3Attributes.ATTRIBUTE_PARENT, parentId);
        return cds;
    }
}
