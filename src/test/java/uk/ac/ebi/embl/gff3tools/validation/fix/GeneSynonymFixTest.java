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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

class GeneSynonymFixTest {

    private String LOCUS_TAG;
    private String GENE;
    private String GENE_SYNONYM;

    private GeneSynonymFix fixer;

    @BeforeEach
    void setUp() {
        LOCUS_TAG = GFF3Attributes.LOCUS_TAG;
        GENE = GFF3Attributes.GENE;
        GENE_SYNONYM = GFF3Attributes.GENE_SYNONYM;

        fixer = new GeneSynonymFix();
    }

    /* ---------- helpers ---------- */

    private static GFF3Annotation annotationWith(GFF3Feature... features) {
        GFF3Annotation ann = Mockito.mock(GFF3Annotation.class);
        when(ann.getAllFeatures()).thenReturn(Arrays.asList(features));
        return ann;
    }

    private static Map<String, Object> attrs(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static List<String> list(String... xs) {
        return new ArrayList<>(Arrays.asList(xs));
    }

    /* ---------- tests ---------- */

    @Test
    void harmonizesAcrossIdentifier_whenCdsSetsMaster() {
        // CDS defines master LT_001 -> ["foo","bar"]
        GFF3Feature cds =
                TestUtils.createGFF3Feature("CDS", "tx1", attrs(LOCUS_TAG, "LT_001", GENE_SYNONYM, list("foo", "bar")));
        // gene missing "bar"
        GFF3Feature gene =
                TestUtils.createGFF3Feature("gene", "root", attrs(LOCUS_TAG, "LT_001", GENE_SYNONYM, list("foo")));
        // mRNA has extra "baz"
        GFF3Feature mrna = TestUtils.createGFF3Feature(
                "mRNA", "gene1", attrs(LOCUS_TAG, "LT_001", GENE_SYNONYM, list("foo", "baz")));

        GFF3Annotation ann = annotationWith(cds, gene, mrna);

        fixer.fix(ann, 1);

        assertEquals(list("foo", "bar"), cds.getAttributeValueList(GENE_SYNONYM));
        assertEquals(list("foo", "bar"), gene.getAttributeValueList(GENE_SYNONYM)); // "bar" added
        assertEquals(list("foo", "bar"), mrna.getAttributeValueList(GENE_SYNONYM)); // "baz" removed, "bar" added
    }

    @Test
    void skipsIdentifier_whenCdsMasterListsConflictByOrder() {
        // Two CDS with same identifier but different order => unreliable => no changes
        GFF3Feature cds1 =
                TestUtils.createGFF3Feature("CDS", "tx1", attrs(LOCUS_TAG, "LT_999", GENE_SYNONYM, list("foo", "bar")));
        GFF3Feature cds2 =
                TestUtils.createGFF3Feature("CDS", "tx2", attrs(LOCUS_TAG, "LT_999", GENE_SYNONYM, list("bar", "foo")));
        // A bystander feature that would have been harmonized if not unreliable
        GFF3Feature gene =
                TestUtils.createGFF3Feature("gene", "root", attrs(LOCUS_TAG, "LT_999", GENE_SYNONYM, list("foo")));

        GFF3Annotation ann = annotationWith(cds1, cds2, gene);

        fixer.fix(ann, 1);

        // Nothing changes due to unreliability
        assertEquals(list("foo", "bar"), cds1.getAttributeValueList(GENE_SYNONYM));
        assertEquals(list("bar", "foo"), cds2.getAttributeValueList(GENE_SYNONYM));
        assertEquals(list("foo"), gene.getAttributeValueList(GENE_SYNONYM));
    }

    @Test
    void seedsMasterFromFirstSeenFeature_whenNoCdsPresent() {
        // No CDS for LT_010; first seen among candidates seeds master (order-dependent)
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene", "root", attrs(LOCUS_TAG, "LT_010", GENE_SYNONYM, list("alpha", "beta")));
        GFF3Feature mrna =
                TestUtils.createGFF3Feature("mRNA", "gene1", attrs(LOCUS_TAG, "LT_010", GENE_SYNONYM, list("alpha")));

        // Order matters: put 'gene' first so it seeds the master
        GFF3Annotation ann = annotationWith(gene, mrna);

        fixer.fix(ann, 1);

        assertEquals(list("alpha", "beta"), gene.getAttributeValueList(GENE_SYNONYM));
        assertEquals(list("alpha", "beta"), mrna.getAttributeValueList(GENE_SYNONYM));
    }

    @Test
    void removesNonMasterSynonyms_andAddsMissing_preservingMasterOrder() {
        GFF3Feature cds =
                TestUtils.createGFF3Feature("CDS", "tx1", attrs(GENE, "yaaA", GENE_SYNONYM, list("s1", "s2", "s3")));
        GFF3Feature mrna = TestUtils.createGFF3Feature(
                "mRNA", "gene1", attrs(GENE, "yaaA", GENE_SYNONYM, list("s2", "extra", "s1")));

        GFF3Annotation ann = annotationWith(cds, mrna);

        fixer.fix(ann, 1);

        // mRNA should be normalized to [s2, s1, s3]? NO — fixer keeps existing order and appends missing in master
        // order,
        // then removes extras. That yields current = ["s2","s1"] + add "s3" => ["s2","s1","s3"]
        assertEquals(list("s1", "s2", "s3"), cds.getAttributeValueList(GENE_SYNONYM));
        assertEquals(list("s2", "s1", "s3"), mrna.getAttributeValueList(GENE_SYNONYM));
    }

    @Test
    void masterEmptyList_purgesSynonymsOnOthers() {
        // CDS with empty gene_synonym establishes empty master; others get stripped
        GFF3Feature cds = TestUtils.createGFF3Feature("CDS", "tx1", attrs(LOCUS_TAG, "LT_777", GENE_SYNONYM, list()));
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene", "root", attrs(LOCUS_TAG, "LT_777", GENE_SYNONYM, list("keep", "me")));

        GFF3Annotation ann = annotationWith(cds, gene);

        fixer.fix(ann, 1);

        assertEquals(list(), cds.getAttributeValueList(GENE_SYNONYM));
        assertEquals(list(), gene.getAttributeValueList(GENE_SYNONYM)); // removed to match empty master
    }

    @Test
    void ignoresFeaturesWithoutIdentifier_andHandlesNoCandidates() {
        GFF3Feature noKeys = TestUtils.createGFF3Feature("misc_feature", "root", attrs(GENE_SYNONYM, list("a")));

        GFF3Annotation ann = annotationWith(noKeys);

        assertDoesNotThrow(() -> fixer.fix(ann, 1));
        // unchanged because not a candidate (no locus_tag/gene)
        assertEquals(list("a"), noKeys.getAttributeValueList(GENE_SYNONYM));
    }

    @Test
    void doesNothing_onNullAnnotation() {
        assertDoesNotThrow(() -> fixer.fix(null, 1));
    }
}
