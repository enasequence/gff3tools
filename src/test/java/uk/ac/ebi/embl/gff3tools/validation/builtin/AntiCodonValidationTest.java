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

public class AntiCodonValidationTest {

    private AntiCodonValidation antiCodonValidation;

    private GFF3Feature feature;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        antiCodonValidation = new AntiCodonValidation();
        gff3Annotation = new GFF3Annotation();
    }

    @Test
    public void testValidateCodonAttributes() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), OntologyTerm.RRNA.name(), Map.of(PRODUCT, List.of("16S ribosomalRNA ")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), OntologyTerm.TRNA.name(), Map.of(PRODUCT, List.of("16S ribosomalRNA ")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWithValidLength() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:250..252,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(ANTI_CODON, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithValidLength() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:250..252,aa:His)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(),
                OntologyTerm.TRNA.name(),
                Map.of(TRANSL_EXCEPT, List.of("(pos:260..262,aa:His)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWith_SingleFragment_ValidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:520..522,aa:His)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), 1000, 1200, Map.of(ANTI_CODON, List.of("(pos:1110..1112,aa:His)")));
        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));
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
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

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

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));
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
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

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
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(ANTI_CODON, "3000", "3003", f1.getStart(), f2.getEnd())));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWith_SingleFragment_ValidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:520..522,aa:His)")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.TRNA.name(), 1000, 1200, Map.of(TRANSL_EXCEPT, List.of("(pos:1110..1112,aa:His)")));
        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));
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
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

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
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(TRANSL_EXCEPT, "1711", "1713", f1.getStart(), f1.getEnd())));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_MultipleFragments_ValidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                1,
                945,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2143),aa:Lys,seq:ttt)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                2141,
                2227,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2143),aa:Lys,seq:ttt)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_MultipleFragments_InvalidRange() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                1,
                945,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2229),aa:Lys,seq:ttt)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                2141,
                2227,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(2141..2229),aa:Lys,seq:ttt)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(TRANSL_EXCEPT, "2141", "2229", f1.getStart(), f2.getEnd())));
    }

    @Test
    public void testValidateTranslExceptAttributeWith_MultipleFragments_InvalidRangeOutsideFragments() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                1,
                945,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(1000..1002),aa:Lys,seq:ttt)")));

        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                2141,
                2227,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(1000..1002),aa:Lys,seq:ttt)")));

        gff3Annotation.addFeature(f1);
        gff3Annotation.addFeature(f2);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("%s location %s..%s is outside feature range %s..%s"
                        .formatted(TRANSL_EXCEPT, "1000", "1002", f1.getStart(), f2.getEnd())));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLengthSpan() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:252..258,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidLengthSpan() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:252..258,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateTranslExceptTermCodonAttributeWithInvalidLengthSpan() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:252..258,aa:TERM)")));

        gff3Annotation.addFeature(f1);

        Assertions.assertDoesNotThrow(() -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:258..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end at %s..%s"
                        .formatted(ANTI_CODON, "258", "221")));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:258..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end at %s..%s"
                        .formatted(TRANSL_EXCEPT, "258", "221")));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidLocationStart() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 0, 989, Map.of(ANTI_CODON, List.of("(pos:0..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidLocationStart() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 0, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:0..221,aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid %s location: start must be > 0 and less than end".formatted(TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeInvalidAminoAcid() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 100, 989, Map.of(ANTI_CODON, List.of("(pos:200..202,aa:Hi)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s contains an invalid amino acid at location".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeInvalidAminoAcid() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 100, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:200..202,aa:Hi)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("%s contains an invalid amino acid at location".formatted(TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeWithInvalidComplementLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:complement(252..258),aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidComplementLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(TRANSL_EXCEPT, List.of("(pos:complement(252..258),aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(TRANSL_EXCEPT)));
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
                ValidationException.class, () -> antiCodonValidation.validateAntiCodon(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(ANTI_CODON)));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeWithInvalidJoinLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(),
                200,
                989,
                Map.of(TRANSL_EXCEPT, List.of("(pos:join(252..258,267..270),aa:His)")));

        gff3Annotation.addFeature(f1);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateTranslExcept(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("%s location span must be \"3\"".formatted(TRANSL_EXCEPT)));
    }

    @Test
    public void testValidateAntiCodonAttributeInvalidAminoAcidAbbreviation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(ANTI_CODON, List.of("(pos:250..252,aa:HIS)")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAminoAcidMismatch(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid amino acid \"%s\" at location %s..%s. Expected \"%s\""
                        .formatted("HIS", feature.getStart(), feature.getEnd(), "His")));
    }

    @Test
    public void testValidateTranslExceptCodonAttributeInvalidAminoAcidAbbreviation() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.RRNA.name(), 200, 989, Map.of(TRANSL_EXCEPT, List.of("(pos:250..252,aa:HIS)")));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> antiCodonValidation.validateAminoAcidMismatch(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("Invalid amino acid \"%s\" at location %s..%s. Expected \"%s\""
                        .formatted("HIS", feature.getStart(), feature.getEnd(), "His")));
    }
}
