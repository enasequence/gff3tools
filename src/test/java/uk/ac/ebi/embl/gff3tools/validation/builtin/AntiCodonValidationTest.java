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

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class AntiCodonValidationTest {

    private AntiCodonValidation antiCodonValidation;

    private GFF3Feature feature;

    @BeforeEach
    public void setUp() {
        antiCodonValidation = new AntiCodonValidation();
    }

    @Test
    public void testValidateCodonAttributes() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                OntologyTerm.RRNA.name(),
                Map.of(GFF3Attributes.PRODUCT, "16S ribosomalRNA "));

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(feature, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWithValidLength() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.ANTI_CODON, "(pos:250..252,aa:His)"));

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(feature, 1));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithValidLength() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:250..252,aa:His)"));

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(feature, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidRange() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.ANTI_CODON, "(pos:2520..2580,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location is invalid. Length must be within feature range."
                        .formatted(GFF3Attributes.ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidRange() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:2520..2580,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location is invalid. Length must be within feature range."
                        .formatted(GFF3Attributes.TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLengthSpan() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.ANTI_CODON, "(pos:252..258,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s location span must be \"3\"".formatted(GFF3Attributes.ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidLengthSpan() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:252..258,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s location span must be \"3\"".formatted(GFF3Attributes.TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateTranslExceptTermCodonAttributeWithInvalidLengthSpan() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:252..258,aa:TERM)"));

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateTranslExcept(feature, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLocation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.ANTI_CODON, "(pos:258..221,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end"
                        .formatted(GFF3Attributes.ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidLocation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:258..221,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end"
                        .formatted(GFF3Attributes.TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLocationStart() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 0, 989, Map.of(GFF3Attributes.ANTI_CODON, "(pos:0..221,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end"
                        .formatted(GFF3Attributes.ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidLocationStart() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 0, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:0..221,aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end"
                        .formatted(GFF3Attributes.TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeInvalidAminoAcid() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 100, 989, Map.of(GFF3Attributes.ANTI_CODON, "(pos:200..202,aa:Hi)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s contains illegal amino acid.".formatted(GFF3Attributes.ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeInvalidAminoAcid() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 100, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:200..202,aa:Hi)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s contains illegal amino acid.".formatted(GFF3Attributes.TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidComplementLocation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(GFF3Attributes.ANTI_CODON, "(pos:complement(252..258),aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s location span must be \"3\"".formatted(GFF3Attributes.ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidComplementLocation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:complement(252..258),aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s location span must be \"3\"".formatted(GFF3Attributes.TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidJoinLocation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(GFF3Attributes.ANTI_CODON, "(pos:join(252..258,267..270),aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s location span must be \"3\"".formatted(GFF3Attributes.ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidJoinLocation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:join(252..258,267..270),aa:His)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(feature, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s location span must be \"3\"".formatted(GFF3Attributes.TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeInvalidAminoAcidAbbreviation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.ANTI_CODON, "(pos:250..252,aa:HIS)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAminoAcidMismatch(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Illegal amino acid \"%s\" should be changed to legal amino acid \"%s\""
                        .formatted("HIS", "His")));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeInvalidAminoAcidAbbreviation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(GFF3Attributes.TRANSL_EXCEPT, "(pos:250..252,aa:HIS)"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAminoAcidMismatch(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Illegal amino acid \"%s\" should be changed to legal amino acid \"%s\""
                        .formatted("HIS", "His")));
    }
}
