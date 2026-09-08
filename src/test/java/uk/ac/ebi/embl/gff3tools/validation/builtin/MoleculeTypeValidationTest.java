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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

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
    class ValidateMoleculeTypeAgainstAnalysisType {

        @Test
        void doesNothingWhenNoAnalysisTypeIsRegistered() {
            withMoleculeType("mRNA");

            assertDoesNotThrow(() -> validation.validateMoleculeTypeAgainstAnalysisType(annotation, LINE));
        }

        @ParameterizedTest
        @EnumSource(
                value = AnalysisType.class,
                names = {"SEQUENCE_FLATFILE", "UNKNOWN"})
        void doesNothingWhenAnalysisTypeConstrainsNoMoleculeType(AnalysisType analysisType) {
            withAnalysisType(analysisType);
            withMoleculeType("mRNA");

            assertDoesNotThrow(() -> validation.validateMoleculeTypeAgainstAnalysisType(annotation, LINE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"genomic DNA", "genomic RNA", "viral cRNA", "other DNA", "rRNA"})
        void doesNothingWhenSequenceAssemblyMoleculeTypeIsPermitted(String moleculeType) {
            withAnalysisType(AnalysisType.SEQUENCE_ASSEMBLY);
            withMoleculeType(moleculeType);

            assertDoesNotThrow(() -> validation.validateMoleculeTypeAgainstAnalysisType(annotation, LINE));
        }

        @ParameterizedTest
        @ValueSource(strings = {"mRNA", "genomic RNA", "viral cRNA", "transcribed RNA", "unassigned RNA"})
        void doesNothingWhenTranscriptomeAssemblyMoleculeTypeIsPermitted(String moleculeType) {
            withAnalysisType(AnalysisType.TRANSCRIPTOME_ASSEMBLY);
            withMoleculeType(moleculeType);

            assertDoesNotThrow(() -> validation.validateMoleculeTypeAgainstAnalysisType(annotation, LINE));
        }

        @Test
        void doesNothingWhenMoleculeTypeIsNotDeclared() {
            withAnalysisType(AnalysisType.TRANSCRIPTOME_ASSEMBLY);
            when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(new FastaHeader()));

            assertDoesNotThrow(() -> validation.validateMoleculeTypeAgainstAnalysisType(annotation, LINE));
        }

        @Test
        void throwsValidationExceptionWhenSequenceAssemblyIsMrna() {
            assertRejected(AnalysisType.SEQUENCE_ASSEMBLY, "mRNA");
        }

        @ParameterizedTest
        @ValueSource(strings = {"genomic DNA", "other DNA", "unassigned DNA"})
        void throwsValidationExceptionWhenTranscriptomeAssemblyIsDna(String moleculeType) {
            assertRejected(AnalysisType.TRANSCRIPTOME_ASSEMBLY, moleculeType);
        }

        private void assertRejected(AnalysisType analysisType, String moleculeType) {
            withAnalysisType(analysisType);
            withMoleculeType(moleculeType);

            ValidationException exception = assertThrows(
                    ValidationException.class,
                    () -> validation.validateMoleculeTypeAgainstAnalysisType(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(MoleculeTypeValidation.ANALYSIS_TYPE_RULE));
            assertTrue(message.contains(
                    "Molecule type %s is not permitted for a %s submission.".formatted(moleculeType, analysisType)));
        }

        private void withAnalysisType(AnalysisType analysisType) {
            when(context.contains(AnalysisContext.class)).thenReturn(true);
            when(context.get(AnalysisContext.class)).thenReturn(new AnalysisContext(analysisType, 10));
        }

        private void withMoleculeType(String moleculeType) {
            when(fastaHeaderProvider.getHeader(ACCESSION))
                    .thenReturn(Optional.of(headerWithMoleculeType(moleculeType)));
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
        void validateMoleculeTypeAgainstAnalysisTypeDoesNotThrow() {
            when(context.contains(AnalysisContext.class)).thenReturn(true);
            when(context.get(AnalysisContext.class))
                    .thenReturn(new AnalysisContext(AnalysisType.TRANSCRIPTOME_ASSEMBLY, 10));

            assertDoesNotThrow(() -> validation.validateMoleculeTypeAgainstAnalysisType(annotation, LINE));
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
