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
    }

    @Test
    public void testValidateMutuallyRequiredAttributesSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CLONE, "clone", GFF3Attributes.SUB_CLONE, "subClone"));
        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyRequiredAttributes(feature, 1));
    }

    @Test
    public void testValidateMutuallyRequiredAttributesFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.SUB_CLONE, "subclone"));

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
                Map.of(GFF3Attributes.PSEUDO, "pseudo", GFF3Attributes.VARIETY, "variety"));
        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesFailure_PseudoCases() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PSEUDO, "pseudo", GFF3Attributes.PRODUCT, "product"));

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
                Map.of(GFF3Attributes.REARRANGED, "rearranged", GFF3Attributes.GERM_LINE, "germLine"));

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
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PROTEIN_ID, "proteinID"));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesByValueSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, "organelle", GFF3Attributes.MACRO_NUCLEAR, "macronuclear"));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributesByValue(feature, 1));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesByValueGeneSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.GENE, "18S rRNA", GFF3Attributes.MACRO_NUCLEAR, "macronuclear"));

        Assertions.assertDoesNotThrow(
                () -> attributesRelationValidation.validateMutuallyExclusiveAttributesByValue(feature, 1));
    }

    @Test
    public void testValidateMutuallyExclusiveAttributesByValueFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, MITOCHONDRION, GFF3Attributes.MACRO_NUCLEAR, "macronuclear"));

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
                Map.of(GFF3Attributes.GENE, rRna, GFF3Attributes.ORGANELLE, "organelle"));

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
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PROTEIN_ID, "proteinID"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.SATELLITE,
                        "satellite",
                        GFF3Attributes.MAP,
                        "map",
                        GFF3Attributes.PCR_PRIMERS,
                        "pcr"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateRequiredAttributes(gff3Annotation, 1));
    }

    @Test
    public void testValidateRequiredAttributesInAnnotationFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PROTEIN_ID, "proteinID"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.SATELLITE, "satellite", GFF3Attributes.MAP, "map"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
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
                Map.of(GFF3Attributes.CLONE, "clone", GFF3Attributes.SUB_CLONE, "subClone"));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateExclusiveAttributesCloneLibSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.CLONE,
                        "clone",
                        GFF3Attributes.SUB_CLONE,
                        "subClone",
                        GFF3Attributes.CLONE_LIB,
                        "clonelib"));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateExclusiveAttributesSubClonFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CLONE, "clone", GFF3Attributes.SUB_CLONE, "clone"));

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
                        "clone",
                        GFF3Attributes.SUB_CLONE,
                        "subclone",
                        GFF3Attributes.CLONE_LIB,
                        "clone"));

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
                Map.of(GFF3Attributes.CLONE, "", GFF3Attributes.SUB_CLONE, "subclone", GFF3Attributes.CLONE_LIB, ""));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateExclusiveAttributes(feature, 1));
    }

    @Test
    public void testValidateFeatureCircularRNASuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.CIRCULAR_RNA, "true"));
        GFF3Feature trna = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), OntologyTerm.TRNA.name(), Map.of(GFF3Attributes.CIRCULAR_RNA, "true"));
        GFF3Feature mrna = TestUtils.createGFF3Feature(
                OntologyTerm.MRNA.name(), OntologyTerm.MRNA.name(), Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(cds, 1));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(trna, 2));
        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(mrna, 3));
    }

    @Test
    public void testValidateFeatureCircularRNAFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.CIRCULAR_RNA, "true"));
        GFF3Feature trna = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.PROPEPTIDE.name(),
                Map.of(GFF3Attributes.CIRCULAR_RNA, "true"));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(cds, 1));
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesRelationValidation.validateCircularRNAAttribute(trna, 2));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attribute circularRNA is not allowed in feature \"%s\"".formatted(trna.getName())));
    }

    @Test
    public void testValidateFeatureCircularRNAFalseFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.CIRCULAR_RNA, "false"));

        Assertions.assertDoesNotThrow(() -> attributesRelationValidation.validateCircularRNAAttribute(cds, 1));
    }
}
