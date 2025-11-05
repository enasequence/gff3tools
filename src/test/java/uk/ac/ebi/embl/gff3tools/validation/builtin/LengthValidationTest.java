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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class LengthValidationTest {

    GFF3Feature feature;

    private LengthValidation lengthValidation;

    @BeforeEach
    public void setUp() {
        lengthValidation = new LengthValidation();
    }

    @Test
    public void testIntronValidationSuccess() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.SPLICEOSOMAL_INTRON.name(), 1L, 20L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateIntronLength(feature, 1));
    }

    @Test
    public void testIntronValidationForCDSSuccess() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                1L,
                5L,
                Map.of(
                        GFF3Attributes.RIBOSOMAL_SLIPPAGE,
                        "ribsomal_slippage",
                        GFF3Attributes.TRANS_SPLICING,
                        "trans_splicing",
                        GFF3Attributes.ARTIFICIAL_LOCATION,
                        "artificial_location"));
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateIntronLength(feature, 1));
    }

    @Test
    public void testIntronValidationForCDSSuccessWithPseudo() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 1L, 5L, Map.of(GFF3Attributes.PSEUDO, "pseudo"));
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateIntronLength(feature, 1));
    }

    @Test
    public void testIntronValidationForCDSFailure() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), 1L, 5L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> lengthValidation.validateIntronLength(feature, 1));
        assertTrue(exception.getMessage().contains("Intron usually expected to be at least 10 nt long"));
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
