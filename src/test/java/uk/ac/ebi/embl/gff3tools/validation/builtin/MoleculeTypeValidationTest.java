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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class MoleculeTypeValidationTest {

    private static final int LINE = 42;
    private static final String ACCESSION = "CM000001.1";

    private MoleculeTypeValidation validation;
    private FastaHeaderProvider fastaHeaderProvider;
    private OntologyClient ontologyClient;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() throws Exception {
        validation = new MoleculeTypeValidation();
        ValidationContext context = mock(ValidationContext.class);
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
