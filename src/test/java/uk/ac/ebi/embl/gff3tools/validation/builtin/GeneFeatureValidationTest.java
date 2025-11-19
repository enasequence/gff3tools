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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class GeneFeatureValidationTest {

    private GeneFeatureValidation geneFeatureValidation;

    private GFF3Annotation gff3Annotation;

    private GFF3Feature feature;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        geneFeatureValidation = new GeneFeatureValidation();
    }

    @Test
    public void testValidateGeneAssociationWithSameLocusTag() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene2"), GFF3Attributes.LOCUS_TAG, List.of("locus3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f3, 3));
    }

    @Test
    public void testValidateGeneAssociationWithMultipleLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene2"), GFF3Attributes.LOCUS_TAG, List.of("locus2")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of(GFF3Attributes.GENE, List.of("gene3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f3, 3));
    }

    @Test
    public void testValidateGeneAssociationWithRNAFeature() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                OntologyTerm.RRNA.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                OntologyTerm.RRNA.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus2")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), OntologyTerm.RRNA.name(), Map.of(GFF3Attributes.GENE, List.of("gene2")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f3, 3));
    }

    @Test
    public void testValidateGeneAssociationWithDifferentLocusTag() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus2")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(f2, 2));

        Assertions.assertTrue(
                ex.getMessage()
                        .contains(
                                "Features sharing gene \"gene1\" are associated with \"locus_tag\" attributes with different values (\"locus1\" and \"locus2\")"));
    }

    @Test
    public void testValidateGeneAssociationWithSamePseudoGene() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.PSEUDOGENE, List.of("pseudoGene1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.PSEUDOGENE, List.of("pseudoGene1")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene3"), GFF3Attributes.PSEUDOGENE, List.of("pseudoGene3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f3, 3));
    }

    @Test
    public void testValidateGeneAssociationWithDifferentPseudoGene() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.PSEUDOGENE, List.of("pseudoGene1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.PSEUDOGENE, List.of("pseudoGene2")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, List.of("gene2"), GFF3Attributes.PSEUDOGENE, List.of("pseudoGene3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(f2, 2));

        Assertions.assertTrue(
                ex.getMessage()
                        .contains(
                                "Features sharing gene \"gene1\" are associated with \"pseudogene\" attributes with different values (\"pseudoGene1\" and \"pseudoGene2\")"));
    }

    @Test
    public void testGeneAssociationAnnotationLevelSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                "annotation1",
                Map.of(GFF3Attributes.GENE, List.of("gene2"), GFF3Attributes.LOCUS_TAG, List.of("locus2")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation2",
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f3, 3));
    }

    @Test
    public void testAnnotationLevelFailure() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                "annotation1",
                Map.of(GFF3Attributes.GENE, List.of("gene2"), GFF3Attributes.LOCUS_TAG, List.of("locus2")));

        // No conflicts yet
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f2, 2));

        // Same annotation, same gene with different locus_tag - conflict
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus3")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(f3, 3));

        Assertions.assertTrue(
                ex.getMessage()
                        .contains(
                                "Features sharing gene \"gene1\" are associated with \"locus_tag\" attributes with different values (\"locus1\" and \"locus3\")"));

        // Different annotation - should not conflict
        GFF3Feature f4 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation2",
                Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f4, 4));
    }

    @Test
    public void testValidateGeneLocusTagAssociationWithoutGeneFeature() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PRODUCT, List.of("product")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.PRODUCT, List.of("product")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.OPERON.name(),
                OntologyTerm.OPERON.name(),
                Map.of(GFF3Attributes.PRODUCT, List.of("product")));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f2, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f3, 1));
    }

    @Test
    public void testValidateGeneLocusTagAssociationWithNoLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.PSEUDOGENE.name(),
                OntologyTerm.PSEUDOGENE.name(),
                Map.of(GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                Map.of(GFF3Attributes.PSEUDOGENE, List.of("non_processed_pseudogene")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                "unitary_pseudogene",
                "unitary_pseudogene",
                Map.of(GFF3Attributes.PSEUDOGENE, List.of("unitary_pseudogene")));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f2, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f3, 1));
    }

    @Test
    public void testValidateGeneLocusTagAssociationWithNoDuplicateLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus12"),
                        GFF3Attributes.PSEUDOGENE,
                        List.of("non_processed_pseudogene")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                "unitary_pseudogene",
                "unitary_pseudogene",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus123"),
                        GFF3Attributes.PSEUDOGENE,
                        List.of("unitary_pseudogene")));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f2, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f3, 1));
    }

    @Test
    public void testGeneLocusTagAssociationAnnotationLevelSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                "annotation1",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus12"),
                        GFF3Attributes.PSEUDOGENE,
                        List.of("non_processed_pseudogene")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f2, 2));

        GFF3Feature f3 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                "annotation2",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(f3, 3));
    }

    @Test
    public void testGeneLocusTagAssociationAnnotationLevelFailure() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                "annotation1",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus12"),
                        GFF3Attributes.PSEUDOGENE,
                        List.of("non_processed_pseudogene")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f2, 2));

        // Same annotation, same locus_tag
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateGeneLocusTagAssociation(f3, 3));

        Assertions.assertTrue(ex.getMessage()
                .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                        .formatted("locus1", f1.getName(), f3.getName())));

        // Different annotation - should not conflict
        GFF3Feature f4 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                "annotation2",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f4, 4));
    }

    @Test
    public void testValidateGeneLocusTagAssociationWithDuplicateLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.PSEUDOGENE, List.of("pseudogene")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus1"),
                        GFF3Attributes.PSEUDOGENE,
                        List.of("non_processed_pseudogene")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(f1, 1));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateGeneLocusTagAssociation(f2, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                        .formatted("locus1", f1.getName(), f2.getName())));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneAssociationSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene1")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag2"), GFF3Attributes.GENE, List.of("gene3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f3, 3));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneAssociationFailure() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag2"), GFF3Attributes.GENE, List.of("gene3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f1, 1));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateLocusTagAssociation(f2, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Features sharing locus_tag \"%s\" are associated with \"gene\" qualifiers with different values"
                                .formatted("locus_tag1")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f3, 1));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneSynonymAssociationFailure() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag1"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym3")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag1"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym4")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag2"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym5")));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f1, 1));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateLocusTagAssociation(f2, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Features sharing locus_tag \"%s\" are associated with \"gene_synonym\""
                        .formatted("locus_tag1")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f3, 1));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneNoSynonymAssociationSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag1"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym3")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag2")));

        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f3, 3));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneSynonymAssociationSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag1"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym3")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag1"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym3")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag2"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f3, 3));
    }

    @Test
    public void testValidateLocusTagAssociationAnnotationLevelSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag2"), GFF3Attributes.GENE, List.of("gene2")));

        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag1"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym3")));

        GFF3Feature f5 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                "annotation2",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));
        GFF3Feature f6 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation2",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag1"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym4")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f3, 3));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f5, 4));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f6, 5));
    }

    @Test
    public void testValidateLocusTagAssociationAnnotationLevelFailure() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene1")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag2"), GFF3Attributes.GENE, List.of("gene2")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag3"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym3")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f1, 1));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f2, 2));
        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f3, 3));

        // Same annotation annotation1 - conflicting gene for same locus_tag - should throw
        GFF3Feature f4 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                "annotation1",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateLocusTagAssociation(f4, 4));

        Assertions.assertTrue(
                ex.getMessage()
                        .contains(
                                "Features sharing locus_tag \"locus_tag1\" are associated with \"gene\" qualifiers with different values"));

        // Conflict in gene_synonym for the same annotation (annotation1)
        GFF3Feature f5 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                "annotation1",
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        List.of("locus_tag3"),
                        GFF3Attributes.GENE_SYNONYM,
                        List.of("synonym1,synonym2,synonym4")));

        ValidationException ex2 = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateLocusTagAssociation(f5, 5));

        Assertions.assertTrue(ex2.getMessage()
                .contains("Features sharing locus_tag \"locus_tag3\" are associated with \"gene_synonym\""));

        // Cross-annotation validation - should not conflict
        GFF3Feature f6 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                "annotation2",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(f6, 6));
    }
}
