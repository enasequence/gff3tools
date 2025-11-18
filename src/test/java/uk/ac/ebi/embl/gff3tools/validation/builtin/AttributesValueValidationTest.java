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

public class AttributesValueValidationTest {

    private GFF3Feature feature;

    private AttributesValueValidation attributesValueValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        attributesValueValidation = new AttributesValueValidation();
    }

    @Test
    public void testValidateAttributesPatternTRNASuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(GFF3Attributes.PRODUCT, List.of("transfer RNA-leucine", "tRNA-Thr")));

        Assertions.assertDoesNotThrow(() -> attributesValueValidation.validateAttributeValuePattern(feature, 1));
    }

    @Test
    public void testValidateAttributesPatternTRNAFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PRODUCT, List.of("transfer RNA-leucine", "tRNA-Thr")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesValueValidation.validateAttributeValuePattern(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Feature \"%s\" requires \"%s\" attributes with value matching the pattern"
                        .formatted(OntologyTerm.CDS.name(), GFF3Attributes.PRODUCT)));
    }

    @Test
    public void testValidateAttributesPatternRRNASuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                OntologyTerm.RRNA.name(),
                Map.of(GFF3Attributes.PRODUCT, List.of("12S ribosomal RNA", "16S ribosomal RNA")));

        Assertions.assertDoesNotThrow(() -> attributesValueValidation.validateAttributeValuePattern(feature, 1));
    }

    @Test
    public void testValidateAttributesPatternRRNAFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.PRODUCT, List.of("12S ribosomal RNA", "16S ribosomal RNA")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesValueValidation.validateAttributeValuePattern(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Feature \"%s\" requires \"%s\" attributes with value matching the pattern"
                        .formatted(OntologyTerm.CDS.name(), GFF3Attributes.PRODUCT)));
    }

    @Test
    public void testValidateAttributesPatternSuccessForNote() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.NOTE, "notes", GFF3Attributes.PROVIRAL, "HERV-K endogenous retrovirus"));

        Assertions.assertDoesNotThrow(() -> attributesValueValidation.validateAttributeValuePattern(feature, 1));
    }

    @Test
    public void testValidateAttributesPatternSuccessForNoteWithoutProviral() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.NOTE, "notes"));

        Assertions.assertDoesNotThrow(() -> attributesValueValidation.validateAttributeValuePattern(feature, 1));
    }

    @Test
    public void testValidateAttributesPatternFailureForNote() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.NOTE, "notes", GFF3Attributes.PROVIRAL, "HERV-K endegenous retravirus"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesValueValidation.validateProviralAttribute(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Attribute \"%s\" value should match the pattern \"%s\""
                        .formatted(GFF3Attributes.PROVIRAL, PROVIRAL_VALUE_PATTERN)));
    }

    @Test
    public void testValidateAttributeValueDependencySuccessNoGene() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.ORGANELLE, "mitochondria"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.GENE, ""));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));
    }

    @Test
    public void testValidateAttributeValueDependencyNoOrganelleSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "123"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, ""));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, prop));

        Assertions.assertDoesNotThrow(
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));
    }

    @Test
    public void testValidateAttributeValueDependencySuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "12S rRNA"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, "mitochondrion"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));
    }

    @Test
    public void testValidateAttributeValueDependencyFailureEmptyValue() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "12S rRNA"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, ""));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Qualifier \"%s\" must have one of values \"%s\" when qualifier \"%s\" has value \"%s\" in any feature."
                                .formatted(GFF3Attributes.ORGANELLE, MITOCHONDRION, GFF3Attributes.GENE, "12S rRNA")));
    }

    @Test
    public void testValidateAttributeValueDependencyFailureInvalidValue() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "12S rRNA"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, "org"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Qualifier \"%s\" must have one of values \"%s\" when qualifier \"%s\" has value \"%s\" in any feature."
                                .formatted(GFF3Attributes.ORGANELLE, MITOCHONDRION, GFF3Attributes.GENE, "12S rRNA")));
    }

    @Test
    public void testValidateAttributeValueDependencyWithOtherValue() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "12S tRNA"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, "organelle"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));
    }

    @Test
    public void testValidateProteinValueSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PROTEIN_ID, "protein"));

        Assertions.assertDoesNotThrow(() -> attributesValueValidation.validateProteinValue(feature, 1));
    }

    @Test
    public void testValidateProteinValueFailure() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PROTEIN_ID, ""));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesValueValidation.validateProteinValue(feature, 1));

        Assertions.assertTrue(ex.getMessage().contains("Protein Id cannot be null or empty"));
    }

    @Test
    public void testValidateQualifierValueDependencySuccessNoGene() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.ORGANELLE, "mitochondria"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.GENE, ""));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));
    }

    @Test
    public void testValidateQualifierValueDependencyNoOrganelleSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "123"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, ""));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, prop));

        Assertions.assertDoesNotThrow(
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));
    }

    @Test
    public void testValidateQualifierValueDependencySuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "12S rRNA"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, "mitochondrion"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));
    }

    @Test
    public void testValidateQualifierValueDependencyFailureEmptyValue() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "12S rRNA"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, ""));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Qualifier \"%s\" must have one of values \"%s\" when qualifier \"%s\" has value \"%s\" in any feature."
                                .formatted(GFF3Attributes.ORGANELLE, MITOCHONDRION, GFF3Attributes.GENE, "12S rRNA")));
    }

    @Test
    public void testValidateQualifierValueDependencyFailureInvalidValue() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.GENE, "12S rRNA"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(),
                Map.of(GFF3Attributes.ORGANELLE, "org"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                OntologyTerm.CDS.name(),
                Map.of(GFF3Attributes.CHROMOSOME, "chromosome"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> attributesValueValidation.validateAttributeValueDependency(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Qualifier \"%s\" must have one of values \"%s\" when qualifier \"%s\" has value \"%s\" in any feature."
                                .formatted(GFF3Attributes.ORGANELLE, MITOCHONDRION, GFF3Attributes.GENE, "12S rRNA")));
    }
}
