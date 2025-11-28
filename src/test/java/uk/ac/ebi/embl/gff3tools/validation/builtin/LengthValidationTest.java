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

import static org.junit.jupiter.api.Assertions.*;

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

public class LengthValidationTest {

    GFF3Feature feature;

    GFF3Annotation gff3Annotation;

    private LengthValidation lengthValidation;

    @BeforeEach
    public void setUp() {
        lengthValidation = new LengthValidation();
        gff3Annotation = new GFF3Annotation();
    }

    @Test
    public void testCdsIntronValidationSuccess() throws ValidationException {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1L, 100L, Map.of(GFF3Attributes.ATTRIBUTE_ID, "CDS1"));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 115L, 200L, Map.of(GFF3Attributes.ATTRIBUTE_ID, "CDS1"));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        lengthValidation.validateIntronLength(cds1, 1);
        lengthValidation.validateIntronLength(cds2, 2);

        assertDoesNotThrow(() -> lengthValidation.validateIntronLengthWithinCDS(gff3Annotation, 1));
    }

    @Test
    public void testCdsIntronValidationSuccessWithArtificialLocation() throws ValidationException {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                1L,
                100L,
                Map.of(
                        GFF3Attributes.ATTRIBUTE_ID, "CDS1",
                        GFF3Attributes.ARTIFICIAL_LOCATION, "true"));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 105L, 200L, Map.of(GFF3Attributes.ATTRIBUTE_ID, "CDS1"));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        lengthValidation.validateIntronLength(cds1, 1);
        lengthValidation.validateIntronLength(cds2, 2);

        assertDoesNotThrow(() -> lengthValidation.validateIntronLengthWithinCDS(gff3Annotation, 1));
    }

    @Test
    public void testCdsIntronValidationSuccessWithPseudo() throws ValidationException {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1L, 100L, Map.of(GFF3Attributes.ATTRIBUTE_ID, "CDS1"));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                105L,
                200L,
                Map.of(
                        GFF3Attributes.ATTRIBUTE_ID, "CDS1",
                        GFF3Attributes.PSEUDO, "true"));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        lengthValidation.validateIntronLength(cds1, 1);
        lengthValidation.validateIntronLength(cds2, 2);

        assertDoesNotThrow(() -> lengthValidation.validateIntronLengthWithinCDS(gff3Annotation, 1));
    }

    @Test
    public void testCdsIntronValidationFailureSmallIntron() throws ValidationException {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1L, 100L, Map.of(GFF3Attributes.ATTRIBUTE_ID, "CDS1"));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 102L, 200L, Map.of(GFF3Attributes.ATTRIBUTE_ID, "CDS1"));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        lengthValidation.validateIntronLength(cds1, 1);
        lengthValidation.validateIntronLength(cds2, 2);

        ValidationException ex = assertThrows(
                ValidationException.class, () -> lengthValidation.validateIntronLengthWithinCDS(gff3Annotation, 1));

        assertTrue(ex.getMessage().contains("Intron usually expected to be at least 10 nt long"));
    }

    @Test
    public void testIntronValidationForCDSSuccessWithPseudo() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 1L, 5L, Map.of(GFF3Attributes.PSEUDO, "pseudo"));
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateIntronLength(feature, 1));
    }

    @Test
    public void testIntronValidationSuccess() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.SPLICEOSOMAL_INTRON.name(), 1L, 20L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateIntronLength(feature, 1));
    }

    @Test
    public void testIntronValidationFailure() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.SPLICEOSOMAL_INTRON.name(), 1L, 9L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> lengthValidation.validateIntronLength(feature, 1));
        assertTrue(exception.getMessage().contains("Intron feature length is invalid for accession"));
    }

    @Test
    public void testExonValidationSuccess() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CODING_EXON.name(), 1L, 30L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateExonLength(feature, 1));
    }

    @Test
    public void testExonValidationFailure() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CODING_EXON.name(), 1L, 14L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> lengthValidation.validateExonLength(feature, 1));
        assertTrue(exception.getMessage().contains("Exon feature length is invalid for accession"));
    }

    @Test
    public void testPropetideValidationSuccess() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE.name(), 1L, 180L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validatePropeptideLength(feature, 1));
    }

    @Test
    public void testPropetideValidationSuccessForException() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(), 1L, 13L, Map.of(GFF3Attributes.EXCEPTION, "ribosomal slippage"));
        Assertions.assertDoesNotThrow(() -> lengthValidation.validatePropeptideLength(feature, 1));
    }

    @Test
    public void testPropetideValidationSuccessForTranslExcept() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(), 1L, 31L, Map.of(GFF3Attributes.TRANSL_EXCEPT, "ribosomal slippage"));
        Assertions.assertDoesNotThrow(() -> lengthValidation.validatePropeptideLength(feature, 1));
    }

    @Test
    public void testPropetideValidationFailure() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE.name(), 1L, 31L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> lengthValidation.validatePropeptideLength(feature, 1));
        assertTrue(exception.getMessage().contains("Propeptide feature length must be a multiple of 3 for accession"));
    }

    @Test
    public void testPropetideValidationInvalidName() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 1L, 180L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validatePropeptideLength(feature, 1));
    }
}
