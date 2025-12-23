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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class CdsRnaLocusFixTest {

    GFF3Annotation geneAnnotation;

    private CdsRnaLocusFix cdsRnsLocusFix;

    @BeforeEach
    public void setUp() {
        cdsRnsLocusFix = new CdsRnaLocusFix();
        geneAnnotation = new GFF3Annotation();
    }

    @Test
    public void testFixFeatureWithNoGenes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PROTEIN_ID, "protein_id");
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 5L, 10L, attributes);
        geneAnnotation.addFeature(f1);
        cdsRnsLocusFix.fix(geneAnnotation, 1);
        Assertions.assertNotNull(f1);
        assertEquals(1, f1.getAttributes().size());
    }

    @Test
    public void testFixFeatureWithGeneAndCDSWithNoOverlapLocation() {
        Map<String, Object> a1 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, "protein_id");
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 5L, 10L, a1);

        Map<String, Object> a2 = new HashMap<>();
        a2.put(GFF3Attributes.GENE, "gene");
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "CD001", 15L, 20L, a2);

        geneAnnotation.setFeatures(List.of(f1, f2));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertNotNull(f1);
        Assertions.assertNotNull(f2);
        assertEquals(1, f1.getAttributes().size());
    }

    @Test
    public void testFixFeatureWithGeneAndCDSWithNoUpdate() {
        Map<String, Object> a1 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, "protein_id");
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 5L, 10L, a1);

        Map<String, Object> a2 = new HashMap<>();
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "CD001", 4L, 11L, a2);

        geneAnnotation.setFeatures(List.of(f1, f2));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertNotNull(f1);
        Assertions.assertNotNull(f2);
        assertEquals(1, f1.getAttributes().size());
    }

    @Test
    public void testFixFeatureWithGeneAndCDSWithOverlapLocation() {
        Map<String, Object> a1 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, "protein_id");
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 300L, 350L, a1);

        Map<String, Object> a2 = new HashMap<>();
        a2.put(GFF3Attributes.GENE, "gene");
        a2.put(GFF3Attributes.LOCUS_TAG, "locus_tag");
        a2.put(GFF3Attributes.GENE_SYNONYM, "gene_synonym");
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "CD001", 200L, 400L, a2);

        geneAnnotation.setFeatures(List.of(f1, f2));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertNotNull(f1);
        Assertions.assertNotNull(f2);
        assertEquals(4, f1.getAttributes().size());
        assertEquals(f1.getAttributeByName(GFF3Attributes.GENE), f2.getAttributeByName(GFF3Attributes.GENE));
        assertEquals(
                f1.getAttributeByName(GFF3Attributes.GENE_SYNONYM), f2.getAttributeByName(GFF3Attributes.GENE_SYNONYM));
        assertEquals(f1.getAttributeByName(GFF3Attributes.LOCUS_TAG), f2.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureWithMultipleFeaturesAndGenePropagation() {
        Map<String, Object> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, "protein_id");
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACCESSION1", 100L, 200L, cdsAttrs);

        Map<String, Object> trnaAttrs = new HashMap<>();
        trnaAttrs.put(GFF3Attributes.PRODUCT, "tRNA-type");
        GFF3Feature trna = TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), "ACCESSION1", 150L, 180L, trnaAttrs);

        Map<String, Object> rrna1Attrs = new HashMap<>();
        rrna1Attrs.put(GFF3Attributes.PRODUCT, "16S");
        GFF3Feature rrna1 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), "ACCESSION1", 170L, 190L, rrna1Attrs);

        Map<String, Object> rrna2Attrs = new HashMap<>();
        rrna2Attrs.put(GFF3Attributes.PRODUCT, "23S");
        GFF3Feature rrna2 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), "ACCESSION1", 195L, 205L, rrna2Attrs);

        Map<String, Object> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, "geneX");
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, "LOCUS123");
        geneAttrs.put(GFF3Attributes.GENE_SYNONYM, "geneSynX");
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACCESSION1", 100L, 210L, geneAttrs);

        geneAnnotation.setFeatures(List.of(cds, gene, trna, rrna1, rrna2, gene));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        for (GFF3Feature f : geneAnnotation.getFeatures()) {
            Assertions.assertEquals(
                    gene.getAttributeByName(GFF3Attributes.GENE), f.getAttributeByName(GFF3Attributes.GENE));
            Assertions.assertEquals(
                    gene.getAttributeByName(GFF3Attributes.GENE_SYNONYM),
                    f.getAttributeByName(GFF3Attributes.GENE_SYNONYM));
            Assertions.assertEquals(
                    gene.getAttributeByName(GFF3Attributes.LOCUS_TAG), f.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        }

        Assertions.assertEquals(3, gene.getAttributes().size());
        Assertions.assertEquals("geneX", gene.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertEquals("geneSynX", gene.getAttributeByName(GFF3Attributes.GENE_SYNONYM));
        Assertions.assertEquals("LOCUS123", gene.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureDifferentAccession() {
        Map<String, Object> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, "p1");
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 100L, 200L, cdsAttrs);

        Map<String, Object> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, "geneB");
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, "LOC2");
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 90L, 210L, geneAttrs);

        geneAnnotation.setFeatures(List.of(cds));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        // Second Annotation
        geneAnnotation.setFeatures(List.of(gene));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertNull(cds.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertNull(cds.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureMultipleAccession() {
        Map<String, Object> cds1Attrs = new HashMap<>();
        cds1Attrs.put(GFF3Attributes.PROTEIN_ID, "p1");
        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 100L, 200L, cds1Attrs);

        Map<String, Object> cds2Attrs = new HashMap<>();
        cds2Attrs.put(GFF3Attributes.PROTEIN_ID, "p2");
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC2", 150L, 250L, cds2Attrs);

        Map<String, Object> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, "geneC");
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, "LOC3");
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 90L, 210L, geneAttrs);

        geneAnnotation.setFeatures(List.of(cds1, cds2, gene));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals("geneC", cds1.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertEquals("LOC3", cds1.getAttributeByName(GFF3Attributes.LOCUS_TAG));

        Assertions.assertNull(cds2.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertNull(cds2.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureOverlapDifferentAccession() {
        Map<String, Object> cds1Attrs = new HashMap<>();
        cds1Attrs.put(GFF3Attributes.PROTEIN_ID, "p1");
        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 100L, 200L, cds1Attrs);

        Map<String, Object> cds2Attrs = new HashMap<>();
        cds2Attrs.put(GFF3Attributes.PROTEIN_ID, "p2");
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC2", 150L, 180L, cds2Attrs);

        Map<String, Object> gene1Attrs = new HashMap<>();
        gene1Attrs.put(GFF3Attributes.GENE, "gene1");
        gene1Attrs.put(GFF3Attributes.LOCUS_TAG, "LOC1");
        GFF3Feature gene1 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 90L, 210L, gene1Attrs);

        Map<String, Object> gene2Attrs = new HashMap<>();
        gene2Attrs.put(GFF3Attributes.GENE, "gene2");
        gene2Attrs.put(GFF3Attributes.LOCUS_TAG, "LOC2");
        GFF3Feature gene2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC2", 140L, 190L, gene2Attrs);

        geneAnnotation.setFeatures(List.of(cds1, gene1));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        geneAnnotation.setFeatures(List.of(cds2, gene2));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals("gene1", cds1.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertEquals("LOC1", cds1.getAttributeByName(GFF3Attributes.LOCUS_TAG));

        Assertions.assertEquals("gene2", cds2.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertEquals("LOC2", cds2.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildAfterAllGenes() {

        Map<String, Object> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, "geneX");
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, "LOCX");
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, geneAttrs);

        Map<String, Object> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, "p1");
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 250L, cdsAttrs);

        // Child appears AFTER gene in ordering
        geneAnnotation.setFeatures(List.of(gene, cds));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals("geneX", cds.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertEquals("LOCX", cds.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildBeforeGene() {

        Map<String, Object> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, "p1");
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 250L, cdsAttrs);

        Map<String, Object> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, "geneY");
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, "LOCY");
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, geneAttrs);

        // Child first, then gene
        geneAnnotation.setFeatures(List.of(cds, gene));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals("geneY", cds.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertEquals("LOCY", cds.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureMultipleGeneOverlapFirstMatch() {

        Map<String, Object> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, "p1");
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 200L, cdsAttrs);

        Map<String, Object> gene1Attrs = Map.of(
                GFF3Attributes.GENE, "gene1",
                GFF3Attributes.LOCUS_TAG, "LOC1");
        GFF3Feature gene1 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, gene1Attrs);

        Map<String, Object> gene2Attrs = Map.of(
                GFF3Attributes.GENE, "gene2",
                GFF3Attributes.LOCUS_TAG, "LOC2");
        GFF3Feature gene2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 120L, 220L, gene2Attrs);

        // Both overlap, but gene1 appears first
        geneAnnotation.setFeatures(List.of(gene1, gene2, cds));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals("gene1", cds.getAttributeByName(GFF3Attributes.GENE));
        Assertions.assertEquals("LOC1", cds.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildrenAroundGenes() {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 105L, 110L, new HashMap<>());
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 180L, 190L, new HashMap<>());

        Map<String, Object> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, "geneZ");
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, "LOCZ");

        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 200L, geneAttrs);

        GFF3Feature cds3 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 120L, 150L, new HashMap<>());

        geneAnnotation.setFeatures(List.of(cds1, cds2, gene, cds3));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals("LOCZ", cds1.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        Assertions.assertEquals("LOCZ", cds2.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        Assertions.assertEquals("LOCZ", cds3.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildAfterLastGeneFailsInOldApproach() {

        Map<String, Object> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, "geneX");
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, "LOCX");

        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, geneAttrs);

        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 200L, new HashMap<>());
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 180L, 250L, new HashMap<>());

        // cds2 appears AFTER all genes
        geneAnnotation.setFeatures(List.of(gene, cds1, cds2));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals("LOCX", cds2.getAttributeByName(GFF3Attributes.LOCUS_TAG));
    }
}
