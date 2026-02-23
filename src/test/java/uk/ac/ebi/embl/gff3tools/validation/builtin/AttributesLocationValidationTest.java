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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class AttributesLocationValidationTest {

    private AttributesLocationValidation attributesLocationValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        attributesLocationValidation = new AttributesLocationValidation();
        gff3Annotation = new GFF3Annotation();
    }

    @Test
    public void testWithoutCodonAttributes() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), OntologyTerm.RRNA.name(), Map.of(PRODUCT, List.of("16S ribosomalRNA ")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), OntologyTerm.TRNA.name(), Map.of(PRODUCT, List.of("16S ribosomalRNA ")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateAntiCodonWithValidLength() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:250..252,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(ANTI_CODON, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateAntiCodonWithInvalidValues() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(234.234)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(ANTI_CODON, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid Anticodon format"));
    }

    @Test
    public void testValidateAntiCodonWithInvalidPosition() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:AVD..12B,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(ANTI_CODON, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid anticodon position"));
    }

    @Test
    public void testValidateAntiCodonWithInvalidNumericValues() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(ANTI_CODON, List.of("(pos:1231231231312312312..123123123123123123123123,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(ANTI_CODON, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid numeric value in anticodon"));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:258..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end at %s..%s"
                        .formatted(ANTI_CODON, "258", "221")));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLengthSpan() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:252..258,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLocationStart() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 0, 989, Map.of(ANTI_CODON, List.of("(pos:0..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidJoinLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(ANTI_CODON, List.of("(pos:join(252..258,267..270),aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid Anticodon format"));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidComplementLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:complement(252..258),aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateAntiCodonAttributeInvalidAminoAcid() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 100, 989, Map.of(ANTI_CODON, List.of("(pos:200..202,aa:MOO)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s contains an invalid amino acid \"%s\"".formatted(ANTI_CODON, "MOO")));
    }

    @Test
    public void testValidateAntiCodonAttributeWith_SingleFragment_ValidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:520..522,aa:His)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), 1000, 1200, Map.of(ANTI_CODON, List.of("(pos:1110..1112,aa:His)")));
        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWith_SingleFragment_InvalidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:520..522,aa:His)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), 1000, 1200, Map.of(ANTI_CODON, List.of("(pos:520..2112,aa:His)")));
        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(ANTI_CODON, "520", "2112", f2.getStart(), f2.getEnd())));
    }

    @Test
    public void testValidateAntiCodonAttributeWith_MultipleFragments_ValidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                1693,
                1727,
                Map.of(ANTI_CODON, List.of("(pos:complement(4229..4231),aa:Lys,seq:ttt)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                4227,
                4264,
                Map.of(ANTI_CODON, List.of("(pos:complement(4229..4231),aa:Lys,seq:ttt)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWithMultipleFragments_InvalidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                1693,
                1727,
                Map.of(ANTI_CODON, List.of("(pos:complement(4229..4278),aa:Lys,seq:ttt)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                4227,
                4264,
                Map.of(ANTI_CODON, List.of("(pos:complement(4229..4278),aa:Lys,seq:ttt)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(ANTI_CODON, "4229", "4278", f1.getStart(), f2.getEnd())));
    }

    @Test
    public void testValidateAntiCodonAttributeWithMultipleFragments_InvalidRangeOutsideFragments() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                1693,
                1727,
                Map.of(ANTI_CODON, List.of("(pos:complement(3000..3003),aa:Lys,seq:ttt)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                4227,
                4264,
                Map.of(ANTI_CODON, List.of("(pos:complement(3000..3003),aa:Lys,seq:ttt)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(ANTI_CODON, "3000", "3003", f1.getStart(), f2.getEnd())));
    }

    // Transl_Except attribute test cases

    @Test
    public void testValidateTranslExceptWithValidLength() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:250..252,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(TRANSL_EXCEPT, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateTranslExceptWithInvalidValues() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(234.234)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(TRANSL_EXCEPT, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid transl_except format"));
    }

    @Test
    public void testValidateTranslExceptWithInvalidPosition() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:AVD..12B,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(TRANSL_EXCEPT, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid position in transl_except"));
    }

    @Test
    public void testValidateTranslExceptWithInvalidNumericValues() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(TRANSL_EXCEPT, List.of("(pos:1231231231312312312..123123123123123123123123,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(TRANSL_EXCEPT, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid position in transl_except"));
    }

    @Test
    public void testValidateTranslExceptWithInvalidLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:258..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end at %s..%s"
                        .formatted(TRANSL_EXCEPT, "258", "221")));
    }

    @Test
    public void testValidateTranslExceptAttributeWithInvalidLengthSpan() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:252..258,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateTranslExceptTermWithInvalidLengthSpan() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:252..258,aa:TERM)")));

        gff3Annotation.addFeature(f1);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));
    }

    @Test
    public void testValidateTranslExceptAttributeWithInvalidLocationStart() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 0, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:0..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end".formatted(TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateTranslExceptAttributeWithInvalidJoinLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(TRANSL_EXCEPT, List.of("(pos:join(252..258,267..270),aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("Invalid transl_except format"));
    }

    @Test
    public void testValidateTranslExceptAttributeWithInvalidComplementLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(252..258),aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateTranslExceptInvalidAminoAcid() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 100, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:200..202,aa:MOO)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s contains an invalid amino acid \"%s\"".formatted(TRANSL_EXCEPT, "MOO")));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_SingleFragment_ValidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:520..522,aa:His)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), 1000, 1200, Map.of(TRANSL_EXCEPT, List.of("(pos:1110..1112,aa:His)")));
        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_SingleFragment_InvalidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:500..502,aa:Met)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), 1000, 1200, Map.of(TRANSL_EXCEPT, List.of("(pos:520..2112,aa:Met)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(TRANSL_EXCEPT, "520", "2112", f2.getStart(), f2.getEnd())));
    }

    @Test
    public void testValidateTranslExceptAttributeWithInvalidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:1711..1713,aa:Met)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(TRANSL_EXCEPT, "1711", "1713", f1.getStart(), f1.getEnd())));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_MultipleFragments_ValidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1, 945, Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2143),aa:Lys)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                2141,
                2227,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2143),aa:Lys)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> attributesLocationValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_MultipleFragments_InvalidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1, 945, Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2229),aa:Lys)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                2141,
                2227,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2229),aa:Lys)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(TRANSL_EXCEPT, "2141", "2229", f1.getStart(), f2.getEnd())));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_MultipleFragments_InvalidRangeOutsideFragments() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1, 945, Map.of(TRANSL_EXCEPT, List.of("(pos:complement(1000..1002),aa:Lys)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                2141,
                2227,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(1000..1002),aa:Lys)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributesLocationValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(TRANSL_EXCEPT, "1000", "1002", f1.getStart(), f2.getEnd())));
    }
}
