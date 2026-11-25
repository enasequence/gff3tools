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

import static uk.ac.ebi.embl.gff3tools.validation.builtin.AttributesRelationValidation.*;

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

public class AttributesRelationValidationTest {

    private GFF3Feature feature;

    private AttributesRelationValidation attributesRelationValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        attributesRelationValidation = new AttributesRelationValidation();
        TestUtils.injectContext(attributesRelationValidation);
    }

    // ---tests for MUTUALLY_REQUIRED_ATTRIBUTES validation
    @Test
    public void testValidateMutuallyRequiredAttributesSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CLONE, List.of("clone"), GFF3Attributes.SUB_CLONE, List.of("subClone")));
        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyRequiredAttributes(feature, 1));
    }

    @Test
    public void testValidateMutuallyRequiredAttributesFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.SUB_CLONE, List.of("subclone")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateMutuallyRequiredAttributes(feature, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"%s\" attribute is required".formatted(GFF3Attributes.CLONE)));
    }

    // ---tests for MUTUALLY_EXCLUSIVE_ATTRIBUTES validation
    @Test
    public void testValidateMutuallyExclusiveAttributesSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PSEUDO, List.of("pseudo"), GFF3Attributes.VARIETY, List.of("variety")));
        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesFailure_PseudoCases() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PSEUDO, List.of("pseudo"), GFF3Attributes.PRODUCT, List.of("product")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributes(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Feature annotated with \"%s\" should not contain \"%s\""
                        .formatted(GFF3Attributes.PSEUDO, GFF3Attributes.PRODUCT)));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.REARRANGED,
                        List.of("rearranged"),
                        GFF3Attributes.GERM_LINE,
                        List.of("germLine")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributes(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attributes \"%s\" and \"%s\" cannot both exist in the same feature"
                        .formatted(GFF3Attributes.REARRANGED, GFF3Attributes.GERM_LINE)));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributes() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("proteinID")));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributes(feature, 1));
    }

    // ---tests for MUTUALLY_EXCLUSIVE_ATTRIBUTES_VALUE validation
    @Test
    public void testValidateMutuallyExclusiveAttributesByValueSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.ORGANELLE,
                        List.of("organelle"),
                        GFF3Attributes.MACRO_NUCLEAR,
                        List.of("macronuclear")));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributesByValue(feature, 1));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesByValueGeneSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.GENE,
                        List.of("18S rRNA"),
                        GFF3Attributes.MACRO_NUCLEAR,
                        List.of("macronuclear")));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributesByValue(feature, 1));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesByValueFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.ORGANELLE,
                        List.of(MITOCHONDRION),
                        GFF3Attributes.MACRO_NUCLEAR,
                        List.of("macronuclear")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributesByValue(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attribute \"%s\" must not exist when qualifier \"%s\" has value \"%s\""
                        .formatted(GFF3Attributes.MACRO_NUCLEAR, GFF3Attributes.ORGANELLE, MITOCHONDRION)));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesByValueGeneFailure() {
        String rRna = "18S rRNA";
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, List.of(rRna), GFF3Attributes.ORGANELLE, List.of("organelle")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributesByValue(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attribute \"%s\" must not exist when qualifier \"%s\" has value \"%s\""
                        .formatted(GFF3Attributes.ORGANELLE, GFF3Attributes.GENE, rRna)));
    }

    // ---tests for REQUIRED_ATTRIBUTES validation
    @Test
    public void testValidateRequiredAttributesInAnnotationSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("proteinID")));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.SATELLITE,
                        List.of("satellite"),
                        GFF3Attributes.MAP,
                        List.of("map"),
                        GFF3Attributes.PCR_PRIMERS,
                        List.of("pcr")));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, List.of("chromosome")));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateRequiredAttributes(gff3Annotation, 1));
    }

    @Test
    public void testValidateRequiredAttributesInAnnotationFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("proteinID")));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.SATELLITE, List.of("satellite"), GFF3Attributes.MAP, List.of("map")));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, List.of("chromosome")));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateRequiredAttributes(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("\"%s\" attribute is required when \"%s\" present in any of the feature"
                        .formatted(GFF3Attributes.PCR_PRIMERS, GFF3Attributes.SATELLITE)));
    }

    // ---tests for EXCLUSIVE_ATTRIBUTES validation
    @Test
    public void testValidateExclusiveAttributesSubCloneSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CLONE, List.of("clone"), GFF3Attributes.SUB_CLONE, List.of("subClone")));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateExclusiveAttributesCloneLibSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.CLONE,
                        List.of("clone"),
                        GFF3Attributes.SUB_CLONE,
                        List.of("subClone"),
                        GFF3Attributes.CLONE_LIB,
                        List.of("clonelib")));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateExclusiveAttributesSubClonFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CLONE, List.of("clone"), GFF3Attributes.SUB_CLONE, List.of("clone")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attributes \"%s\" and \"%s\" cannot have the same value"
                        .formatted(GFF3Attributes.CLONE, GFF3Attributes.SUB_CLONE)));
    }

    @Test
    public void testValidateExclusiveAttributesFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.CLONE,
                        List.of("clone"),
                        GFF3Attributes.SUB_CLONE,
                        List.of("subclone"),
                        GFF3Attributes.CLONE_LIB,
                        List.of("clone")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attributes \"%s\" and \"%s\" cannot have the same value"
                        .formatted(GFF3Attributes.CLONE, GFF3Attributes.CLONE_LIB)));
    }

    @Test
    public void testValidateExclusiveAttributesFailureEmptyValue() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.CLONE,
                        List.of(),
                        GFF3Attributes.SUB_CLONE,
                        List.of("subclone"),
                        GFF3Attributes.CLONE_LIB,
                        List.of()));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));
    }

    // ---tests for CIRCULAR_RNA_ATTRIBUTES validation
    @Test
    public void testValidateFeatureCircularRNASuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("true")));
        GFF3Feature trna = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("true")));
        GFF3Feature mrna = TestUtils.createGFF3Feature(
                OntologyTerm.MRNA.name(),
                OntologyTerm.MRNA.name(),
                Map.of(GFF3Attributes.CHROMOSOME, List.of("chromosome")));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(cds, 1));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(trna, 2));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(mrna, 3));
    }

    @Test
    public void testValidateFeatureCircularRNAFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("true")));
        GFF3Feature trna = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.PROPEPTIDE.name(),
                Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("true")));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(cds, 1));
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesRelationValidation.validateCircularRNAAttribute(trna, 2));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attribute circularRNA is not allowed in feature \"%s\"".formatted(trna.getName())));
    }

    @Test
    public void testValidateFeatureCircularRNAFalseFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CIRCULAR_RNA, List.of("false")));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(cds, 1));
    }

    // ---tests for CDS_PSEUDO_PRODUCT validation
    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudoParent() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(gene, cds));

        assertCdsPseudoProductReported(
                "CDS \"cds1\" at seq1:1..90 has a \"product\" attribute", "its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogeneParent() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        gene.addAttribute(GFF3Attributes.PSEUDOGENE, "processed");
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(gene, cds));

        assertCdsPseudoProductReported("its ancestor \"gene1\" has pseudogene=processed");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogeneTypeGrandparent() {
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "mrna1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(
                TestUtils.createGFF3Feature("gene1", null, "pseudogene", "seq1", 1, 90),
                TestUtils.createGFF3Feature("mrna1", "gene1", "mRNA", "seq1", 1, 90),
                cds));

        assertCdsPseudoProductReported("\"cds1\"", "its ancestor \"gene1\" is of type pseudogene");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogenicTranscriptParent() {
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "tx1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(
                List.of(TestUtils.createGFF3Feature("tx1", null, "pseudogenic_transcript", "seq1", 1, 90), cds));

        assertCdsPseudoProductReported("its ancestor \"tx1\" is of type pseudogenic_transcript");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_SoAccessions() {
        // SO:0000336 is pseudogene, SO:0000316 is CDS.
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", "SO:0000316", "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(
                List.of(TestUtils.createGFF3Feature("gene1", null, "SO:0000336", "seq1", 1, 90), cds));

        assertCdsPseudoProductReported("\"cds1\"", "its ancestor \"gene1\" is of type SO:0000336");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogenicCds() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", "pseudogenic_CDS", "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(gene, cds));

        assertCdsPseudoProductReported("pseudogenic_CDS \"cds1\"", "its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_ProductOnSecondCdsLine() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature secondLine = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 50, 90);
        secondLine.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(
                gene,
                TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 40),
                secondLine));

        assertCdsPseudoProductReported("CDS \"cds1\" at seq1:1..40", "its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_AlsoPseudoItself() {
        // The CDS's own pair is MUTUALLY_EXCLUSIVE_ATTRIBUTES', but its pseudo parent is still reported here.
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PSEUDO, "true");
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(gene, cds));

        assertCdsPseudoProductReported("its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_ReportsEveryCds() {
        GFF3Feature pseudoGene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        pseudoGene.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Feature unitaryGene = TestUtils.createGFF3Feature("gene3", null, "gene", "seq1", 1, 90);
        unitaryGene.addAttribute(GFF3Attributes.PSEUDOGENE, "unitary");
        GFF3Feature cds1 = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds1.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        GFF3Feature cds3 = TestUtils.createGFF3Feature("cds3", "gene2", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds3.addAttribute(GFF3Attributes.PRODUCT, "Def");
        GFF3Feature cds4 = TestUtils.createGFF3Feature("cds4", "gene3", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds4.addAttribute(GFF3Attributes.PRODUCT, "Ghi");
        gff3Annotation.setFeatures(List.of(
                pseudoGene,
                cds1,
                TestUtils.createGFF3Feature("cds2", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90),
                TestUtils.createGFF3Feature("gene2", null, "gene", "seq1", 1, 90),
                cds3,
                unitaryGene,
                cds4));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"cds1\""), ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("\"cds4\""), ex.getMessage());
        Assertions.assertFalse(ex.getMessage().contains("\"cds2\""), "No product: " + ex.getMessage());
        Assertions.assertFalse(ex.getMessage().contains("\"cds3\""), "No pseudo parent: " + ex.getMessage());
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_PseudoOnlyOnCds() {
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PSEUDO, "true");
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90), cds));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_PseudogenicCdsWithoutPseudoParent() {
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", null, "pseudogenic_CDS", "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(cds));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_NoProduct() {
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        gff3Annotation.setFeatures(
                List.of(gene, TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90)));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_ParentNotPseudo() {
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90), cds));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_ProductOnPseudoGeneItself() {
        // Only CDS features are checked; the gene's own pair is MUTUALLY_EXCLUSIVE_ATTRIBUTES'.
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "seq1", 1, 90);
        gene.addAttribute(GFF3Attributes.PSEUDO, "true");
        gene.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        GFF3Feature mrna = TestUtils.createGFF3Feature("mrna1", "gene1", "mRNA", "seq1", 1, 90);
        mrna.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(gene, mrna));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_ParentCycle() {
        // Neither gene is pseudo, so nothing is reported, and the walk must not loop.
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "gene1", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(
                TestUtils.createGFF3Feature("gene1", "gene2", "gene", "seq1", 1, 90),
                TestUtils.createGFF3Feature("gene2", "gene1", "gene", "seq1", 1, 90),
                cds));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_DanglingParent() {
        GFF3Feature cds = TestUtils.createGFF3Feature("cds1", "missing", OntologyTerm.CDS.name(), "seq1", 1, 90);
        cds.addAttribute(GFF3Attributes.PRODUCT, "Abc");
        gff3Annotation.setFeatures(List.of(cds));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    private void assertCdsPseudoProductReported(String... expected) {
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
        for (String text : expected) {
            Assertions.assertTrue(ex.getMessage().contains(text), "Expected \"" + text + "\" in: " + ex.getMessage());
        }
    }
}
