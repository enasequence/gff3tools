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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

public class LengthValidationTest {

    GFF3Feature feature;

    GFF3Annotation gff3Annotation;

    private LengthValidation lengthValidation;

    @BeforeEach
    public void setUp() {
        lengthValidation = new LengthValidation();
        TestUtils.injectContext(lengthValidation);
        gff3Annotation = new GFF3Annotation();
    }

    @Test
    public void testCdsIntronValidationSuccess() {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1L, 100L, Map.of(GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1")));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 115L, 200L, Map.of(GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1")));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        assertDoesNotThrow(() -> lengthValidation.validateCdsIntronLength(gff3Annotation, 1));
    }

    @Test
    public void testCdsIntronValidationSuccessWithArtificialLocation() {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                1L,
                100L,
                Map.of(
                        GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1"),
                        GFF3Attributes.ARTIFICIAL_LOCATION, List.of("true")));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 105L, 200L, Map.of(GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1")));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        assertDoesNotThrow(() -> lengthValidation.validateCdsIntronLength(gff3Annotation, 1));
    }

    @Test
    public void testCdsIntronValidationSuccessWithPseudo() {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1L, 100L, Map.of(GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1")));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                105L,
                200L,
                Map.of(
                        GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1"),
                        GFF3Attributes.PSEUDO, List.of("true")));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        assertDoesNotThrow(() -> lengthValidation.validateCdsIntronLength(gff3Annotation, 1));
    }

    @Test
    public void testCdsIntronValidationFailureSmallIntron() {

        GFF3Feature cds1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1L, 100L, Map.of(GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1")));

        GFF3Feature cds2 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 102L, 200L, Map.of(GFF3Attributes.ATTRIBUTE_ID, List.of("CDS1")));

        gff3Annotation.addFeature(cds1);
        gff3Annotation.addFeature(cds2);

        ValidationException ex = assertThrows(
                ValidationException.class, () -> lengthValidation.validateCdsIntronLength(gff3Annotation, 1));

        assertTrue(ex.getMessage().contains("Intron usually expected to be at least 10 nt long"));
    }

    @Test
    public void testIntronValidationForCDSSuccessWithPseudo() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), 1L, 5L, Map.of(GFF3Attributes.PSEUDO, List.of("pseudo")));
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
                OntologyTerm.PROPEPTIDE.name(),
                1L,
                13L,
                Map.of(GFF3Attributes.EXCEPTION, List.of("ribosomal slippage")));
        Assertions.assertDoesNotThrow(() -> lengthValidation.validatePropeptideLength(feature, 1));
    }

    @Test
    public void testPropetideValidationSuccessForTranslExcept() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.PROPEPTIDE.name(),
                1L,
                31L,
                Map.of(GFF3Attributes.TRANSL_EXCEPT, List.of("ribosomal slippage")));
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

    @Nested
    class CdsLengthValidation {

        private static final String SEQ_ID = "seq1";

        private LengthValidation validation;
        private TranslationState translationState;

        @BeforeEach
        void setUp() {
            validation = new LengthValidation();
            ValidationContext context = TestUtils.createTestContext();
            context.register(TranslationState.class, new TranslationStateProvider());
            translationState = context.get(TranslationState.class);
            TestUtils.injectContext(validation, context);
            gff3Annotation = new GFF3Annotation();
        }

        @Test
        void failsWhenCompleteCdsIsShorterThanTheMinimum() {
            addFeatures(cds("cds1", 1L, 9L));

            ValidationException exception =
                    assertThrows(ValidationException.class, () -> validation.validateCdsLength(gff3Annotation, 1));

            assertTrue(exception.getMessage().contains("Complete coding regions must be at least"));
            assertTrue(exception.getMessage().contains(SEQ_ID));
        }

        @Test
        void passesWhenCompleteCdsIsLongEnough() {
            addFeatures(cds("cds1", 1L, 300L));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void passesAtExactlyTheMinimumNucleotideLength() {
            // 25 amino acids plus the terminal stop codon that INSDC includes in the coding region.
            addFeatures(cds("cds1", 1L, 78L));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void failsOneNucleotideBelowTheMinimum() {
            addFeatures(cds("cds1", 1L, 77L));

            assertThrows(ValidationException.class, () -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void sumsSegmentsOfASplicedCdsRatherThanMeasuringItsSpan() {
            // The segments span 1-230 but encode only 61 nucleotides.
            addFeatures(cds("cds1", 1L, 30L), cds("cds1", 200L, 230L));

            assertThrows(ValidationException.class, () -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void passesWhenSegmentsOfASplicedCdsSumToTheMinimum() {
            addFeatures(cds("cds1", 1L, 40L), cds("cds1", 200L, 238L));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void skipsFivePrimePartialCds() {
            addFeatures(cds("cds1", 1L, 9L, Map.of(GFF3Attributes.PARTIAL, List.of("start"))));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void skipsThreePrimePartialCds() {
            addFeatures(cds("cds1", 1L, 9L, Map.of(GFF3Attributes.PARTIAL, List.of("end"))));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void skipsPartialityDeclaredOnABoundarySegmentOnly() {
            addFeatures(cds("cds1", 1L, 30L), cds("cds1", 200L, 230L, Map.of(GFF3Attributes.PARTIAL, List.of("end"))));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void skipsPseudoCds() {
            addFeatures(cds("cds1", 1L, 9L, Map.of(GFF3Attributes.PSEUDO, List.of("true"))));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void skipsShortCdsWithExperimentEvidence() {
            addFeatures(cds("cds1", 1L, 9L, Map.of(GFF3Attributes.EXPERIMENT, List.of("northern blot"))));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void skipsShortCdsWithInferenceEvidence() {
            addFeatures(cds(
                    "cds1",
                    1L,
                    9L,
                    Map.of(GFF3Attributes.INFERENCE, List.of("similar to AA sequence:UniProtKB:P0001"))));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void skipsTranslExceptCdsWhenNoTranslationWasComputed() {
            // A one or two base stop codon leaves a complete coding region short of a multiple of
            // three, so the nucleotide measure cannot be trusted for these features.
            addFeatures(cds("cds1", 1L, 9L, Map.of(GFF3Attributes.TRANSL_EXCEPT, List.of("(pos:8..9,aa:TERM)"))));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void countsRecordedAminoAcidsInPreferenceToNucleotides() {
            // Long enough in nucleotides, but the computed protein is one amino acid short.
            addFeatures(cds("cds1", 1L, 300L));
            recordTranslation("cds1", "M".repeat(24));

            assertThrows(ValidationException.class, () -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void passesWhenTheRecordedTranslationMeetsTheMinimum() {
            // Too short in nucleotides, but the computed protein is long enough.
            addFeatures(cds("cds1", 1L, 9L));
            recordTranslation("cds1", "M".repeat(25));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void fallsBackToNucleotidesWhenTheRecordedTranslationIsEmpty() {
            addFeatures(cds("cds1", 1L, 9L));
            recordTranslation("cds1", "");

            assertThrows(ValidationException.class, () -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void ignoresFeaturesThatAreNotCds() {
            addFeatures(feature("gene", "gene1", 1L, 9L, Map.of()));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void ignoresCdsSynonymsAndCdsExtensions() {
            // Neither is translated, so neither is measured here.
            addFeatures(
                    feature("coding_sequence", "syn1", 1L, 9L, Map.of()),
                    feature("CDS_extension", "ext1", 20L, 28L, Map.of()));

            assertDoesNotThrow(() -> validation.validateCdsLength(gff3Annotation, 1));
        }

        @Test
        void measuresEachCodingRegionSeparately() {
            addFeatures(cds("cds1", 1L, 300L), cds("cds2", 400L, 408L));

            assertThrows(ValidationException.class, () -> validation.validateCdsLength(gff3Annotation, 1));
        }

        private void addFeatures(GFF3Feature... features) {
            for (GFF3Feature f : features) {
                gff3Annotation.addFeature(f);
            }
        }

        private void recordTranslation(String featureId, String translation) {
            translationState.record(TranslationState.buildKey(SEQ_ID, featureId), null, translation);
        }

        private GFF3Feature cds(String id, long start, long end) {
            return cds(id, start, end, Map.of());
        }

        private GFF3Feature cds(String id, long start, long end, Map<String, List<String>> attributes) {
            return feature(OntologyTerm.CDS.name(), id, start, end, attributes);
        }

        private GFF3Feature feature(
                String name, String id, long start, long end, Map<String, List<String>> attributes) {
            GFF3Feature f = new GFF3Feature(
                    Optional.of(id), Optional.empty(), SEQ_ID, Optional.empty(), ".", name, start, end, ".", "+", "0");
            f.addAttributes(attributes);
            return f;
        }
    }
}
