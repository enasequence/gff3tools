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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.TRANSL_EXCEPT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

class TranslExceptCodonValidationTest {

    private static final String SEQ_ID = "seq1";
    private static final String CDS_ID = "cds1";
    private static final String PLUS = "+";
    private static final String MINUS = "-";

    private TranslExceptCodonValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new TranslExceptCodonValidation();
        TestUtils.injectContext(validation);
        annotation = new GFF3Annotation();
    }

    @Nested
    class OncePerCodon {

        @Test
        void passesWhenEachCodonCarriesOneValue() {
            cds(1, 300, te("4..6", "Sec"), te("10..12", "Trp"));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void passesWhenAJoinCopiesItsValuesOntoEveryRow() {
            cds(1, 150, te("4..6", "Sec"));
            cds(201, 350, te("4..6", "Sec"));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void failsWhenAValueIsRepeatedOnOneRow() {
            cds(1, 300, te("4..6", "Sec"), te("4..6", "Sec"));

            assertTrue(violationsOf(this::validate)
                    .contains("CDS \"cds1\" at seq1:1..300: \"(pos:4..6,aa:Sec)\" is given more than once"));
        }

        @Test
        void failsWhenTwoValuesNameTheSameCodon() {
            cds(1, 300, te("4..6", "Sec"), te("4..6", "Trp"));

            assertTrue(violationsOf(this::validate)
                    .contains("\"(pos:4..6,aa:Sec)\" and \"(pos:4..6,aa:Trp)\" name the same codon"));
        }

        @Test
        void failsWhenTheSameCodonIsWrittenTwoWays() {
            cds(1, 300, te("4..6", "Sec"), "(pos: 4..6, aa:Sec)");

            assertTrue(violationsOf(this::validate)
                    .contains("\"(pos:4..6,aa:Sec)\" and \"(pos: 4..6, aa:Sec)\" name the same codon"));
        }

        @Test
        void failsWhenTwoValuesOverlap() {
            cds(1, 300, te("4..6", "Sec"), te("5..7", "Trp"));

            assertTrue(violationsOf(this::validate)
                    .contains("\"(pos:4..6,aa:Sec)\" and \"(pos:5..7,aa:Trp)\" overlap at 5..6"));
        }

        @Test
        void leavesValuesThatCannotBeReadToTranslExceptLocation() {
            cds(1, 300, "(pos:x..y,aa:Sec)", "(pos:x..y,aa:Sec)");

            assertDoesNotThrow(this::validate);
        }

        @Test
        void leavesValuesOfNoCodonLengthToTranslExceptLocation() {
            cds(1, 300, te("4..8", "Term"), te("4..6", "Sec"));

            assertDoesNotThrow(this::validate);
        }

        private void validate() throws ValidationException {
            validation.validateOncePerCodon(annotation, 1);
        }
    }

    @Nested
    class ShortStopPosition {

        @Test
        void passesWhenAOneBaseStopEndsAPlusStrandCds() {
            cds(1, 298, te("298", "TERM"));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void passesWhenATwoBaseStopEndsAPlusStrandCds() {
            cds(1, 299, te("298..299", "TERM"));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void passesWhenAShortStopEndsAMinusStrandCdsAtItsLowestCoordinate() {
            cds(MINUS, 1, 298, te("1", "TERM"));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void passesWhenAShortStopEndsTheLastSegmentOfAJoin() {
            cds(1, 150, te("349", "TERM"));
            cds(201, 349, te("349", "TERM"));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void passesWhenAShortStopEndsAMinusStrandJoinAtItsLowestCoordinate() {
            cds(MINUS, 1, 150, te("1", "TERM"));
            cds(MINUS, 201, 349, te("1", "TERM"));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void passesWhenAFullStopCodonSitsInsideTheCds() {
            cds(1, 300, te("100..102", "TERM"));

            assertDoesNotThrow(this::validate);
        }

        @ParameterizedTest
        @ValueSource(strings = {"TERM", "Ter", "Term"})
        void failsWhenAShortStopSitsInsideTheCds(String stop) {
            cds(1, 298, te("101..102", stop));

            assertTrue(violationsOf(this::validate)
                    .contains("CDS \"cds1\" at seq1:1..298: \"(pos:101..102,aa:%s)\" does not end the CDS, "
                                    .formatted(stop)
                            + "whose 3' end is at 298"));
        }

        @Test
        void failsWhenAShortStopSitsAtTheHighestCoordinateOfAMinusStrandCds() {
            cds(MINUS, 1, 298, te("298", "TERM"));

            assertTrue(violationsOf(this::validate).contains("does not end the CDS, whose 3' end is at 1"));
        }

        @Test
        void failsWhenAShortStopEndsAnEarlierSegmentOfAJoin() {
            cds(1, 150, te("150", "TERM"));
            cds(201, 349, te("150", "TERM"));

            assertTrue(violationsOf(this::validate).contains("does not end the CDS, whose 3' end is at 349"));
        }

        private void validate() throws ValidationException {
            validation.validateShortStopPosition(annotation, 1);
        }
    }

    @Nested
    class ReadingFrame {

        @Test
        void passesWhenEachValueStartsACodon() {
            cds(1, 300, te("4..6", "Sec"), te("298..300", "TERM"));

            assertDoesNotThrow(this::validate);
        }

        @ParameterizedTest
        @CsvSource({"5..7, 1 base", "6..8, 2 bases"})
        void failsWhenAValueStartsInsideACodon(String position, String offset) {
            cds(1, 300, te(position, "Sec"));

            assertTrue(violationsOf(this::validate)
                    .contains(
                            "CDS \"cds1\" at seq1:1..300: \"(pos:%s,aa:Sec)\" starts %s into a codon on strand \"+\" (codon_start=1)"
                                    .formatted(position, offset)));
        }

        @Test
        void followsCodonStart() {
            cds(1, 300, te("2..4", "Sec")).addAttribute(GFF3Attributes.CODON_START, "2");

            assertDoesNotThrow(this::validate);
        }

        @Test
        void failsWhenAValueStartsBeforeTheFirstFullCodon() {
            cds(1, 300, te("1..3", "Sec")).addAttribute(GFF3Attributes.CODON_START, "2");

            assertTrue(violationsOf(this::validate)
                    .contains("\"(pos:1..3,aa:Sec)\" starts before the first full codon (codon_start=2)"));
        }

        @Test
        void countsAlongTheSplicedSequenceOfAJoin() {
            // 201 is base 151 of the spliced sequence, so 201..203 is a codon and 202..204 is not
            cds(1, 150, te("201..203", "Sec"), te("202..204", "Trp"));
            cds(201, 350, te("201..203", "Sec"), te("202..204", "Trp"));

            String violations = violationsOf(this::validate);
            assertTrue(violations.contains("\"(pos:202..204,aa:Trp)\" starts 1 base into a codon"));
            assertFalse(violations.contains("(pos:201..203,aa:Sec)"));
        }

        @Test
        void readsAMinusStrandJoinFromItsHighestCoordinate() {
            // 350 is base 1 of the spliced sequence, so 348..350 is a codon and 347..349 is not
            cds(MINUS, 1, 150, te("348..350", "Met"), te("347..349", "Trp"));
            cds(MINUS, 201, 350, te("348..350", "Met"), te("347..349", "Trp"));

            String violations = violationsOf(this::validate);
            assertTrue(violations.contains("\"(pos:347..349,aa:Trp)\" starts 1 base into a codon on strand \"-\""));
            assertFalse(violations.contains("(pos:348..350,aa:Met)"));
        }

        @Test
        void keepsOneFrameAcrossARibosomalSlippage() {
            // The slipped base 101 is read twice, so 102 is base 103 of the spliced sequence
            cds(1, 101, te("102..104", "Sec"), te("103..105", "Trp"))
                    .addAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE, "true");
            cds(101, 300, te("102..104", "Sec"), te("103..105", "Trp"))
                    .addAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE, "true");

            String violations = violationsOf(this::validate);
            assertTrue(violations.contains("\"(pos:103..105,aa:Trp)\" starts 1 base into a codon"));
            assertFalse(violations.contains("(pos:102..104,aa:Sec)"));
        }

        @Test
        void keepsTheWrittenOrderOfATransSplicedCds() {
            // Read in the order written, 501..600 comes first, so 3 is base 103 and 1 is base 101
            cds(501, 600, te("3..5", "Sec"), te("1..3", "Trp")).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");
            cds(1, 150, te("3..5", "Sec"), te("1..3", "Trp")).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");

            String violations = violationsOf(this::validate);
            assertTrue(violations.contains("\"(pos:1..3,aa:Trp)\" starts 1 base into a codon"));
            assertFalse(violations.contains("(pos:3..5,aa:Sec)"));
        }

        @Test
        void readsEachSegmentOfAMixedStrandJoinInItsOwnDirection() {
            // The minus-strand row 1..150 is read from 150, so 150 is base 1 and 201 is base 151
            cds(MINUS, 1, 150, te("148..150", "Met"), te("201..203", "Sec"), te("147..149", "Trp"));
            cds(PLUS, 201, 350, te("148..150", "Met"), te("201..203", "Sec"), te("147..149", "Trp"));

            String violations = violationsOf(this::validate);
            assertTrue(violations.contains("\"(pos:147..149,aa:Trp)\" starts 1 base into a codon on strand \"-\""));
            assertFalse(violations.contains("(pos:148..150,aa:Met)"));
            assertFalse(violations.contains("(pos:201..203,aa:Sec)"));
        }

        @ParameterizedTest
        @ValueSource(strings = {GFF3Attributes.PSEUDO, GFF3Attributes.PSEUDOGENE, GFF3Attributes.EXCEPTION})
        void leavesOutCdssNotReadInOneFrame(String attribute) {
            cds(1, 300, te("5..7", "Sec")).addAttribute(attribute, "true");

            assertDoesNotThrow(this::validate);
        }

        @ParameterizedTest
        @ValueSource(strings = {"4", "x"})
        void failsOnAnInvalidCodonStart(String codonStart) {
            cds(1, 300, te("5..7", "Sec")).addAttribute(GFF3Attributes.CODON_START, codonStart);

            String violations = violationsOf(this::validate);
            assertTrue(violations.contains("codon_start must be 1, 2 or 3"));
            assertTrue(violations.contains("CDS \"cds1\" at seq1:1..300 has codon_start \"%s\"".formatted(codonStart)));
        }

        @ParameterizedTest
        @CsvSource({"200..204, Term", "5..6, Sec"})
        void leavesValuesOfNoCodonLengthToTranslExceptLocation(String position, String aminoAcid) {
            cds(1, 300, te(position, aminoAcid));

            assertDoesNotThrow(this::validate);
        }

        @Test
        void reportsEveryCdsTogetherWithTheLineOfItsFirstRow() {
            cds(CDS_ID, PLUS, 1, 300, te("5..7", "Sec")).setLine(7);
            cds("cds2", PLUS, 1001, 1300, te("1002..1004", "Sec"));

            String violations = violationsOf(this::validate);
            assertTrue(violations.contains("CDS \"cds1\" at seq1:1..300 (line 7):"));
            assertTrue(violations.contains("CDS \"cds2\" at seq1:1001..1300:"));
        }

        private void validate() throws ValidationException {
            validation.validateReadingFrame(annotation, 1);
        }
    }

    private static String violationsOf(Executable rule) {
        return assertThrows(ValidationException.class, rule).getMessage();
    }

    private static String te(String position, String aminoAcid) {
        return "(pos:%s,aa:%s)".formatted(position, aminoAcid);
    }

    private GFF3Feature cds(long start, long end, String... translExcepts) {
        return cds(CDS_ID, PLUS, start, end, translExcepts);
    }

    private GFF3Feature cds(String strand, long start, long end, String... translExcepts) {
        return cds(CDS_ID, strand, start, end, translExcepts);
    }

    /** One row of a CDS; rows sharing an ID form a join. */
    private GFF3Feature cds(String id, String strand, long start, long end, String... translExcepts) {
        GFF3Feature row = TestUtils.createGFF3Feature(id, OntologyTerm.CDS.name(), SEQ_ID, start, end, strand);
        for (String translExcept : translExcepts) {
            row.addAttribute(TRANSL_EXCEPT, translExcept);
        }
        annotation.addFeature(row);
        return row;
    }
}
