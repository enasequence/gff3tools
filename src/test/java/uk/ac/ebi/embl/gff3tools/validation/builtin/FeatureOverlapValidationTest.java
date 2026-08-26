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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

class FeatureOverlapValidationTest {

    private static final String SEQ_ID = "seq1";
    private static final String OTHER_SEQ_ID = "seq2";

    private FeatureOverlapValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new FeatureOverlapValidation();
        TestUtils.injectContext(validation);
        annotation = new GFF3Annotation();
    }

    /** INSDC Annotation Minimum Specification b.iv.1. */
    @Nested
    class FeatureIntervalOverlap {

        @Test
        void failsWhenTwoIntervalsOfOneFeatureShareABase() {
            addFeatures(cds("cds1", 100L, 300L), cds("cds1", 300L, 400L));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateFeatureIntervalOverlap(annotation, 1));

            assertTrue(exception.getMessage().contains("100..300"));
            assertTrue(exception.getMessage().contains("300..400"));
            assertTrue(exception.getMessage().contains(SEQ_ID));
        }

        @Test
        void passesWhenIntervalsOnlyAbut() {
            addFeatures(cds("cds1", 100L, 300L), cds("cds1", 301L, 400L));

            assertDoesNotThrow(() -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }

        @Test
        void passesWhenIntervalsAreDisjoint() {
            addFeatures(cds("cds1", 100L, 200L), cds("cds1", 300L, 400L));

            assertDoesNotThrow(() -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }

        @Test
        void passesWhenTheOverlapIsDeclaredAsRibosomalSlippage() {
            // The one exception the specification allows: a programmed frameshift.
            addFeatures(
                    cds("cds1", 100L, 300L, Map.of(GFF3Attributes.RIBOSOMAL_SLIPPAGE, List.of("true"))),
                    cds("cds1", 290L, 400L));

            assertDoesNotThrow(() -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }

        @Test
        void passesWhenAFeatureHasASingleInterval() {
            addFeatures(cds("cds1", 100L, 300L));

            assertDoesNotThrow(() -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }

        @Test
        void passesWhenIdenticalIntervalsBelongToDifferentFeatures() {
            // The rule speaks about the intervals within one feature, not about two features meeting.
            addFeatures(cds("cds1", 100L, 300L), cds("cds2", 100L, 300L));

            assertDoesNotThrow(() -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }

        @Test
        void passesWhenSegmentsSharingAnIdLieOnDifferentSequences() {
            // Coordinates only meet when they are counted from the same sequence.
            addFeatures(
                    feature(OntologyTerm.CDS.name(), "cds1", SEQ_ID, 100L, 300L),
                    feature(OntologyTerm.CDS.name(), "cds1", OTHER_SEQ_ID, 100L, 300L));

            assertDoesNotThrow(() -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }

        @Test
        void ignoresFeaturesWithoutAnId() {
            // Two unrelated features are not one feature whose intervals overlap.
            addFeatures(
                    feature(OntologyTerm.CDS.name(), null, SEQ_ID, 100L, 300L),
                    feature(OntologyTerm.CDS.name(), null, SEQ_ID, 200L, 400L));

            assertDoesNotThrow(() -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }

        @Test
        void reportsEveryFeatureWhoseIntervalsOverlap() {
            addFeatures(
                    cds("cds1", 100L, 300L), cds("cds1", 250L, 400L),
                    cds("cds2", 1000L, 1300L), cds("cds2", 1250L, 1400L));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateFeatureIntervalOverlap(annotation, 1));

            assertTrue(exception.getMessage().contains("100..400"));
            assertTrue(exception.getMessage().contains("1000..1400"));
        }

        @Test
        void appliesToAnyFeatureTypeNotOnlyCds() {
            addFeatures(feature("tRNA", "trna1", SEQ_ID, 100L, 300L), feature("tRNA", "trna1", SEQ_ID, 250L, 400L));

            assertThrows(ValidationException.class, () -> validation.validateFeatureIntervalOverlap(annotation, 1));
        }
    }

    /** INSDC Annotation Minimum Specification b.iv.2. */
    @Nested
    class RrnaOverlap {

        @Test
        void failsWhenAnRrnaOverlapsACds() {
            addFeatures(rrna("rrna1", 100L, 300L), cds("cds1", 200L, 400L));

            ValidationException exception =
                    assertThrows(ValidationException.class, () -> validation.validateRrnaOverlap(annotation, 1));

            assertTrue(exception.getMessage().contains("overlaps CDS"));
            assertTrue(exception.getMessage().contains("100..300"));
            assertTrue(exception.getMessage().contains("200..400"));
        }

        @Test
        void failsWhenAnRrnaOverlapsAnotherRrna() {
            addFeatures(rrna("rrna1", 100L, 300L), rrna("rrna2", 250L, 400L));

            ValidationException exception =
                    assertThrows(ValidationException.class, () -> validation.validateRrnaOverlap(annotation, 1));

            assertTrue(exception.getMessage().contains("overlaps rRNA"));
        }

        @Test
        void reportsAPairOfOverlappingRrnasOnlyOnce() {
            addFeatures(rrna("rrna1", 100L, 300L), rrna("rrna2", 250L, 400L));

            ValidationException exception =
                    assertThrows(ValidationException.class, () -> validation.validateRrnaOverlap(annotation, 1));

            assertEquals(1, occurrences(exception.getMessage(), "overlaps rRNA"));
        }

        @Test
        void passesWhenAnRrnaOnlyAbutsACds() {
            addFeatures(rrna("rrna1", 100L, 300L), cds("cds1", 301L, 400L));

            assertDoesNotThrow(() -> validation.validateRrnaOverlap(annotation, 1));
        }

        @Test
        void passesWhenAnRrnaSitsInAnIntronOfASplicedCds() {
            // The rRNA is inside the span the CDS covers while sharing no base with its exons.
            addFeatures(cds("cds1", 100L, 200L), cds("cds1", 400L, 500L), rrna("rrna1", 250L, 350L));

            assertDoesNotThrow(() -> validation.validateRrnaOverlap(annotation, 1));
        }

        @Test
        void passesWhenTwoCdsOverlapAndNoRrnaIsPresent() {
            addFeatures(cds("cds1", 100L, 300L), cds("cds2", 200L, 400L));

            assertDoesNotThrow(() -> validation.validateRrnaOverlap(annotation, 1));
        }

        @Test
        void passesWhenAnRrnaAndACdsLieOnDifferentSequences() {
            addFeatures(
                    feature("rRNA", "rrna1", SEQ_ID, 100L, 300L),
                    feature(OntologyTerm.CDS.name(), "cds1", OTHER_SEQ_ID, 200L, 400L));

            assertDoesNotThrow(() -> validation.validateRrnaOverlap(annotation, 1));
        }

        @Test
        void recognisesDescendantsOfRrna() {
            addFeatures(feature("cytosolic_16S_rRNA", "rrna1", SEQ_ID, 100L, 300L), cds("cds1", 200L, 400L));

            assertThrows(ValidationException.class, () -> validation.validateRrnaOverlap(annotation, 1));
        }

        @Test
        void ignoresPseudogenicRrna() {
            // pseudogenic_rRNA sits under pseudogenic_transcript, not under rRNA.
            addFeatures(feature("pseudogenic_rRNA", "rrna1", SEQ_ID, 100L, 300L), cds("cds1", 200L, 400L));

            assertDoesNotThrow(() -> validation.validateRrnaOverlap(annotation, 1));
        }
    }

    /** INSDC Annotation Minimum Specification b.iv.3. */
    @Nested
    class TrnaWithinCdsExon {

        @Test
        void failsWhenATrnaLiesEntirelyInsideACdsExon() {
            addFeatures(cds("cds1", 3000L, 3900L), trna("trna1", 3100L, 3170L));

            ValidationException exception =
                    assertThrows(ValidationException.class, () -> validation.validateTrnaWithinCdsExon(annotation, 1));

            assertTrue(exception.getMessage().contains("3100..3170"));
            assertTrue(exception.getMessage().contains("3000..3900"));
            assertTrue(exception.getMessage().contains(SEQ_ID));
        }

        @Test
        void failsWhenATrnaExactlyMatchesACdsExon() {
            addFeatures(cds("cds1", 3000L, 3070L), trna("trna1", 3000L, 3070L));

            assertThrows(ValidationException.class, () -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void failsWhenEverySegmentOfASplicedTrnaLiesInsideAnExon() {
            addFeatures(
                    cds("cds1", 100L, 200L), cds("cds1", 400L, 500L),
                    trna("trna1", 110L, 150L), trna("trna1", 420L, 450L));

            assertThrows(ValidationException.class, () -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void passesWhenATrnaOnlyPartlyOverlapsACds() {
            // Running into a coding region is not being contained by it.
            addFeatures(cds("cds1", 3000L, 3900L), trna("trna1", 3850L, 3950L));

            assertDoesNotThrow(() -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void passesWhenATrnaLiesInAnIntronOfASplicedCds() {
            addFeatures(cds("cds1", 4000L, 4100L), cds("cds1", 4300L, 4400L), trna("trna1", 4150L, 4220L));

            assertDoesNotThrow(() -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void passesWhenOnlyPartOfASplicedTrnaLiesInsideTheExons() {
            addFeatures(
                    cds("cds1", 100L, 200L), cds("cds1", 400L, 500L),
                    trna("trna1", 110L, 150L), trna("trna1", 600L, 650L));

            assertDoesNotThrow(() -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void passesWhenACdsLiesInsideATrna() {
            addFeatures(cds("cds1", 3100L, 3170L), trna("trna1", 3000L, 3900L));

            assertDoesNotThrow(() -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void passesWhenThereIsNoCds() {
            addFeatures(trna("trna1", 3100L, 3170L));

            assertDoesNotThrow(() -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void passesWhenTheTrnaAndCdsLieOnDifferentSequences() {
            addFeatures(
                    feature(OntologyTerm.CDS.name(), "cds1", SEQ_ID, 3000L, 3900L),
                    feature("tRNA", "trna1", OTHER_SEQ_ID, 3100L, 3170L));

            assertDoesNotThrow(() -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }

        @Test
        void recognisesDescendantsOfTrna() {
            addFeatures(cds("cds1", 3000L, 3900L), feature("alanyl_tRNA", "trna1", SEQ_ID, 3100L, 3170L));

            assertThrows(ValidationException.class, () -> validation.validateTrnaWithinCdsExon(annotation, 1));
        }
    }

    private void addFeatures(GFF3Feature... features) {
        for (GFF3Feature f : features) {
            annotation.addFeature(f);
        }
    }

    private static int occurrences(String message, String needle) {
        return message.split(needle, -1).length - 1;
    }

    private GFF3Feature cds(String id, long start, long end) {
        return cds(id, start, end, Map.of());
    }

    private GFF3Feature cds(String id, long start, long end, Map<String, List<String>> attributes) {
        GFF3Feature f = feature(OntologyTerm.CDS.name(), id, SEQ_ID, start, end);
        f.addAttributes(attributes);
        return f;
    }

    private GFF3Feature rrna(String id, long start, long end) {
        return feature("rRNA", id, SEQ_ID, start, end);
    }

    private GFF3Feature trna(String id, long start, long end) {
        return feature("tRNA", id, SEQ_ID, start, end);
    }

    private GFF3Feature feature(String name, String id, String seqId, long start, long end) {
        return new GFF3Feature(
                id == null ? Optional.empty() : Optional.of(id),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                name,
                start,
                end,
                ".",
                "+",
                "0");
    }
}
