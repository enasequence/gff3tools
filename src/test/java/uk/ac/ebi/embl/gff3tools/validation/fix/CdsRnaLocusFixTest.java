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
import java.util.Optional;
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
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PROTEIN_ID, List.of("protein_id"));
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 5L, 10L, attributes);
        geneAnnotation.addFeature(f1);
        cdsRnsLocusFix.fix(geneAnnotation, 1);
        Assertions.assertNotNull(f1);
        assertEquals(1, f1.getAttributeKeys().size());
    }

    @Test
    public void testFixFeatureWithGeneAndCDSWithNoOverlapLocation() {
        Map<String, List<String>> a1 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, List.of("protein_id"));
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 5L, 10L, a1);

        Map<String, List<String>> a2 = new HashMap<>();
        a2.put(GFF3Attributes.GENE, List.of("gene"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "CD001", 15L, 20L, a2);

        geneAnnotation.setFeatures(List.of(f1, f2));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertNotNull(f1);
        Assertions.assertNotNull(f2);
        assertEquals(1, f1.getAttributeKeys().size());
    }

    @Test
    public void testFixFeatureWithGeneAndCDSWithNoUpdate() {
        Map<String, List<String>> a1 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, List.of("protein_id"));
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 5L, 10L, a1);

        Map<String, List<String>> a2 = new HashMap<>();
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "CD001", 4L, 11L, a2);

        geneAnnotation.setFeatures(List.of(f1, f2));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertNotNull(f1);
        Assertions.assertNotNull(f2);
        assertEquals(1, f1.getAttributeKeys().size());
    }

    @Test
    public void testFixFeatureWithGeneAndCDSWithOverlapLocation() {
        Map<String, List<String>> a1 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, List.of("protein_id"));
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "CD001", 5L, 10L, a1);

        Map<String, List<String>> a2 = new HashMap<>();
        a2.put(GFF3Attributes.GENE, List.of("gene"));
        a2.put(GFF3Attributes.LOCUS_TAG, List.of("locus_tag"));
        a2.put(GFF3Attributes.GENE_SYNONYM, List.of("gene_synonym"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "CD001", 4L, 11L, a2);

        geneAnnotation.setFeatures(List.of(f1, f2));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertNotNull(f1);
        Assertions.assertNotNull(f2);
        assertEquals(4, f1.getAttributeKeys().size());
        assertEquals(f1.getAttributeList(GFF3Attributes.GENE), f2.getAttributeList(GFF3Attributes.GENE));
        assertEquals(
                f1.getAttributeList(GFF3Attributes.GENE_SYNONYM), f2.getAttributeList(GFF3Attributes.GENE_SYNONYM));
        assertEquals(f1.getAttributeList(GFF3Attributes.LOCUS_TAG), f2.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureWithMultipleFeaturesAndGenePropagation() {
        Map<String, List<String>> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, List.of("protein_id"));
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACCESSION1", 100L, 200L, cdsAttrs);

        Map<String, List<String>> trnaAttrs = new HashMap<>();
        trnaAttrs.put(GFF3Attributes.PRODUCT, List.of("tRNA-type"));
        GFF3Feature trna = TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), "ACCESSION1", 150L, 180L, trnaAttrs);

        Map<String, List<String>> rrna1Attrs = new HashMap<>();
        rrna1Attrs.put(GFF3Attributes.PRODUCT, List.of("16S"));
        GFF3Feature rrna1 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), "ACCESSION1", 170L, 190L, rrna1Attrs);

        Map<String, List<String>> rrna2Attrs = new HashMap<>();
        rrna2Attrs.put(GFF3Attributes.PRODUCT, List.of("23S"));
        GFF3Feature rrna2 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), "ACCESSION1", 195L, 205L, rrna2Attrs);

        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, List.of("geneX"));
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOCUS123"));
        geneAttrs.put(GFF3Attributes.GENE_SYNONYM, List.of("geneSynX"));
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACCESSION1", 100L, 210L, geneAttrs);

        geneAnnotation.setFeatures(List.of(cds, gene, trna, rrna1, rrna2, gene));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        for (GFF3Feature f : geneAnnotation.getFeatures()) {
            Assertions.assertEquals(
                    gene.getAttributeList(GFF3Attributes.GENE), f.getAttributeList(GFF3Attributes.GENE));
            Assertions.assertEquals(
                    gene.getAttributeList(GFF3Attributes.GENE_SYNONYM),
                    f.getAttributeList(GFF3Attributes.GENE_SYNONYM));
            Assertions.assertEquals(
                    gene.getAttributeList(GFF3Attributes.LOCUS_TAG), f.getAttributeList(GFF3Attributes.LOCUS_TAG));
        }

        Assertions.assertEquals(3, gene.getAttributeKeys().size());
        Assertions.assertEquals(Optional.of(List.of("geneX")), gene.getAttributeList(GFF3Attributes.GENE));
        Assertions.assertEquals(Optional.of(List.of("geneSynX")), gene.getAttributeList(GFF3Attributes.GENE_SYNONYM));
        Assertions.assertEquals(Optional.of(List.of("LOCUS123")), gene.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureDifferentAccession() {
        Map<String, List<String>> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, List.of("p1"));
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 100L, 200L, cdsAttrs);

        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, List.of("geneB"));
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOC2"));
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 90L, 210L, geneAttrs);

        geneAnnotation.setFeatures(List.of(cds));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        // Second Annotation
        geneAnnotation.setFeatures(List.of(gene));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertTrue(cds.getAttributeList(GFF3Attributes.GENE).isEmpty());
        Assertions.assertTrue(cds.getAttributeList(GFF3Attributes.LOCUS_TAG).isEmpty());
    }

    @Test
    public void testFixFeatureMultipleAccession() {
        Map<String, List<String>> cds1Attrs = new HashMap<>();
        cds1Attrs.put(GFF3Attributes.PROTEIN_ID, List.of("p1"));
        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 100L, 200L, cds1Attrs);

        Map<String, List<String>> cds2Attrs = new HashMap<>();
        cds2Attrs.put(GFF3Attributes.PROTEIN_ID, List.of("p2"));
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC2", 150L, 250L, cds2Attrs);

        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, List.of("geneC"));
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOC3"));
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 90L, 210L, geneAttrs);

        geneAnnotation.setFeatures(List.of(cds1, cds2, gene));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals(Optional.of(List.of("geneC")), cds1.getAttributeList(GFF3Attributes.GENE));
        Assertions.assertEquals(Optional.of(List.of("LOC3")), cds1.getAttributeList(GFF3Attributes.LOCUS_TAG));

        Assertions.assertTrue(cds2.getAttributeList(GFF3Attributes.GENE).isEmpty());
        Assertions.assertTrue(cds2.getAttributeList(GFF3Attributes.LOCUS_TAG).isEmpty());
    }

    @Test
    public void testFixFeatureOverlapDifferentAccession() {
        Map<String, List<String>> cds1Attrs = new HashMap<>();
        cds1Attrs.put(GFF3Attributes.PROTEIN_ID, List.of("p1"));
        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 100L, 200L, cds1Attrs);

        Map<String, List<String>> cds2Attrs = new HashMap<>();
        cds2Attrs.put(GFF3Attributes.PROTEIN_ID, List.of("p2"));
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC2", 150L, 180L, cds2Attrs);

        Map<String, List<String>> gene1Attrs = new HashMap<>();
        gene1Attrs.put(GFF3Attributes.GENE, List.of("gene1"));
        gene1Attrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOC1"));
        GFF3Feature gene1 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 90L, 210L, gene1Attrs);

        Map<String, List<String>> gene2Attrs = new HashMap<>();
        gene2Attrs.put(GFF3Attributes.GENE, List.of("gene2"));
        gene2Attrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOC2"));
        GFF3Feature gene2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC2", 140L, 190L, gene2Attrs);

        geneAnnotation.setFeatures(List.of(cds1, gene1));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        geneAnnotation.setFeatures(List.of(cds2, gene2));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals(Optional.of(List.of("gene1")), cds1.getAttributeList(GFF3Attributes.GENE));
        Assertions.assertEquals(Optional.of(List.of("LOC1")), cds1.getAttributeList(GFF3Attributes.LOCUS_TAG));

        Assertions.assertEquals(Optional.of(List.of("gene2")), cds2.getAttributeList(GFF3Attributes.GENE));
        Assertions.assertEquals(Optional.of(List.of("LOC2")), cds2.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildAfterAllGenes() {

        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, List.of("geneX"));
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOCX"));
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, geneAttrs);

        Map<String, List<String>> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, List.of("p1"));
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 250L, cdsAttrs);

        // Child appears AFTER gene in ordering
        geneAnnotation.setFeatures(List.of(gene, cds));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals(Optional.of(List.of("geneX")), cds.getAttributeList(GFF3Attributes.GENE));
        Assertions.assertEquals(Optional.of(List.of("LOCX")), cds.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildBeforeGene() {

        Map<String, List<String>> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, List.of("p1"));
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 250L, cdsAttrs);

        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, List.of("geneY"));
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOCY"));
        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, geneAttrs);

        // Child first, then gene
        geneAnnotation.setFeatures(List.of(cds, gene));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals(Optional.of(List.of("geneY")), cds.getAttributeList(GFF3Attributes.GENE));
        Assertions.assertEquals(Optional.of(List.of("LOCY")), cds.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureMultipleGeneOverlapFirstMatch() {

        Map<String, List<String>> cdsAttrs = new HashMap<>();
        cdsAttrs.put(GFF3Attributes.PROTEIN_ID, List.of("p1"));
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 200L, cdsAttrs);

        Map<String, List<String>> gene1Attrs = Map.of(
                GFF3Attributes.GENE, List.of("gene1"),
                GFF3Attributes.LOCUS_TAG, List.of("LOC1"));
        GFF3Feature gene1 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, gene1Attrs);

        Map<String, List<String>> gene2Attrs = Map.of(
                GFF3Attributes.GENE, List.of("gene2"),
                GFF3Attributes.LOCUS_TAG, List.of("LOC2"));
        GFF3Feature gene2 = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 120L, 220L, gene2Attrs);

        // Both overlap, but gene1 appears first
        geneAnnotation.setFeatures(List.of(gene1, gene2, cds));
        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals(Optional.of(List.of("gene1")), cds.getAttributeList(GFF3Attributes.GENE));
        Assertions.assertEquals(Optional.of(List.of("LOC1")), cds.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildrenAroundGenes() {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 105L, 110L, new HashMap<>());
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 180L, 190L, new HashMap<>());

        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, List.of("geneZ"));
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOCZ"));

        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 200L, geneAttrs);

        GFF3Feature cds3 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 120L, 150L, new HashMap<>());

        geneAnnotation.setFeatures(List.of(cds1, cds2, gene, cds3));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals(Optional.of(List.of("LOCZ")), cds1.getAttributeList(GFF3Attributes.LOCUS_TAG));
        Assertions.assertEquals(Optional.of(List.of("LOCZ")), cds2.getAttributeList(GFF3Attributes.LOCUS_TAG));
        Assertions.assertEquals(Optional.of(List.of("LOCZ")), cds3.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }

    @Test
    public void testFixFeatureChildAfterLastGeneFailsInOldApproach() {

        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GFF3Attributes.GENE, List.of("geneX"));
        geneAttrs.put(GFF3Attributes.LOCUS_TAG, List.of("LOCX"));

        GFF3Feature gene = TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), "ACC1", 100L, 300L, geneAttrs);

        GFF3Feature cds1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 150L, 200L, new HashMap<>());
        GFF3Feature cds2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), "ACC1", 180L, 250L, new HashMap<>());

        // cds2 appears AFTER all genes
        geneAnnotation.setFeatures(List.of(gene, cds1, cds2));

        cdsRnsLocusFix.fix(geneAnnotation, 1);

        Assertions.assertEquals(Optional.of(List.of("LOCX")), cds2.getAttributeList(GFF3Attributes.LOCUS_TAG));
    }
}
