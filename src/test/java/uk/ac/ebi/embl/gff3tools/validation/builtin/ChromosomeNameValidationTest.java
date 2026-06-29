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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class ChromosomeNameValidationTest {

    private static final int LINE = 42;

    private ChromosomeNameValidation validation;
    private ValidationContext context;
    private FastaHeaderProvider fastaHeaderProvider;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() throws Exception {
        validation = new ChromosomeNameValidation();
        context = mock(ValidationContext.class);
        fastaHeaderProvider = mock(FastaHeaderProvider.class);
        annotation = mock(GFF3Annotation.class);

        when(context.contains(FastaHeaderProvider.class)).thenReturn(true);
        when(context.get(FastaHeaderProvider.class)).thenReturn(fastaHeaderProvider);

        injectContext(validation, context);
    }

    @Nested
    class NoFastaHeaderProvider {

        // When no FastaHeaderProvider is registered (e.g. a header-less conversion), there is no
        // chromosome_name to resolve, so none of the validation methods should throw.
        @BeforeEach
        void noProvider() {
            when(context.contains(FastaHeaderProvider.class)).thenReturn(false);
        }

        @Test
        void validateChromosomeNameUniqueDoesNotThrow() {
            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));
        }

        @Test
        void validateChromosomeOrLinkageGroupNameAssignedDoesNotThrow() {
            assertDoesNotThrow(() -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));
        }

        @Test
        void validatePlasmidChromosomeNameFormatDoesNotThrow() {
            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }
    }

    @Nested
    class ValidateUniqueChromosomeName {

        @Test
        void doesNothingWhenNoFastaHeaderExistsForAccession() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));
        }

        @Test
        void doesNothingWhenChromosomeNameIsMissing() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1")).thenReturn(Optional.of(headerWithChromosomeName(null)));

            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));

            when(annotation.getAccession()).thenReturn("CM000002.1");
            when(fastaHeaderProvider.getHeader("CM000002.1")).thenReturn(Optional.of(headerWithChromosomeName("  ")));

            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));
        }

        @Test
        void doesNothingWhenChromosomeNamesAreUnique() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1")).thenReturn(Optional.of(headerWithChromosomeName("I")));

            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));

            when(annotation.getAccession()).thenReturn("CM000002.1");
            when(fastaHeaderProvider.getHeader("CM000002.1")).thenReturn(Optional.of(headerWithChromosomeName("II")));

            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));
        }

        @Test
        void doesNothingWhenSameAccessionIsValidatedAgain() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1")).thenReturn(Optional.of(headerWithChromosomeName("I")));

            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));
            assertDoesNotThrow(() -> validation.validateChromosomeNameUnique(annotation, LINE));
        }

        @Test
        void throwsValidationExceptionWhenChromosomeNameIsDuplicated() throws ValidationException {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1")).thenReturn(Optional.of(headerWithChromosomeName("I")));
            validation.validateChromosomeNameUnique(annotation, LINE);

            when(annotation.getAccession()).thenReturn("CM000002.1");
            when(fastaHeaderProvider.getHeader("CM000002.1")).thenReturn(Optional.of(headerWithChromosomeName(" I ")));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validateChromosomeNameUnique(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(ChromosomeNameValidation.CHROMOSOME_NAME_UNIQUE_RULE));
            assertTrue(message.contains("Duplicate chromosome_name 'I'"));
            assertTrue(message.contains("CM000001.1"));
            assertTrue(message.contains("CM000002.1"));
        }
    }

    @Nested
    class ValidateChromosomeOrLinkageGroupNameAssigned {

        @Test
        void doesNothingWhenNoFastaHeaderExistsForAccession() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));
        }

        @Test
        void doesNothingWhenChromosomeTypeIsMissingOrNotRestricted() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName(null, "unknown")));

            assertDoesNotThrow(() -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));

            when(annotation.getAccession()).thenReturn("CM000002.1");
            when(fastaHeaderProvider.getHeader("CM000002.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", "unknown")));

            assertDoesNotThrow(() -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));
        }

        @Test
        void doesNothingWhenRestrictedChromosomeTypeHasAssignedName() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("chromosome", "I")));

            assertDoesNotThrow(() -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));

            when(annotation.getAccession()).thenReturn("CM000002.1");
            when(fastaHeaderProvider.getHeader("CM000002.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("linkage_group", "LG1")));

            assertDoesNotThrow(() -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));
        }

        @Test
        void throwsValidationExceptionWhenChromosomeNameIsUnknownForChromosome() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("chromosome", "Unknown")));

            ValidationException exception = assertThrows(
                    ValidationException.class,
                    () -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(ChromosomeNameValidation.CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED_RULE));
            assertTrue(message.contains("chromosome_name 'Unknown' is not permitted"));
            assertTrue(message.contains("chromosome_type 'chromosome'"));
        }

        @Test
        void throwsValidationExceptionWhenChromosomeNameContainsUnassignedWordForLinkageGroup() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("linkage_group", "LG Unk")));

            ValidationException exception = assertThrows(
                    ValidationException.class,
                    () -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(ChromosomeNameValidation.CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED_RULE));
            assertTrue(message.contains("chromosome_name 'LG Unk' is not permitted"));
            assertTrue(message.contains("chromosome_type 'linkage_group'"));
        }

        @Test
        void throwsValidationExceptionWhenChromosomeNameIsZeroForRestrictedChromosomeType() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("chromosome", "0")));

            ValidationException exception = assertThrows(
                    ValidationException.class,
                    () -> validation.validateChromosomeOrLinkageGroupNameAssigned(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(ChromosomeNameValidation.CHROMOSOME_OR_LINKAGE_GROUP_NAME_NOT_UNASSIGNED_RULE));
            assertTrue(message.contains("chromosome_name '0' is not permitted"));
        }
    }

    @Nested
    class ValidatePlasmidChromosomeNameFormat {

        @Test
        void doesNothingWhenNoFastaHeaderExistsForAccession() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @Test
        void doesNothingWhenChromosomeTypeIsMissingOrNotPlasmid() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName(null, "Plasmid1")));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));

            when(annotation.getAccession()).thenReturn("CM000002.1");
            when(fastaHeaderProvider.getHeader("CM000002.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("chromosome", "Plasmid1")));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @Test
        void doesNothingWhenPlasmidNameStartsWithLowercaseP() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", "pABC1")));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @Test
        void doesNothingWhenPlasmidNameIsMegaplasmidName() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", "pMegaplasmid1")));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @Test
        void doesNothingWhenPlasmidNameIsAllowedUnnamedName() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", "unnamed")));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));

            when(annotation.getAccession()).thenReturn("CM000002.1");
            when(fastaHeaderProvider.getHeader("CM000002.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", "unnamed2")));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @Test
        void doesNothingWhenPlasmidNameIsAllowedHistoricalName() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", "F1")));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "F", // F factor without digits
                    "R1",
                    "R100",
                    "R1822", // R factors
                    "RP1",
                    "RP4",
                    "RK2",
                    "R68", // RP / RK factors
                    "ColE1",
                    "ColIb",
                    "ColIb-P9",
                    "ColV", // Col factors
                    "Ti",
                    "Ri", // Ti / Ri plasmids
                    "Megaplasmid" // megaplasmid
                })
        void doesNothingWhenPlasmidNameIsAdditionalHistoricalName(String chromosomeName) {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", chromosomeName)));

            assertDoesNotThrow(() -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "R", // R factor needs digits
                    "Col", // Col factor needs a suffix
                    "FX", // F factor only allows digits
                    "Tx" // not a Ti / Ri plasmid
                })
        void throwsValidationExceptionWhenPlasmidNameResemblesButIsNotHistoricalName(String chromosomeName) {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", chromosomeName)));

            assertThrows(
                    ValidationException.class, () -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));
        }

        @Test
        void throwsValidationExceptionWhenPlasmidNameDoesNotStartWithLowercaseP() {
            when(annotation.getAccession()).thenReturn("CM000001.1");
            when(fastaHeaderProvider.getHeader("CM000001.1"))
                    .thenReturn(Optional.of(headerWithChromosomeTypeAndName("plasmid", "P34")));

            ValidationException exception = assertThrows(
                    ValidationException.class, () -> validation.validatePlasmidChromosomeNameFormat(annotation, LINE));

            String message = exception.getMessage();
            assertTrue(message.contains(ChromosomeNameValidation.PLASMID_CHROMOSOME_NAME_FORMAT_RULE));
            assertTrue(message.contains("chromosome_name 'P34' is not a permitted plasmid name"));
        }
    }

    private static FastaHeader headerWithChromosomeName(String chromosomeName) {
        FastaHeader header = new FastaHeader();
        header.setChromosomeName(chromosomeName);
        return header;
    }

    private static FastaHeader headerWithChromosomeTypeAndName(String chromosomeType, String chromosomeName) {
        FastaHeader header = headerWithChromosomeName(chromosomeName);
        header.setChromosomeType(chromosomeType);
        return header;
    }

    private static void injectContext(ChromosomeNameValidation validation, ValidationContext context) throws Exception {
        Field field = ChromosomeNameValidation.class.getDeclaredField("context");
        field.setAccessible(true);
        field.set(validation, context);
    }
}
