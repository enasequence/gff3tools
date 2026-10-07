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
package uk.ac.ebi.embl.gff3tools.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.gff3tools.validation.builtin.AssemblyGapValidation;

class GapOptionsValidatorTest {

    /** The three INSDC gap types for which linkage_evidence is required and allowed. */
    private static final Set<String> REQUIRE_LINKAGE =
            Set.of("within scaffold", "repeat within scaffold", "contamination");

    private static void assertValid(String gapType, String linkageEvidence) {
        assertEquals(
                Optional.empty(),
                GapOptionsValidator.validate(gapType, linkageEvidence),
                () -> "expected valid: gap_type=%s linkage_evidence=%s".formatted(gapType, linkageEvidence));
    }

    private static String assertRejected(String gapType, String linkageEvidence) {
        Optional<String> problem = GapOptionsValidator.validate(gapType, linkageEvidence);
        assertTrue(problem.isPresent(), () -> "expected rejection: gap_type=%s linkage_evidence=%s"
                .formatted(gapType, linkageEvidence));
        return problem.get();
    }

    // ------------------------------------------------------------- nothing supplied

    @Test
    void acceptsBothAbsent() {
        assertValid(null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void treatsBlankAsNotSupplied(String blank) {
        assertValid(blank, blank);
    }

    // ------------------------------------------------------- linkage without gap type

    @Test
    void rejectsLinkageEvidenceWithoutGapType() {
        assertEquals("linkage_evidence requires a gap_type to be supplied", assertRejected(null, "paired-ends"));
    }

    @Test
    void rejectsLinkageEvidenceWithBlankGapType() {
        assertEquals("linkage_evidence requires a gap_type to be supplied", assertRejected("  ", "paired-ends"));
    }

    // ----------------------------------------------------------------- vocabulary

    /**
     * Every gap type the engine accepts must also be accepted here, supplying linkage_evidence for
     * the three that require it. This is what keeps the CLI check from drifting away from
     * {@link AssemblyGapValidation#validGapTypes()}.
     */
    @ParameterizedTest
    @MethodSource("validGapTypes")
    void acceptsEveryGapTypeTheEngineAccepts(String gapType) {
        assertValid(gapType, REQUIRE_LINKAGE.contains(gapType) ? "paired-ends" : null);
    }

    private static Stream<String> validGapTypes() {
        return AssemblyGapValidation.validGapTypes().stream();
    }

    @Test
    void rejectsGapTypeOutsideTheVocabulary() {
        assertEquals(
                "gap_type \"between contigs\" is not a valid INSDC gap_type", assertRejected("between contigs", null));
    }

    @Test
    void reportsTheInvalidGapTypeEvenWhenLinkageEvidenceIsAlsoSupplied() {
        // Vocabulary is checked before the linkage relationship, so the user is told the actual
        // problem rather than being sent to fix linkage_evidence on a gap_type that cannot work.
        assertEquals(
                "gap_type \"between contigs\" is not a valid INSDC gap_type",
                assertRejected("between contigs", "paired-ends"));
    }

    @Test
    void rejectionMessageQuotesTheTrimmedInput() {
        assertEquals("gap_type \"nonsense\" is not a valid INSDC gap_type", assertRejected("  nonsense  ", null));
    }

    // ------------------------------------------------------------ linkage relationship

    @ParameterizedTest
    @ValueSource(strings = {"within scaffold", "repeat within scaffold", "contamination"})
    void acceptsLinkageRequiringGapTypeWhenLinkageSupplied(String gapType) {
        assertValid(gapType, "paired-ends");
    }

    @ParameterizedTest
    @ValueSource(strings = {"within scaffold", "repeat within scaffold", "contamination"})
    void rejectsLinkageRequiringGapTypeWithoutLinkage(String gapType) {
        assertEquals(
                "gap_type \"%s\" requires a linkage_evidence to be supplied".formatted(gapType),
                assertRejected(gapType, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"between scaffolds", "centromere", "telomere", "unknown", "short arm"})
    void acceptsOtherGapTypesWithoutLinkage(String gapType) {
        assertValid(gapType, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"between scaffolds", "centromere", "telomere", "unknown", "short arm"})
    void rejectsLinkageOnGapTypesThatDoNotAllowIt(String gapType) {
        assertEquals(
                "linkage_evidence is only valid with gap_type "
                        + "\"within scaffold\", \"repeat within scaffold\" or \"contamination\"",
                assertRejected(gapType, "paired-ends"));
    }

    @Test
    void treatsBlankLinkageAsNotSuppliedForATypeThatForbidsIt() {
        assertValid("telomere", "   ");
    }

    @Test
    void treatsBlankLinkageAsMissingForATypeThatRequiresIt() {
        assertEquals(
                "gap_type \"within scaffold\" requires a linkage_evidence to be supplied",
                assertRejected("within scaffold", "   "));
    }

    // --------------------------------------------------------- normalisation of input

    @ParameterizedTest
    @ValueSource(strings = {"WITHIN SCAFFOLD", "Within Scaffold", "  within scaffold  "})
    void normalisesCaseAndSurroundingWhitespace(String gapType) {
        assertValid(gapType, "paired-ends");
    }

    // ------------------------------------------------------------------ normaliseGapType

    @ParameterizedTest
    @ValueSource(strings = {"WITHIN SCAFFOLD", "Within Scaffold", "  Within Scaffold  ", "within scaffold"})
    void normaliseGapTypeReturnsTheFormTheVocabularyContains(String supplied) {
        String normalised = GapOptionsValidator.normaliseGapType(supplied);
        assertEquals("within scaffold", normalised);
        assertTrue(
                AssemblyGapValidation.validGapTypes().contains(normalised),
                "the normalised form must be the one AssemblyGapValidation would accept");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void normaliseGapTypeTreatsBlankAsNotSupplied(String blank) {
        assertNull(GapOptionsValidator.normaliseGapType(blank));
    }

    @Test
    void normaliseGapTypeTreatsNullAsNotSupplied() {
        assertNull(GapOptionsValidator.normaliseGapType(null));
    }

    /**
     * Every accepted gap_type must survive normalisation unchanged, otherwise validate() and the
     * stored value would disagree for that entry.
     */
    @ParameterizedTest
    @MethodSource("validGapTypes")
    void normaliseGapTypeIsIdempotentOverTheWholeVocabulary(String gapType) {
        assertEquals(gapType, GapOptionsValidator.normaliseGapType(gapType));
    }

    // ----------------------------------------------------------- normaliseLinkageEvidence

    @Test
    void normaliseLinkageEvidenceTrimsButKeepsCase() {
        // Not checked against a vocabulary, so there is nothing to lower-case it for.
        assertEquals("Paired-Ends", GapOptionsValidator.normaliseLinkageEvidence("  Paired-Ends  "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void normaliseLinkageEvidenceTreatsBlankAsNotSupplied(String blank) {
        assertNull(GapOptionsValidator.normaliseLinkageEvidence(blank));
    }

    @Test
    void normaliseLinkageEvidenceTreatsNullAsNotSupplied() {
        assertNull(GapOptionsValidator.normaliseLinkageEvidence(null));
    }

    @Test
    void doesNotValidateTheLinkageEvidenceValueItself() {
        // The vocabulary for linkage_evidence is not checked here - only its presence relative to
        // gap_type. AssemblyGapValidation owns the value check for submitted features.
        assertValid("within scaffold", "not-a-real-evidence-type");
    }
}
