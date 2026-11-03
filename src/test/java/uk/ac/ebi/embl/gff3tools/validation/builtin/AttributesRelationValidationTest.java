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
import java.util.Optional;
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

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudoParent() {
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of(GFF3Attributes.PSEUDO, List.of("true"))),
                feature(OntologyTerm.CDS.name(), "cds1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported(
                "CDS \"cds1\" at seq1:1..90 has a \"product\" attribute", "its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogeneParent() {
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of(GFF3Attributes.PSEUDOGENE, List.of("processed"))),
                feature(OntologyTerm.CDS.name(), "cds1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported("its ancestor \"gene1\" has pseudogene=processed");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogeneTypeGrandparent() {
        gff3Annotation.setFeatures(List.of(
                feature("pseudogene", "gene1", null, Map.of()),
                feature("mRNA", "mrna1", "gene1", Map.of()),
                feature(OntologyTerm.CDS.name(), "cds1", "mrna1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported("\"cds1\"", "its ancestor \"gene1\" is of type pseudogene");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogenicTranscriptParent() {
        gff3Annotation.setFeatures(List.of(
                feature("pseudogenic_transcript", "tx1", null, Map.of()),
                feature(OntologyTerm.CDS.name(), "cds1", "tx1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported("its ancestor \"tx1\" is of type pseudogenic_transcript");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_SoAccessions() {
        // SO:0000336 is pseudogene, SO:0000316 is CDS.
        gff3Annotation.setFeatures(List.of(
                feature("SO:0000336", "gene1", null, Map.of()),
                feature("SO:0000316", "cds1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported("\"cds1\"", "its ancestor \"gene1\" is of type SO:0000336");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_PseudogenicCds() {
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of(GFF3Attributes.PSEUDO, List.of("true"))),
                feature("pseudogenic_CDS", "cds1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported("pseudogenic_CDS \"cds1\"", "its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_ProductOnSecondCdsLine() {
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of(GFF3Attributes.PSEUDO, List.of("true"))),
                feature(OntologyTerm.CDS.name(), "cds1", "gene1", 1, 40, Map.of()),
                feature(
                        OntologyTerm.CDS.name(),
                        "cds1",
                        "gene1",
                        50,
                        90,
                        Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported("CDS \"cds1\" at seq1:1..40", "its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_AlsoPseudoItself() {
        // The CDS's own pair is MUTUALLY_EXCLUSIVE_ATTRIBUTES', but its pseudo parent is still reported here.
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of(GFF3Attributes.PSEUDO, List.of("true"))),
                feature(
                        OntologyTerm.CDS.name(),
                        "cds1",
                        "gene1",
                        Map.of(GFF3Attributes.PSEUDO, List.of("true"), GFF3Attributes.PRODUCT, List.of("Abc")))));

        assertCdsPseudoProductReported("its ancestor \"gene1\" has pseudo=true");
    }

    @Test
    public void testValidateCdsPseudoParentProductFailure_ReportsEveryCds() {
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of(GFF3Attributes.PSEUDO, List.of("true"))),
                feature(OntologyTerm.CDS.name(), "cds1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc"))),
                feature(OntologyTerm.CDS.name(), "cds2", "gene1", Map.of()),
                feature("gene", "gene2", null, Map.of()),
                feature(OntologyTerm.CDS.name(), "cds3", "gene2", Map.of(GFF3Attributes.PRODUCT, List.of("Def"))),
                feature("gene", "gene3", null, Map.of(GFF3Attributes.PSEUDOGENE, List.of("unitary"))),
                feature(OntologyTerm.CDS.name(), "cds4", "gene3", Map.of(GFF3Attributes.PRODUCT, List.of("Ghi")))));

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
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of()),
                feature(
                        OntologyTerm.CDS.name(),
                        "cds1",
                        "gene1",
                        Map.of(GFF3Attributes.PSEUDO, List.of("true"), GFF3Attributes.PRODUCT, List.of("Abc")))));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_PseudogenicCdsWithoutPseudoParent() {
        gff3Annotation.setFeatures(
                List.of(feature("pseudogenic_CDS", "cds1", null, Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_NoProduct() {
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of(GFF3Attributes.PSEUDO, List.of("true"))),
                feature(OntologyTerm.CDS.name(), "cds1", "gene1", Map.of())));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_ParentNotPseudo() {
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", null, Map.of()),
                feature(OntologyTerm.CDS.name(), "cds1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_ProductOnPseudoGeneItself() {
        // Only CDS features are checked; the gene's own pair is MUTUALLY_EXCLUSIVE_ATTRIBUTES'.
        gff3Annotation.setFeatures(List.of(
                feature(
                        "gene",
                        "gene1",
                        null,
                        Map.of(GFF3Attributes.PSEUDO, List.of("true"), GFF3Attributes.PRODUCT, List.of("Abc"))),
                feature("mRNA", "mrna1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_ParentCycle() {
        // Neither gene is pseudo, so nothing is reported, and the walk must not loop.
        gff3Annotation.setFeatures(List.of(
                feature("gene", "gene1", "gene2", Map.of()),
                feature("gene", "gene2", "gene1", Map.of()),
                feature(OntologyTerm.CDS.name(), "cds1", "gene1", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

    @Test
    public void testValidateCdsPseudoParentProductSuccess_DanglingParent() {
        gff3Annotation.setFeatures(List.of(
                feature(OntologyTerm.CDS.name(), "cds1", "missing", Map.of(GFF3Attributes.PRODUCT, List.of("Abc")))));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
    }

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

    private void assertCdsPseudoProductReported(String... expected) {
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesRelationValidation.validateCdsPseudoParentProduct(gff3Annotation, 1));
        for (String text : expected) {
            Assertions.assertTrue(ex.getMessage().contains(text), "Expected \"" + text + "\" in: " + ex.getMessage());
        }
    }

    private static GFF3Feature feature(String name, String id, String parentId, Map<String, List<String>> attributes) {
        return feature(name, id, parentId, 1, 90, attributes);
    }

    private static GFF3Feature feature(
            String name, String id, String parentId, long start, long end, Map<String, List<String>> attributes) {
        GFF3Feature feature = new GFF3Feature(
                Optional.of(id),
                Optional.ofNullable(parentId),
                "seq1",
                Optional.empty(),
                ".",
                name,
                start,
                end,
                ".",
                "+",
                "0");
        feature.addAttributes(attributes);
        return feature;
    }
}
