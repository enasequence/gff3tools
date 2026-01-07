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

import java.lang.reflect.Field;
import java.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class GeneSynonymFixTest {

    // Use the same key as the fix class uses
    private static final String GENE_SYNONYM = GFF3Attributes.GENE_SYNONYM;

    /**
     * Helper to create a GeneSynonymFix with a controlled GENE_FEATURES set.
     * This bypasses the static FeatureMapping call in the constructor.
     */
    private GeneSynonymFix mockGff3GeneFeatureList(List<String> geneNamesOrIds, List<String> geneLike) {
        GeneSynonymFix fix = new GeneSynonymFix();
        try {
            Field field = GeneSynonymFix.class.getDeclaredField("GENE_FEATURES");
            Field field1 = GeneSynonymFix.class.getDeclaredField("GENELIKE_FEATURES");
            field.setAccessible(true);
            field1.setAccessible(true);
            HashSet<String> value = new HashSet<>(geneNamesOrIds);
            HashSet<String> value1 = new HashSet<>(geneLike);
            field.set(fix, value);
            field1.set(fix, value1);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject GENE_FEATURES of GENELIKE_FEATURES into GeneSynonymFix", e);
        }
        return fix;
    }

    @Test
    void movesGeneSynonymFromChildToGeneAncestor() {
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of("gene1"), List.of("cds1"));
        // parent gene feature
        Map<String, List<String>> geneAttrs = new HashMap<>();
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", geneAttrs);
        // child feature with gene_synonym
        Map<String, List<String>> childAttrs = new HashMap<>();
        childAttrs.put(GENE_SYNONYM, new ArrayList<>(List.of("syn1", "syn2")));
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", childAttrs);
        // link up the family
        cds.setParent(gene);
        gene.addChild(cds);
        // build annotation
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.addFeature(gene);
        annotation.addFeature(cds);

        // Run fix
        fix.fix(annotation, 0);

        List<GFF3Feature> result = annotation.getFeatures();
        assertEquals(2, result.size());
        // order should be preserved
        assertSame(gene, result.get(0));
        assertSame(cds, result.get(1));
        // gene_synonym moved to parent gene and removed from child
        assertTrue(gene.hasAttribute(GENE_SYNONYM));
        assertEquals(
                List.of("syn1", "syn2"), gene.getAttributeList(GENE_SYNONYM).orElse(new ArrayList<>()));
        assertFalse(cds.hasAttribute(GENE_SYNONYM));
    }

    @Test
    void movesGeneSynonymFromChildToGenelikeAncestor() {
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of("gene1"), List.of("ncRNA_gene"));
        // parent gene feature
        Map<String, List<String>> geneLikeAttrs = new HashMap<>();
        GFF3Feature genelike = TestUtils.createGFF3Feature("ncRNA_gene", geneLikeAttrs);
        // child feature with gene_synonym
        Map<String, List<String>> childAttrs = new HashMap<>();
        childAttrs.put(GENE_SYNONYM, new ArrayList<>(List.of("syn1", "syn2")));
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", childAttrs);
        // link up the family
        cds.setParent(genelike);
        genelike.addChild(cds);
        // build annotation
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.addFeature(genelike);
        annotation.addFeature(cds);

        // Run fix
        fix.fix(annotation, 0);

        List<GFF3Feature> result = annotation.getFeatures();
        assertEquals(2, result.size());
        // order should be preserved
        assertSame(genelike, result.get(0));
        assertSame(cds, result.get(1));
        // gene_synonym moved to parent gene and removed from child
        assertTrue(genelike.hasAttribute(GENE_SYNONYM));
        assertEquals(
                List.of("syn1", "syn2"), genelike.getAttributeList(GENE_SYNONYM).orElse(new ArrayList<>()));
        assertFalse(cds.hasAttribute(GENE_SYNONYM));
    }

    @Test
    void doesNotOverwriteExistingParentGeneSynonym() {
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of("gene1"), List.of());
        // parent gene already has canonical gene_synonym
        Map<String, List<String>> geneAttrs = new HashMap<>();
        geneAttrs.put(GENE_SYNONYM, new ArrayList<>(List.of("parentSyn")));
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", geneAttrs);
        // child feature with a different synonym list
        Map<String, List<String>> childAttrs = new HashMap<>();
        childAttrs.put(GENE_SYNONYM, new ArrayList<>(List.of("childSyn")));
        GFF3Feature cds = TestUtils.createGFF3Feature("something", "gene1", childAttrs);
        // link up the family
        cds.setParent(gene);
        gene.addChild(cds);
        // set up the annotation
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.addFeature(gene);
        annotation.addFeature(cds);

        fix.fix(annotation, 0);

        // Parent keeps its original synonyms and child loses its synonyms
        assertTrue(gene.hasAttribute(GENE_SYNONYM));
        assertEquals(List.of("parentSyn"), gene.getAttributeList(GENE_SYNONYM).orElse(new ArrayList<>()));
        assertFalse(cds.hasAttribute(GENE_SYNONYM));
    }

    @Test
    void fallsBackToOldestAncestorWithSameLocationWhenNoGeneAncestor() {
        // Empty gene feature set → findGeneAncestor will always return null
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of("gene1"), List.of());
        // All three share same location (start/end) so fallback should climb to the root
        Map<String, List<String>> rootAttrs = new HashMap<>();
        GFF3Feature root = TestUtils.createGFF3Feature("root", 1, 800, rootAttrs);
        Map<String, List<String>> midAttrs = new HashMap<>();
        GFF3Feature mid = TestUtils.createGFF3Feature("mid", "root", TestUtils.DEFAULT_ACCESSION, midAttrs);
        Map<String, List<String>> leafAttrs = new HashMap<>();
        leafAttrs.put(GENE_SYNONYM, new ArrayList<>(List.of("leafSyn")));
        GFF3Feature leaf = TestUtils.createGFF3Feature("leaf", "mid", TestUtils.DEFAULT_ACCESSION, leafAttrs);
        // Set up the family
        mid.setParent(root);
        root.addChild(mid);
        leaf.setParent(mid);
        mid.addChild(leaf);
        // build annotation
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.addFeature(root);
        annotation.addFeature(mid);
        annotation.addFeature(leaf);

        fix.fix(annotation, 0);

        // should have pushed gene_synonym to the top-most ancestor with same location (root)
        assertTrue(root.hasAttribute(GENE_SYNONYM));
        assertEquals(List.of("leafSyn"), root.getAttributeList(GENE_SYNONYM).orElse(new ArrayList<>()));
        assertFalse(leaf.hasAttribute(GENE_SYNONYM));
    }

    @Test
    void skipsFeaturesWhoseNameIsInGeneFeatures() {
        // Here we treat the accession / seqId as a "gene feature", so the condition
        // !GENE_FEATURES.contains(f.getSeqId()) should fail and the fix should skip.
        String geneName = "gene1";
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of(geneName), List.of());
        // region parent feature
        Map<String, List<String>> geneAttrs = new HashMap<>();
        GFF3Feature region = TestUtils.createGFF3Feature("region", geneAttrs);
        // gene with gene synonyms feature - child of region
        Map<String, List<String>> childAttrs = new HashMap<>();
        childAttrs.put(GENE_SYNONYM, new ArrayList<>(List.of("syn1")));
        GFF3Feature genefeature = TestUtils.createGFF3Feature(geneName, "gene1", childAttrs);
        // link up the family
        genefeature.setParent(region);
        region.addChild(genefeature);
        // build anotation
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.addFeature(region);
        annotation.addFeature(genefeature);

        fix.fix(annotation, 0);

        // Because seqId is treated as a "gene feature", no movement should occur
        assertFalse(region.hasAttribute(GENE_SYNONYM));
        assertTrue(genefeature.hasAttribute(GENE_SYNONYM));
        assertEquals(List.of("syn1"), genefeature.getAttributeList(GENE_SYNONYM).orElse(new ArrayList<>()));
    }

    @Test
    void skipsFeaturesWhoseNameIsInGeneLikeFeaturesAndHaveNoGeneParent() {
        // Here we treat the accession / seqId as a "gene feature", so the condition
        // !GENE_FEATURES.contains(f.getSeqId()) should fail and the fix should skip.
        String rnaFeature = "ncRNA";
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of("gene1"), List.of(rnaFeature));
        // region parent feature
        Map<String, List<String>> geneAttrs = new HashMap<>();
        GFF3Feature region = TestUtils.createGFF3Feature("region", geneAttrs);
        // gene with gene synonyms feature - child of region
        Map<String, List<String>> childAttrs = new HashMap<>();
        childAttrs.put(GENE_SYNONYM, new ArrayList<>(List.of("syn1")));
        GFF3Feature genefeature = TestUtils.createGFF3Feature(rnaFeature, "", childAttrs);
        // link up the family
        genefeature.setParent(region);
        region.addChild(genefeature);
        // build anotation
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.addFeature(region);
        annotation.addFeature(genefeature);

        fix.fix(annotation, 0);

        // Because seqId is treated as a "gene feature", no movement should occur
        assertFalse(region.hasAttribute(GENE_SYNONYM));
        assertTrue(genefeature.hasAttribute(GENE_SYNONYM));
        assertEquals(List.of("syn1"), genefeature.getAttributeList(GENE_SYNONYM).orElse(new ArrayList<>()));
    }

    @Test
    void findGeneAncestorReturnsGeneWhenNameInGeneFeatures() {
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of("gene1"), List.of("ncRNA"));

        Map<String, List<String>> geneAttrs = new HashMap<>();
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", geneAttrs);

        Map<String, List<String>> mrnaAttrs = new HashMap<>();
        GFF3Feature mrna = TestUtils.createGFF3Feature("ncRNA", "gene1", geneAttrs);

        Map<String, List<String>> childAttrs = new HashMap<>();
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "ncRNA", childAttrs);

        mrna.setParent(gene);
        cds.setParent(mrna);
        gene.addChild(mrna);
        mrna.addChild(cds);

        GFF3Feature ancestor = fix.findGeneAncestor(cds);
        assertNotNull(ancestor);
        assertSame(gene, ancestor);
    }

    @Test
    void findGeneAncestorReturnsGeneLikeWhenNameInGeneLikeFeatures() {
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of("gene1"), List.of("ncRNA"));

        Map<String, List<String>> somethingelse = new HashMap<>();
        GFF3Feature parnt = TestUtils.createGFF3Feature("somethingelse", somethingelse);

        Map<String, List<String>> mrnaAttrs = new HashMap<>();
        GFF3Feature mrna = TestUtils.createGFF3Feature("ncRNA", "somethingelse", mrnaAttrs);

        Map<String, List<String>> childAttrs = new HashMap<>();
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "ncRNA", childAttrs);

        mrna.setParent(parnt);
        cds.setParent(mrna);
        parnt.addChild(mrna);
        mrna.addChild(cds);

        GFF3Feature ancestor = fix.findLikeGeneAncestor(cds);
        assertNotNull(ancestor);
        assertSame(mrna, ancestor);
    }

    @Test
    void findOldestAncestorWithSameLocationClimbsToRoot() {
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of(), List.of());

        GFF3Feature root = TestUtils.createGFF3Feature("root", 1, 800); // climbs up to here
        GFF3Feature mid = TestUtils.createGFF3Feature("mid", 1, 800);
        GFF3Feature leaf = TestUtils.createGFF3Feature("leaf", 1, 800);

        mid.setParent(root);
        root.addChild(mid);
        leaf.setParent(mid);
        mid.addChild(leaf);

        GFF3Feature oldest = fix.findOldestAncestorWithSameLocation(leaf);
        assertSame(root, oldest);
    }

    @Test
    void findOldestAncestorWithSameLocationStopsWhenLocationDiffers() {
        GeneSynonymFix fix = mockGff3GeneFeatureList(List.of(), List.of());

        GFF3Feature root = TestUtils.createGFF3Feature("root", 1, 800);
        GFF3Feature mid = TestUtils.createGFF3Feature("mid", 100, 900); // climbs up to here
        GFF3Feature leaf = TestUtils.createGFF3Feature("leaf", 100, 900);

        mid.setParent(root);
        root.addChild(mid);
        leaf.setParent(mid);
        mid.addChild(leaf);

        GFF3Feature oldest = fix.findOldestAncestorWithSameLocation(leaf);
        assertSame(mid, oldest);
    }
}
