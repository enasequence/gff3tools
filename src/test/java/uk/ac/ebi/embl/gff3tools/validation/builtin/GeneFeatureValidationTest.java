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
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

/**
 * Grouped by the validation each test exercises rather than by what its name suggests, so a group
 * is the whole of a rule's coverage.
 *
 * <p>Several tests place a feature the rule does not apply to ahead of the violation — a leading
 * {@code region} feature, or an mRNA between two genes. Those features must be stepped over, not
 * treated as the end of the annotation: every such case passed silently while the loops exited with
 * {@code return} instead of {@code continue}.
 */
public class GeneFeatureValidationTest {

    private GeneFeatureValidation geneFeatureValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        geneFeatureValidation = new GeneFeatureValidation();
        TestUtils.injectContext(geneFeatureValidation);
    }

    /** Covers {@link GeneFeatureValidation#GENE_ASSOCIATION_RULE}. */
    @Nested
    class GeneAssociationTests {

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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));
            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 2));

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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 2));

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

            gff3Annotation.setFeatures(List.of(f1, f2));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));

            gff3Annotation.setFeatures(List.of(f2, f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));
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
            gff3Annotation.setFeatures(List.of(f1, f2));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));

            // Same annotation, same gene with different locus_tag - conflict
            GFF3Feature f3 = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS.name(),
                    OntologyTerm.CDS.name(),
                    "annotation1",
                    Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus3")));

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));

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

            gff3Annotation.setFeatures(List.of(f4));
            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 4));
        }

        @Test
        public void testValidateGeneAssociationWithNoGeneAttributeSuccess() {
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    "processed_pseudogene",
                    "processed_pseudogene",
                    "annotation1",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("pseudogene")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    "non_processed_pseudogene",
                    "non_processed_pseudogene",
                    "annotation1",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus12"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("non_processed_pseudogene")));

            gff3Annotation.setFeatures(List.of(f1, f2));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 3));

            // Different annotation, reusing locus1 - should not conflict
            GFF3Feature f3 = TestUtils.createGFF3Feature(
                    "processed_pseudogene",
                    "processed_pseudogene",
                    "annotation2",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("pseudogene")));

            gff3Annotation.setFeatures(List.of(f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 3));
        }

        @Test
        public void testValidateGeneAssociationWithConflictAfterFeatureWithoutGene() {
            GFF3Feature region =
                    TestUtils.createGFF3Feature(OntologyTerm.REGION.name(), OntologyTerm.REGION.name(), Map.of());
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS.name(),
                    OntologyTerm.CDS.name(),
                    Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus1")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.GENE, List.of("gene1"), GFF3Attributes.LOCUS_TAG, List.of("locus2")));

            gff3Annotation.setFeatures(List.of(region, f1, f2));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class, () -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 1));

            Assertions.assertTrue(
                    ex.getMessage()
                            .contains(
                                    "Features sharing gene \"gene1\" are associated with \"locus_tag\" attributes with different values (\"locus1\" and \"locus2\")"));
        }
    }

    /** Covers {@link GeneFeatureValidation#GENE_LOCUS_TAG_ASSOCIATION_RULE}. */
    @Nested
    class GeneLocusTagAssociationTests {

        @Test
        public void testValidateGeneLocusTagAssociationWithoutGeneFeature() {
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS.name(),
                    OntologyTerm.CDS.name(),
                    Map.of(GFF3Attributes.PRODUCT, List.of("product")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    OntologyTerm.SIGNAL_PEPTIDE.name(),
                    OntologyTerm.SIGNAL_PEPTIDE.name(),
                    Map.of(GFF3Attributes.PRODUCT, List.of("product")));
            GFF3Feature f3 = TestUtils.createGFF3Feature(
                    OntologyTerm.OPERON.name(),
                    OntologyTerm.OPERON.name(),
                    Map.of(GFF3Attributes.PRODUCT, List.of("product")));
            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));
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
            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));
        }

        @Test
        public void testValidateGeneLocusTagAssociationWithNoDuplicateLocusTags() {
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    "processed_pseudogene",
                    "processed_pseudogene",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("pseudogene")));
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
            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));
        }

        @Test
        public void testValidateGeneLocusTagAssociationWithDuplicateLocusTagScopedToAnnotation() {
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    "processed_pseudogene",
                    "processed_pseudogene",
                    "annotation1",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("pseudogene")));

            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    "non_processed_pseudogene",
                    "non_processed_pseudogene",
                    "annotation1",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus12"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("non_processed_pseudogene")));

            gff3Annotation.setFeatures(List.of(f1, f2));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateGeneAssociation(gff3Annotation, 3));

            // Same annotation, same locus_tag
            GFF3Feature f3 = TestUtils.createGFF3Feature(
                    "processed_pseudogene",
                    "processed_pseudogene",
                    "annotation1",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("pseudogene")));

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 3));

            Assertions.assertTrue(ex.getMessage()
                    .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                            .formatted("locus1", f1.getName(), f3.getName())));

            // Different annotation - should not conflict
            GFF3Feature f4 = TestUtils.createGFF3Feature(
                    "processed_pseudogene",
                    "processed_pseudogene",
                    "annotation2",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("pseudogene")));

            gff3Annotation.setFeatures(List.of(f4));

            Assertions.assertDoesNotThrow(
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 4));
        }

        @Test
        public void testValidateGeneLocusTagAssociationWithDuplicateLocusTags() {
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    "processed_pseudogene",
                    "processed_pseudogene",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("pseudogene")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    "non_processed_pseudogene",
                    "non_processed_pseudogene",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus1"),
                            GFF3Attributes.PSEUDOGENE,
                            List.of("non_processed_pseudogene")));

            gff3Annotation.setFeatures(List.of(f1, f2));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));

            Assertions.assertTrue(ex.getMessage()
                    .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                            .formatted("locus1", f1.getName(), f2.getName())));
        }

        @Test
        public void testValidateGeneLocusTagAssociationWithDuplicateAfterNonGeneFeature() {
            // Every EMBL-converted file opens with a region feature mapped from the source feature.
            GFF3Feature region =
                    TestUtils.createGFF3Feature(OntologyTerm.REGION.name(), OntologyTerm.REGION.name(), Map.of());
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.GENE, List.of("gene1")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.GENE, List.of("gene2")));

            gff3Annotation.setFeatures(List.of(region, f1, f2));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));

            Assertions.assertTrue(ex.getMessage()
                    .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                            .formatted("locus1", f1.getName(), f2.getName())));
        }

        @Test
        public void testValidateGeneLocusTagAssociationWithDuplicateAcrossInterleavedFeatures() {
            // gene -> mRNA -> gene is the ordinary shape of an annotated gene model.
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.GENE, List.of("gene1")));
            GFF3Feature mrna = TestUtils.createGFF3Feature(
                    OntologyTerm.MRNA.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.GENE, List.of("gene1")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.GENE, List.of("gene2")));

            gff3Annotation.setFeatures(List.of(f1, mrna, f2));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));

            Assertions.assertTrue(ex.getMessage()
                    .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                            .formatted("locus1", f1.getName(), f2.getName())));
        }

        @Test
        public void testValidateGeneLocusTagAssociationWithDuplicateAfterGeneWithoutLocusTag() {
            GFF3Feature noLocusTag = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of(GFF3Attributes.GENE, List.of("gene1")));
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.GENE, List.of("gene2")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus1"), GFF3Attributes.GENE, List.of("gene3")));

            gff3Annotation.setFeatures(List.of(noLocusTag, f1, f2));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateGeneLocusTagAssociation(gff3Annotation, 1));

            Assertions.assertTrue(ex.getMessage()
                    .contains("locus_tag=\"%s\" already used by \"%s\" and \"%s\""
                            .formatted("locus1", f1.getName(), f2.getName())));
        }
    }

    /** Covers {@link GeneFeatureValidation#LOCUS_TAG_ASSOCIATION_RULE}. */
    @Nested
    class LocusTagAssociationTests {

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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));

            Assertions.assertTrue(ex.getMessage()
                    .contains(
                            "Features sharing locus_tag \"%s\" are associated with \"gene\" qualifiers with different values"
                                    .formatted("locus_tag1")));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));

            Assertions.assertTrue(ex.getMessage()
                    .contains("Features sharing locus_tag \"%s\" are associated with \"gene_synonym\""
                            .formatted("locus_tag1")));
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

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));
            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));

            GFF3Feature f4 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    "annotation2",
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));
            GFF3Feature f5 = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS.name(),
                    OntologyTerm.CDS.name(),
                    "annotation2",
                    Map.of(
                            GFF3Attributes.LOCUS_TAG,
                            List.of("locus_tag1"),
                            GFF3Attributes.GENE_SYNONYM,
                            List.of("synonym1,synonym2,synonym4")));

            gff3Annotation.setFeatures(List.of(f4, f5));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));
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

            gff3Annotation.setFeatures(List.of(f1, f2, f3));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));

            // Same annotation annotation1 - conflicting gene for same locus_tag - should throw
            GFF3Feature f4 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    "annotation1",
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));

            gff3Annotation.setFeatures(List.of(f1, f2, f3, f4));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 4));

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

            gff3Annotation.setFeatures(List.of(f1, f2, f3, f5));

            ValidationException ex2 = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 5));

            Assertions.assertTrue(ex2.getMessage()
                    .contains("Features sharing locus_tag \"locus_tag3\" are associated with \"gene_synonym\""));

            // Cross-annotation validation - should not conflict
            GFF3Feature f6 = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    "annotation2",
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));

            gff3Annotation.setFeatures(List.of(f6));

            Assertions.assertDoesNotThrow(() -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 6));
        }

        @Test
        public void testValidateLocusTagAssociationWithConflictAfterFeatureWithoutLocusTag() {
            GFF3Feature region =
                    TestUtils.createGFF3Feature(OntologyTerm.REGION.name(), OntologyTerm.REGION.name(), Map.of());
            GFF3Feature f1 = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS.name(),
                    OntologyTerm.CDS.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene1")));
            GFF3Feature f2 = TestUtils.createGFF3Feature(
                    OntologyTerm.CDS.name(),
                    OntologyTerm.CDS.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("locus_tag1"), GFF3Attributes.GENE, List.of("gene2")));

            gff3Annotation.setFeatures(List.of(region, f1, f2));

            ValidationException ex = Assertions.assertThrows(
                    ValidationException.class,
                    () -> geneFeatureValidation.validateLocusTagAssociation(gff3Annotation, 1));

            Assertions.assertTrue(ex.getMessage()
                    .contains(
                            "Features sharing locus_tag \"%s\" are associated with \"gene\" qualifiers with different values"
                                    .formatted("locus_tag1")));
        }
    }

    /** Covers {@link GeneFeatureValidation#LOCUS_TAG_EXISTS_RULE}. */
    @Nested
    class LocusTagExistsTests {

        private static final String LOCUS_TAG_EXISTS_MESSAGE =
                "/locus_tag must exist for annotated contigs/scaffolds/chromosomes.";

        private GFF3Feature gene() {
            return TestUtils.createGFF3Feature(OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of());
        }

        private GFF3Feature feature(String name) {
            return TestUtils.createGFF3Feature(name, name, Map.of());
        }

        /**
         * Wires the validation with the given analysis type and master metadata, either of which may
         * be absent, and runs the rule over the supplied features.
         */
        private Executable validation(AnalysisType analysisType, MasterMetadata metadata, GFF3Feature... features) {
            ValidationContext context = TestUtils.createTestContext();

            if (analysisType != null) {
                context.register(
                        AnalysisContext.class, provider(AnalysisContext.class, new AnalysisContext(analysisType, 10)));
            }
            if (metadata != null) {
                MasterMetadataProvider metadataProvider = new MasterMetadataProvider();
                metadataProvider.addSource(seqId -> Optional.of(metadata));
                context.register(
                        MasterMetadataProvider.class, provider(MasterMetadataProvider.class, metadataProvider));
            }

            TestUtils.injectContext(geneFeatureValidation, context);
            gff3Annotation.setFeatures(List.of(features));
            return () -> geneFeatureValidation.validateLocusTagExists(gff3Annotation, 1);
        }

        private <T> ContextProvider<T> provider(Class<T> type, T value) {
            return new ContextProvider<>() {
                @Override
                public T get(ValidationContext ctx) {
                    return value;
                }

                @Override
                public Class<T> type() {
                    return type;
                }
            };
        }

        private MasterMetadata metadata(String dataClass, String contigDataClass, String lineage) {
            MasterMetadata metadata = new MasterMetadata();
            metadata.setDataClass(dataClass);
            metadata.setContigDataclass(contigDataClass);
            metadata.setLineage(lineage);
            return metadata;
        }

        private void assertViolation(Executable validation) {
            ValidationException ex = Assertions.assertThrows(ValidationException.class, validation);
            Assertions.assertTrue(ex.getMessage().contains(LOCUS_TAG_EXISTS_MESSAGE), ex.getMessage());
        }

        @Test
        public void testAnnotatedGenomeWithoutLocusTagFails() {
            assertViolation(validation(null, metadata("STD", null, null), gene()));
        }

        @Test
        public void testAnnotatedGenomeWithLocusTagSucceeds() {
            GFF3Feature tagged = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(),
                    OntologyTerm.GENE.name(),
                    Map.of(GFF3Attributes.LOCUS_TAG, List.of("LOCUS_0001")));

            Assertions.assertDoesNotThrow(validation(null, metadata("STD", null, null), tagged));
        }

        /**
         * A locus_tag submitted with a blank value leaves the entry untagged: {@code GFF3Feature}
         * drops blank attribute values on the way in, so the attribute never reaches the rule. The
         * blank guard in {@code hasLocusTag} is belt-and-braces against a feature built some other
         * way, and is unreachable through the reader.
         */
        @Test
        public void testLocusTagWithBlankValueFails() {
            GFF3Feature blank = TestUtils.createGFF3Feature(
                    OntologyTerm.GENE.name(), OntologyTerm.GENE.name(), Map.of(GFF3Attributes.LOCUS_TAG, List.of(" ")));

            assertViolation(validation(null, metadata("STD", null, null), blank));
        }

        /**
         * A WGS set master entry carries dataClass "SET" and the contig's own class separately.
         * Reading dataClass alone would skip every contig of a WGS assembly - the main case the rule
         * exists for.
         */
        @Test
        public void testWgsContigOfSetMasterFails() {
            assertViolation(validation(null, metadata("SET", "WGS", null), gene()));
        }

        @Test
        public void testNonGenomeDataClassSucceeds() {
            Assertions.assertDoesNotThrow(validation(null, metadata("CON", null, null), gene()));
        }

        /** The analysis type alone identifies a genome, for callers that supply no master entry. */
        @Test
        public void testSequenceAssemblyWithoutMetadataFails() {
            assertViolation(validation(AnalysisType.SEQUENCE_ASSEMBLY, null, gene()));
        }

        @ParameterizedTest
        @EnumSource(
                value = AnalysisType.class,
                names = {"SEQUENCE_ASSEMBLY"},
                mode = EnumSource.Mode.EXCLUDE)
        public void testOtherAnalysisTypesWithoutMetadataSucceed(AnalysisType analysisType) {
            Assertions.assertDoesNotThrow(validation(analysisType, null, gene()));
        }

        /** A transcriptome is never a genome, whatever data class it inherited from a master. */
        @Test
        public void testTranscriptomeAssemblyOverridesGenomeDataClass() {
            Assertions.assertDoesNotThrow(
                    validation(AnalysisType.TRANSCRIPTOME_ASSEMBLY, metadata("WGS", null, null), gene()));
        }

        /** Neither signal supplied - every plain CLI invocation. */
        @Test
        public void testWithoutAnalysisTypeOrMetadataSucceeds() {
            Assertions.assertDoesNotThrow(validation(null, null, gene()));
        }

        @Test
        public void testVirusSucceeds() {
            Assertions.assertDoesNotThrow(validation(
                    null, metadata("STD", null, "Viruses; Riboviria; Orthornavirae; Pisuviricota."), gene()));
        }

        /** An organism that resolves to something else is not exempt. */
        @Test
        public void testEukaryoteFails() {
            assertViolation(validation(null, metadata("STD", null, "Eukaryota; Metazoa; Chordata; Homo."), gene()));
        }

        /** One repeat_region or misc_feature exempts the whole entry, as it does at ENA. */
        @ParameterizedTest
        @ValueSource(strings = {"repeat_region", "tandem_repeat", "sequence_feature", "biological_region"})
        public void testExemptFeatureSucceeds(String featureName) {
            Assertions.assertDoesNotThrow(validation(null, metadata("STD", null, null), gene(), feature(featureName)));
        }

        /** Nothing but the source feature and a gap: no annotation to tag. */
        @Test
        public void testWithoutAnnotationSucceeds() {
            Assertions.assertDoesNotThrow(
                    validation(null, metadata("STD", null, null), feature(OntologyTerm.REGION.name()), feature("gap")));
        }
    }
}
