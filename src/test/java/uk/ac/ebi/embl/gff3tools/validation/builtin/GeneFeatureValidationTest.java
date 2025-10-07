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
    public void testValidateGeneLocusTagAssociationWithoutGeneFeature() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PRODUCT, "product"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.PRODUCT, "product"));
        GFF3Feature opr = TestUtils.createGFF3Feature(
                OntologyTerm.OPERON.name(), OntologyTerm.OPERON.name(), Map.of(GFF3Attributes.PRODUCT, "product"));
        gff3Annotation.setFeatures(List.of(cds, sig, opr));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneLocusTagAssociationWithNoLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.PSEUDOGENE.name(),
                OntologyTerm.PSEUDOGENE.name(),
                Map.of(GFF3Attributes.PSEUDOGENE, "pseudogene"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                Map.of(GFF3Attributes.PSEUDOGENE, "non_processed_pseudogene"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                "unitary_pseudogene", "unitary_pseudogene", Map.of(GFF3Attributes.PSEUDOGENE, "unitary_pseudogene"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneLocusTagAssociationWithNoDuplicateLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, "locus1", GFF3Attributes.PSEUDOGENE, "pseudogene"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, "locus12", GFF3Attributes.PSEUDOGENE, "non_processed_pseudogene"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                "unitary_pseudogene",
                "unitary_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, "locus123", GFF3Attributes.PSEUDOGENE, "unitary_pseudogene"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneLocusTagAssociationWithDuplicateLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "processed_pseudogene",
                "processed_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, "locus1", GFF3Attributes.PSEUDOGENE, "pseudogene"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "non_processed_pseudogene",
                "non_processed_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, "locus1", GFF3Attributes.PSEUDOGENE, "non_processed_pseudogene"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                "unitary_pseudogene",
                "unitary_pseudogene",
                Map.of(GFF3Attributes.LOCUS_TAG, "locus123", GFF3Attributes.PSEUDOGENE, "unitary_pseudogene"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                        .formatted("locus1", f1.getName(), f2.getName())));
    }

    @Test
    public void testValidateGeneAssociationWithSameLocusTag() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.LOCUS_TAG, "locus1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.LOCUS_TAG, "locus1"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene2", GFF3Attributes.LOCUS_TAG, "locus3"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneAssociationWithMultipleLocusTags() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.LOCUS_TAG, "locus1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.GENE, "gene2", GFF3Attributes.LOCUS_TAG, "locus2"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of(GFF3Attributes.GENE, "gene3"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneAssociationWithRNAFeature() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                OntologyTerm.RRNA.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.LOCUS_TAG, "locus1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                OntologyTerm.RRNA.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.LOCUS_TAG, "locus2"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), OntologyTerm.RRNA.name(), Map.of(GFF3Attributes.GENE, "gene2"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneAssociationWithDifferentLocusTag() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.LOCUS_TAG, "locus1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.LOCUS_TAG, "locus2"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene2", GFF3Attributes.LOCUS_TAG, "locus3"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Features sharing gene \"%s\" are associated with \"%s\" attributes with different values (\"%s\" and \"%s\")"
                                .formatted("gene1", GFF3Attributes.LOCUS_TAG, "locus1", "locus2")));
    }

    @Test
    public void testValidateGeneAssociationWithSamePseudoGene() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.PSEUDOGENE, "pseudoGene1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.PSEUDOGENE, "pseudoGene1"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.GENE, "gene3", GFF3Attributes.PSEUDOGENE, "pseudoGene3"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneAssociationWithMultiplePseudoGenes() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.PSEUDOGENE, "pseudoGene1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.GENE, "gene2", GFF3Attributes.PSEUDOGENE, "pseudoGene2"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of(GFF3Attributes.GENE, "gene3"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateGeneAssociationWithDifferentPseudoGene() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.PSEUDOGENE, "pseudoGene1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene1", GFF3Attributes.PSEUDOGENE, "pseudoGene2"));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.GENE, "gene2", GFF3Attributes.PSEUDOGENE, "pseudoGene3"));
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Features sharing gene \"%s\" are associated with \"%s\" attributes with different values (\"%s\" and \"%s\")"
                                .formatted("gene1", GFF3Attributes.PSEUDOGENE, "pseudoGene1", "pseudoGene2")));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneAssociationSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.GENE.name(),
                OntologyTerm.GENE.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag1", GFF3Attributes.GENE, "gene1"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag1", GFF3Attributes.GENE, "gene1"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag2", GFF3Attributes.GENE, "gene3"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneAssociationFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag1", GFF3Attributes.GENE, "gene1"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag1", GFF3Attributes.GENE, "gene2"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag1", GFF3Attributes.GENE, "gene3"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Features sharing locus_tag \"%s\" are associated with \"gene\" qualifiers with different values"
                                .formatted("locus_tag1")));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneSynonymAssociationFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "locus_tag1",
                        GFF3Attributes.GENE_SYNONYM,
                        "synonym1,synonym2,synonym3"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "locus_tag1",
                        GFF3Attributes.GENE_SYNONYM,
                        "synonym1,synonym2,synonym4"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "locus_tag2",
                        GFF3Attributes.GENE_SYNONYM,
                        "synonym1,synonym2,synonym5"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Features sharing locus_tag \"%s\" are associated with \"gene_synonym\""
                        .formatted("locus_tag1")));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneNoSynonymAssociationSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "locus_tag1",
                        GFF3Attributes.GENE_SYNONYM,
                        "synonym1,synonym2,synonym3"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag1"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.LOCUS_TAG, "locus_tag2"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));
    }

    @Test
    public void testValidateLocusTagAssociationWithDifferentGeneSynonymAssociationSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "locus_tag1",
                        GFF3Attributes.GENE_SYNONYM,
                        "synonym1,synonym2,synonym3"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "locus_tag1",
                        GFF3Attributes.GENE_SYNONYM,
                        "synonym1,synonym2,synonym3"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "locus_tag2",
                        GFF3Attributes.GENE_SYNONYM,
                        "synonym1,synonym2,synonym3"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));
    }
}
