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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class MoleculeTypeValidationTest {

    private static final int LINE = 42;
    private static final String ACCESSION = "CM000001.1";

    private MoleculeTypeValidation validation;
    private ValidationContext context;
    private FastaHeaderProvider fastaHeaderProvider;
    private OntologyClient ontologyClient;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() throws Exception {
        validation = new MoleculeTypeValidation();
        context = mock(ValidationContext.class);
        fastaHeaderProvider = mock(FastaHeaderProvider.class);
        ontologyClient = mock(OntologyClient.class);
        annotation = mock(GFF3Annotation.class);

        when(context.contains(FastaHeaderProvider.class)).thenReturn(true);
        when(context.get(FastaHeaderProvider.class)).thenReturn(fastaHeaderProvider);
        when(context.get(OntologyClient.class)).thenReturn(ontologyClient);
        when(annotation.getAccession()).thenReturn(ACCESSION);

        injectContext(validation, context);
    }

    @Nested
    class ValidateRequiredFeature {

        @Test
        void doesNothingWhenMoleculeTypeDoesNotRequireAFeature() {
            when(fastaHeaderProvider.getHeader(ACCESSION))
                    .thenReturn(Optional.of(headerWithMoleculeType("genomic DNA")));

            assertDoesNotThrow(() -> validation.validateRequiredFeature(annotation, LINE));
        }

        @Test
        void doesNothingWhenRequiredFeatureIsPresent() {
            GFF3Feature feature = feature("ribosomal RNA");
            when(annotation.getFeatures()).thenReturn(List.of(feature));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("rRNA")));
            when(ontologyClient.findTermByNameOrSynonym("ribosomal RNA")).thenReturn(Optional.of(OntologyTerm.RRNA.ID));
            when(ontologyClient.isSelfOrDescendantOf(OntologyTerm.RRNA.ID, OntologyTerm.RRNA.ID))
                    .thenReturn(true);

            assertDoesNotThrow(() -> validation.validateRequiredFeature(annotation, LINE));
        }

        @Test
        void throwsValidationExceptionWhenRequiredFeatureIsMissing() {
            GFF3Feature feature = feature("gene");
            when(annotation.getFeatures()).thenReturn(List.of(feature));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("tRNA")));
            when(ontologyClient.findTermByNameOrSynonym("gene")).thenReturn(Optional.of(OntologyTerm.GENE.ID));
            when(ontologyClient.isSelfOrDescendantOf(OntologyTerm.GENE.ID, OntologyTerm.TRNA.ID))
                    .thenReturn(false);

            ValidationException exception =
                    assertThrows(ValidationException.class, () -> validation.validateRequiredFeature(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(MoleculeTypeValidation.REQUIRED_FEATURE_RULE));
            assertTrue(message.contains("Feature TRNA is required when molecule type is TRNA."));
        }
    }

    @Nested
    class ValidateMrnaCdsComplement {

        @Test
        void doesNothingWhenEntryIsNotMrna() {
            GFF3Feature complementCds = feature("CDS", true);
            when(annotation.getFeatures()).thenReturn(List.of(complementCds));
            when(fastaHeaderProvider.getHeader(ACCESSION))
                    .thenReturn(Optional.of(headerWithMoleculeType("genomic DNA")));

            assertDoesNotThrow(() -> validation.validateMrnaCdsComplement(annotation, LINE));
        }

        @Test
        void doesNothingWhenMrnaCdsIsNotComplement() {
            GFF3Feature cds = feature("CDS", false);
            when(annotation.getFeatures()).thenReturn(List.of(cds));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("mRNA")));
            when(ontologyClient.findTermByNameOrSynonym("CDS")).thenReturn(Optional.of(OntologyTerm.CDS.ID));

            assertDoesNotThrow(() -> validation.validateMrnaCdsComplement(annotation, LINE));
        }

        @Test
        void throwsValidationExceptionWhenMrnaCdsIsComplement() {
            GFF3Feature complementCds = feature("CDS", true);
            when(annotation.getFeatures()).thenReturn(List.of(complementCds));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("mRNA")));
            when(ontologyClient.findTermByNameOrSynonym("CDS")).thenReturn(Optional.of(OntologyTerm.CDS.ID));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateMrnaCdsComplement(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(MoleculeTypeValidation.MRNA_CDS_COMPLEMENT_RULE));
            assertTrue(message.contains("Complement locations are not permitted in CDS features on mRNA entries."));
        }
    }

    @Nested
    class ValidateMrnaCdsJoinedLocation {

        @Test
        void doesNothingWhenEntryIsNotAProcessedTranscript() {
            // A genomic record still holds the introns, so a joined coding region is expected there.
            givenMoleculeTypeAndCds("genomic DNA", cdsSegment("cds1", 1L, 60L), cdsSegment("cds1", 100L, 159L));

            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }

        @Test
        void throwsWhenACodingRegionSpansTwoLocationsOnAnMrnaEntry() {
            givenMoleculeTypeAndCds("mRNA", cdsSegment("cds1", 1L, 60L), cdsSegment("cds1", 100L, 159L));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(MoleculeTypeValidation.MRNA_CDS_JOINED_LOCATION_RULE));
            assertTrue(message.contains("Coding regions must not span multiple joined locations on mRNA entries."));
            assertTrue(message.contains(ACCESSION));
            assertTrue(message.contains("spans 2 locations (1..60, 100..159)"));
        }

        @Test
        void throwsWhenACodingRegionSpansTwoLocationsOnATranscribedRnaEntry() {
            givenMoleculeTypeAndCds("transcribed RNA", cdsSegment("cds1", 1L, 60L), cdsSegment("cds1", 100L, 159L));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));

            assertTrue(exception
                    .getMessage()
                    .contains("Coding regions must not span multiple joined locations on transcribed RNA entries."));
        }

        @Test
        void doesNothingWhenTheCodingRegionOccupiesASingleLocation() {
            givenMoleculeTypeAndCds("mRNA", cdsSegment("cds1", 1L, 60L));

            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }

        @Test
        void doesNothingWhenTheJoinIsDeclaredAsRibosomalSlippage() {
            // The one exception the specification allows: a programmed frameshift.
            givenMoleculeTypeAndCds("mRNA", cdsSegment("cds1", 1L, 60L, true), cdsSegment("cds1", 100L, 159L, true));

            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }

        @Test
        void doesNothingWhenSlippageIsDeclaredOnOneSegmentOnly() {
            givenMoleculeTypeAndCds("mRNA", cdsSegment("cds1", 1L, 60L, true), cdsSegment("cds1", 100L, 159L));

            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }

        @Test
        void doesNothingWhenTwoDistinctCodingRegionsEachOccupyOneLocation() {
            givenMoleculeTypeAndCds("mRNA", cdsSegment("cdsA", 1L, 60L), cdsSegment("cdsB", 100L, 159L));

            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }

        @Test
        void doesNothingWhenCdsLinesCarryNoId() {
            // Without a shared ID these are two coding regions, not one spanning two locations.
            givenMoleculeTypeAndCds("mRNA", cdsSegment(null, 1L, 60L), cdsSegment(null, 100L, 159L));

            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }

        @Test
        void doesNothingWhenTheJoinedFeatureIsNotACodingRegion() {
            GFF3Feature first = segment("gene", "gene1", 1L, 60L, false);
            GFF3Feature second = segment("gene", "gene1", 100L, 159L, false);
            when(annotation.getFeatures()).thenReturn(List.of(first, second));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("mRNA")));
            when(ontologyClient.findTermByNameOrSynonym("gene")).thenReturn(Optional.of(OntologyTerm.GENE.ID));

            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }

        @Test
        void reportsEveryLocationInCoordinateOrder() {
            givenMoleculeTypeAndCds(
                    "mRNA",
                    cdsSegment("cds1", 200L, 259L),
                    cdsSegment("cds1", 1L, 60L),
                    cdsSegment("cds1", 100L, 159L));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));

            assertTrue(exception.getMessage().contains("spans 3 locations (1..60, 100..159, 200..259)"));
        }

        private void givenMoleculeTypeAndCds(String moleculeType, GFF3Feature... segments) {
            when(annotation.getFeatures()).thenReturn(List.of(segments));
            when(fastaHeaderProvider.getHeader(ACCESSION))
                    .thenReturn(Optional.of(headerWithMoleculeType(moleculeType)));
            when(ontologyClient.findTermByNameOrSynonym("CDS")).thenReturn(Optional.of(OntologyTerm.CDS.ID));
        }
    }

    @Nested
    class ValidateForbiddenFeature {

        @Test
        void doesNothingWhenMoleculeTypeHasNoForbiddenFeatures() {
            GFF3Feature feature = feature("CDS");
            when(annotation.getFeatures()).thenReturn(List.of(feature));
            when(fastaHeaderProvider.getHeader(ACCESSION))
                    .thenReturn(Optional.of(headerWithMoleculeType("genomic DNA")));

            assertDoesNotThrow(() -> validation.validateForbiddenFeature(annotation, LINE));
        }

        @Test
        void doesNothingWhenFeatureIsPermittedForTheMoleculeType() {
            GFF3Feature feature = feature("CDS");
            when(annotation.getFeatures()).thenReturn(List.of(feature));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("mRNA")));
            when(ontologyClient.findTermByNameOrSynonym("CDS")).thenReturn(Optional.of(OntologyTerm.CDS.ID));

            assertDoesNotThrow(() -> validation.validateForbiddenFeature(annotation, LINE));
        }

        @Test
        void doesNothingWhenRrnaFeatureIsOnRrnaMoleculeType() {
            GFF3Feature feature = feature("rRNA");
            when(annotation.getFeatures()).thenReturn(List.of(feature));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("rRNA")));
            when(ontologyClient.findTermByNameOrSynonym("rRNA")).thenReturn(Optional.of(OntologyTerm.RRNA.ID));

            assertDoesNotThrow(() -> validation.validateForbiddenFeature(annotation, LINE));
        }

        @Test
        void doesNothingWhenGeneFeatureIsOnMrnaMoleculeType() {
            GFF3Feature feature = feature("gene");
            when(annotation.getFeatures()).thenReturn(List.of(feature));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("mRNA")));
            when(ontologyClient.findTermByNameOrSynonym("gene")).thenReturn(Optional.of(OntologyTerm.GENE.ID));

            assertDoesNotThrow(() -> validation.validateForbiddenFeature(annotation, LINE));
        }

        @Test
        void doesNothingWhenFeatureNameIsNotFoundInTheOntology() {
            GFF3Feature feature = feature("not_a_so_term");
            when(annotation.getFeatures()).thenReturn(List.of(feature));
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(headerWithMoleculeType("rRNA")));
            when(ontologyClient.findTermByNameOrSynonym("not_a_so_term")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> validation.validateForbiddenFeature(annotation, LINE));
        }

        @Test
        void throwsValidationExceptionWhenMrnaFeatureIsOnMrnaMoleculeType() {
            assertForbidden("mRNA", "mRNA", OntologyTerm.MRNA, OntologyTerm.MRNA);
        }

        @Test
        void throwsValidationExceptionWhenTrnaFeatureIsOnMrnaMoleculeType() {
            assertForbidden("mRNA", "tRNA", OntologyTerm.TRNA, OntologyTerm.TRNA);
        }

        @Test
        void throwsValidationExceptionWhenTrnaFeatureIsOnRrnaMoleculeType() {
            assertForbidden("rRNA", "tRNA", OntologyTerm.TRNA, OntologyTerm.TRNA);
        }

        @Test
        void throwsValidationExceptionWhenCdsFeatureIsOnRrnaMoleculeType() {
            assertForbidden("rRNA", "CDS", OntologyTerm.CDS, OntologyTerm.CDS);
        }

        @Test
        void throwsValidationExceptionWhenFeatureIsADescendantOfAForbiddenFeature() {
            assertForbidden("rRNA", "pseudogenic_CDS", OntologyTerm.PSEUDOGENIC_CDS, OntologyTerm.CDS);
        }

        // Asserts that a feature resolving to featureTerm is rejected on the given molecule type
        // because featureTerm is (or descends from) the forbidden forbiddenParent.
        private void assertForbidden(
                String moleculeType, String featureName, OntologyTerm featureTerm, OntologyTerm forbiddenParent) {
            GFF3Feature gff3Feature = feature(featureName);
            when(annotation.getFeatures()).thenReturn(List.of(gff3Feature));
            when(fastaHeaderProvider.getHeader(ACCESSION))
                    .thenReturn(Optional.of(headerWithMoleculeType(moleculeType)));
            when(ontologyClient.findTermByNameOrSynonym(featureName)).thenReturn(Optional.of(featureTerm.ID));
            when(ontologyClient.isSelfOrDescendantOf(featureTerm.ID, forbiddenParent.ID))
                    .thenReturn(true);

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateForbiddenFeature(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(MoleculeTypeValidation.FORBIDDEN_FEATURE_RULE));
            assertTrue(message.contains("Feature %s is not permitted when molecule type is %s."
                    .formatted(featureName, moleculeTypeName(moleculeType))));
        }
    }

    @Nested
    class NoFastaHeaderProvider {

        // When no FastaHeaderProvider is registered (e.g. a header-less conversion), the molecule
        // type cannot be resolved, so neither validation method should throw.
        @BeforeEach
        void noProvider() {
            when(context.contains(FastaHeaderProvider.class)).thenReturn(false);
        }

        @Test
        void validateRequiredFeatureDoesNotThrow() {
            assertDoesNotThrow(() -> validation.validateRequiredFeature(annotation, LINE));
        }

        @Test
        void validateForbiddenFeatureDoesNotThrow() {
            assertDoesNotThrow(() -> validation.validateForbiddenFeature(annotation, LINE));
        }

        @Test
        void validateMrnaCdsComplementDoesNotThrow() {
            assertDoesNotThrow(() -> validation.validateMrnaCdsComplement(annotation, LINE));
        }

        @Test
        void validateMrnaCdsJoinedLocationDoesNotThrow() {
            assertDoesNotThrow(() -> validation.validateMrnaCdsJoinedLocation(annotation, LINE));
        }
    }

    private static String moleculeTypeName(String moleculeType) {
        return ControlledVocabularyUtils.MolType.fromValue(moleculeType)
                .orElseThrow()
                .name();
    }

    private static GFF3Feature feature(String name) {
        return feature(name, false);
    }

    private static GFF3Feature feature(String name, boolean complement) {
        GFF3Feature feature = mock(GFF3Feature.class);
        when(feature.getName()).thenReturn(name);
        when(feature.isComplement()).thenReturn(complement);
        return feature;
    }

    private static GFF3Feature cdsSegment(String id, long start, long end) {
        return cdsSegment(id, start, end, false);
    }

    private static GFF3Feature cdsSegment(String id, long start, long end, boolean ribosomalSlippage) {
        return segment("CDS", id, start, end, ribosomalSlippage);
    }

    /** One line of a feature's location: the segments of a joined feature share an id. */
    private static GFF3Feature segment(String name, String id, long start, long end, boolean ribosomalSlippage) {
        GFF3Feature feature = feature(name, false);
        when(feature.getId()).thenReturn(Optional.ofNullable(id));
        when(feature.getStart()).thenReturn(start);
        when(feature.getEnd()).thenReturn(end);
        when(feature.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE)).thenReturn(ribosomalSlippage);
        return feature;
    }

    private static FastaHeader headerWithMoleculeType(String moleculeType) {
        FastaHeader header = new FastaHeader();
        header.setMoleculeType(moleculeType);
        return header;
    }

    private static void injectContext(MoleculeTypeValidation validation, ValidationContext context) throws Exception {
        Field field = MoleculeTypeValidation.class.getDeclaredField("context");
        field.setAccessible(true);
        field.set(validation, context);
    }
}
