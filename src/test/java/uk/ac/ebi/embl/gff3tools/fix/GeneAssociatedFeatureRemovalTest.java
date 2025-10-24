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
package uk.ac.ebi.embl.gff3tools.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class GeneAssociatedFeatureRemovalTest {

    GFF3Annotation gff3Annotation;

    private GeneAssociatedFeatureRemoval geneAssociatedFeatureRemoval;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        geneAssociatedFeatureRemoval = new GeneAssociatedFeatureRemoval();
    }

    @Test
    public void testFixAnnotationWithoutGene() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 100L, 200L);
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), 101L, 300L);
        GFF3Feature f3 = TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), 301L, 400L);

        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation);
        assertEquals(3, gff3Annotation.getFeatures().size());
    }

    @Test
    public void testFixAnnotationWithGeneOnly() {
        GFF3Feature f1 = TestUtils.createGFF3Feature("intron", 201L, 300L);

        GFF3Feature f2 = TestUtils.createGFF3Feature("gene", 301L, 400L);

        gff3Annotation.setFeatures(List.of(f1, f2));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation);
        assertEquals(2, gff3Annotation.getFeatures().size());
        assertTrue(gff3Annotation.getFeatures().contains(f2));
    }

    @Test
    public void testFixAnnotationWithGeneDifferentLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 100L, 200L);
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), 201L, 300L);
        GFF3Feature f3 = TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), 301L, 400L);
        GFF3Feature f4 = TestUtils.createGFF3Feature("gene", 401L, 500L);

        gff3Annotation.setFeatures(List.of(f1, f2, f3, f4));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation);
        assertEquals(4, gff3Annotation.getFeatures().size());
        assertTrue(gff3Annotation.getFeatures().contains(f4));
    }

    @Test
    public void testFixAnnotationWithGeneSameLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 100L, 200L);
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.RRNA.name(), 201L, 300L);
        GFF3Feature f3 = TestUtils.createGFF3Feature(OntologyTerm.TRNA.name(), 301L, 400L);
        GFF3Feature f4 = TestUtils.createGFF3Feature("gene", 301L, 400L);

        gff3Annotation.setFeatures(List.of(f1, f2, f3, f4));

        geneAssociatedFeatureRemoval.fixAnnotation(gff3Annotation);
        assertEquals(3, gff3Annotation.getFeatures().size());
        assertFalse(gff3Annotation.getFeatures().contains(f4));
    }
}
