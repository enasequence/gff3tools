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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

public class FastaHeaderFormatValidationTest {

    private static final int LINE = 42;
    private static final String ACCESSION = "CM000001.1";
    private static final List<String> VALID_MOL_TYPES = List.of(
            "genomic DNA",
            "genomic RNA",
            "mRNA",
            "tRNA",
            "rRNA",
            "other RNA",
            "other DNA",
            "transcribed RNA",
            "viral cRNA",
            "unassigned DNA",
            "unassigned RNA");

    private FastaHeaderFormatValidation validation;
    private ValidationContext context;
    private FastaHeaderProvider fastaHeaderProvider;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() throws Exception {
        validation = new FastaHeaderFormatValidation();

        context = mock(ValidationContext.class);
        fastaHeaderProvider = mock(FastaHeaderProvider.class);
        annotation = mock(GFF3Annotation.class);

        when(context.contains(FastaHeaderProvider.class)).thenReturn(true);
        when(context.get(FastaHeaderProvider.class)).thenReturn(fastaHeaderProvider);
        when(annotation.getAccession()).thenReturn(ACCESSION);

        injectContext(validation, context);
    }

    @Test
    void validateDoesNothingWhenNoFastaHeaderExistsForAccession() {
        when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> validation.validate(annotation, LINE));
    }

    @Test
    void validateDoesNotThrowWhenNoFastaHeaderProviderRegistered() {
        // No FastaHeaderProvider on the context (e.g. a header-less conversion) -> nothing to validate.
        when(context.contains(FastaHeaderProvider.class)).thenReturn(false);

        assertDoesNotThrow(() -> validation.validate(annotation, LINE));
    }

    @Test
    void validateDoesNothingWhenFastaHeaderIsValid() {
        FastaHeader header = validHeader();

        when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(header));

        assertDoesNotThrow(() -> validation.validate(annotation, LINE));
    }

    @Test
    void validateThrowsValidationExceptionWhenFastaHeaderIsInvalid() {
        FastaHeader header = mock(FastaHeader.class);

        when(header.getDescription()).thenReturn("");
        when(header.getMoleculeType()).thenReturn("genomic DNA");
        when(header.getTopology()).thenReturn("linear");

        when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(header));

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validate(annotation, LINE));

        String message = exception.getMessage();

        assertTrue(message.contains("FASTA_HEADER_SYNTAX_VALIDATION"));
        assertTrue(message.contains("Fasta header with id " + ACCESSION));
        assertTrue(message.contains("description is mandatory"));
    }

    @Test
    void validateIncludesAllSyntaxViolationsInThrownException() {
        FastaHeader header = mock(FastaHeader.class);

        when(header.getDescription()).thenReturn("");
        when(header.getMoleculeType()).thenReturn("");
        when(header.getTopology()).thenReturn("invalid-topology");

        when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(header));

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validate(annotation, LINE));

        String message = exception.getMessage();

        assertTrue(message.contains("Fasta header with id " + ACCESSION));
        assertTrue(message.contains("description is mandatory"));
        assertTrue(message.contains("molecule_type is mandatory"));
        assertTrue(message.contains("topology must be 'linear' or 'circular'"));
    }

    @Test
    void validateUsesAnnotationAccessionToResolveHeader() {
        String differentAccession = "CM999999.1";
        FastaHeader validHeader = validHeader();
        FastaHeader invalidHeader = mock(FastaHeader.class);
        when(annotation.getAccession()).thenReturn(ACCESSION);
        when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(validHeader));
        when(fastaHeaderProvider.getHeader(differentAccession)).thenReturn(Optional.of(invalidHeader));

        when(invalidHeader.getDescription()).thenReturn("");
        when(invalidHeader.getMoleculeType()).thenReturn("");
        when(invalidHeader.getTopology()).thenReturn("");

        assertDoesNotThrow(() -> validation.validate(annotation, LINE));
    }

    @Nested
    public class ValidateTest {
        @Test
        void shouldPassValidation_whenValidHeader() {
            FastaHeader h = new FastaHeader();
            h.setDescription("Some description");
            h.setMoleculeType("genomic DNA");
            h.setTopology("linear");
            h.setChromosomeType("chromosome");
            h.setChromosomeLocation("Mitochondrion");
            h.setChromosomeName("seq1_valid");

            FastaHeader h2 = new FastaHeader();
            h2.setDescription("Some description");
            h2.setMoleculeType("genomic DNA");
            h2.setTopology("circular");

            List<String> errors = FastaHeaderFormatValidation.validate(h);
            List<String> errors2 = FastaHeaderFormatValidation.validate(h2);

            assertTrue(errors.isEmpty());
            assertTrue(errors2.isEmpty());
        }

        @Test
        void shouldFailValidation_whenHeaderNullOrEmpty() {
            FastaHeader h = new FastaHeader();

            List<String> errors = FastaHeaderFormatValidation.validate(h);
            List<String> errors2 = FastaHeaderFormatValidation.validate(null);

            assertFalse(errors.isEmpty());
            assertFalse(errors2.isEmpty());
        }

        @Test
        void shouldFail_whenMandatoryFieldsMissing() {
            FastaHeader h = new FastaHeader();

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.contains("description is mandatory"));
            assertTrue(errors.contains("molecule_type is mandatory"));
            assertTrue(errors.contains("topology is mandatory"));
        }

        @Test
        void shouldFail_whenInvalidTopology() {
            FastaHeader h = validHeader();
            h.setTopology("triangle");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.contains("topology must be 'linear' or 'circular'"));
        }

        @Test
        void shouldPassValidation_whenMoleculeTypeIsAllowedMolTypeValue() {
            for (String molType : VALID_MOL_TYPES) {
                FastaHeader h = validHeader();
                h.setMoleculeType(molType);

                List<String> errors = FastaHeaderFormatValidation.validate(h);

                assertTrue(errors.isEmpty(), "Expected mol_type value to be valid: " + molType);
            }
        }

        @Test
        void shouldFail_whenInvalidMoleculeType() {
            FastaHeader h = validHeader();
            h.setMoleculeType("DNA");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.contains("molecule_type must be one of the allowed mol_type qualifier values"));
        }

        @Test
        void shouldFail_whenInvalidChromosomeType() {
            FastaHeader h = validHeader();
            // all three chromosome fields present -> valid combination, so only the type value is at fault
            h.setChromosomeName("seq1_valid");
            h.setChromosomeLocation("Mitochondrion");
            h.setChromosomeType("invalid_type");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.contains("invalid chromosome_type - see allowed values list"));
        }

        @Test
        void shouldPassValidation_whenChromosomeLocationIsNucleus() {
            // "Nucleus"/"Cytoplasm" are allowed values denoting the default location; per the team
            // decision chromosome_location is mandatory for a chromosome, so all three fields are set.
            FastaHeader h = validHeader();
            h.setChromosomeName("1");
            h.setChromosomeType("chromosome");
            h.setChromosomeLocation("Nucleus");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.isEmpty());
        }

        @Test
        void shouldPassValidation_whenChromosomeLocationIsCytoplasm() {
            FastaHeader h = validHeader();
            h.setChromosomeName("1");
            h.setChromosomeType("plasmid");
            h.setChromosomeLocation("Cytoplasm");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.isEmpty());
        }

        @Test
        void shouldFail_whenChromosomeOmitsLocation() {
            // chromosome_location is mandatory when a chromosome is described (name + type present),
            // so omitting it is an invalid combination even for the default location.
            FastaHeader h = validHeader();
            h.setChromosomeName("1");
            h.setChromosomeType("chromosome");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.stream().anyMatch(e -> e.contains("invalid combination of optional chromosome fields")));
        }

        @Test
        void shouldFail_whenInvalidChromosomeLocation() {
            FastaHeader h = validHeader();
            // all three chromosome fields present -> valid combination, so only the location is at fault
            h.setChromosomeName("seq1_valid");
            h.setChromosomeType("chromosome");
            h.setChromosomeLocation("mars");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.contains("invalid chromosome_location - see allowed values list"));
        }

        @Test
        void shouldFail_whenInvalidChromosomeNamePattern() {
            FastaHeader h = validHeader();
            h.setChromosomeName("-badName");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.contains("invalid chromosome_name format"));
        }

        @Test
        void shouldFail_whenChromosomeNameTooLong() {
            FastaHeader h = validHeader();
            h.setChromosomeName("a".repeat(33));

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.contains("chromosome_name must be shorter than 33 characters"));
        }

        @Test
        void shouldFail_whenChromosomeNameContainsForbiddenWord() {
            FastaHeader h = validHeader();
            h.setChromosomeName("myChromosome1");

            List<String> errors = FastaHeaderFormatValidation.validate(h);

            assertTrue(errors.stream().anyMatch(e -> e.contains("forbidden term")));
        }

        @Test
        void shouldFail_whenHeaderIsNull() {
            List<String> errors = FastaHeaderFormatValidation.validate(null);

            assertEquals(1, errors.size());
            assertEquals("FastaHeader must not be null", errors.get(0));
        }

        private FastaHeader validHeader() {
            FastaHeader h = new FastaHeader();
            h.setDescription("desc");
            h.setMoleculeType("genomic DNA");
            h.setTopology("linear");
            return h;
        }
    }

    /**
     * Covers the allowed combinations of the optional chromosome fields. Per the team decision,
     * chromosome_location is mandatory when a chromosome is described, so only three combinations are
     * valid: none present (unplaced contig), chromosome_name only (unlocalized), or all three present
     * (chromosome). Every other combination must be reported. Field values below are all individually
     * valid so the combination rule is the only thing under test.
     */
    @Nested
    public class ChromosomeFieldCombinationTest {

        private static final String COMBINATION_ERROR = "invalid combination of optional chromosome fields";

        private static final String NAME = "seq1_valid";
        private static final String TYPE = "chromosome";
        private static final String LOCATION = "Mitochondrion";

        // --- valid combinations ---

        @Test
        void shouldPass_whenNoChromosomeFieldsPresent() { // table row 1: contig (unplaced)
            assertFalse(combinationErrors(null, null, null).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        @Test
        void shouldPass_whenOnlyChromosomeNamePresent() { // table row 2.a: unlocalized
            assertFalse(combinationErrors(NAME, null, null).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        @Test
        void shouldPass_whenAllThreeChromosomeFieldsPresent() { // table row 4: chromosome
            assertFalse(combinationErrors(NAME, TYPE, LOCATION).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        // --- invalid combinations ---

        @Test
        void shouldFail_whenOnlyChromosomeTypePresent() { // table row 2.b
            assertTrue(combinationErrors(null, TYPE, null).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        @Test
        void shouldFail_whenOnlyChromosomeLocationPresent() { // table row 2.c
            assertTrue(combinationErrors(null, null, LOCATION).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        @Test
        void shouldFail_whenChromosomeTypeAndLocationPresentWithoutName() { // table row 3.a
            assertTrue(combinationErrors(null, TYPE, LOCATION).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        @Test
        void shouldFail_whenChromosomeNameAndLocationPresentWithoutType() { // table row 3.b
            assertTrue(combinationErrors(NAME, null, LOCATION).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        @Test
        void shouldFail_whenChromosomeNameAndTypePresentWithoutLocation() { // table row 3.c
            assertTrue(combinationErrors(NAME, TYPE, null).stream().anyMatch(e -> e.contains(COMBINATION_ERROR)));
        }

        private List<String> combinationErrors(String name, String type, String location) {
            FastaHeader h = new FastaHeader();
            h.setDescription("desc");
            h.setMoleculeType("genomic DNA");
            h.setTopology("linear");
            h.setChromosomeName(name);
            h.setChromosomeType(type);
            h.setChromosomeLocation(location);
            return FastaHeaderFormatValidation.validate(h);
        }
    }

    // --------------------------- helpers --------------------------------
    private static FastaHeader validHeader() {
        FastaHeader header = mock(FastaHeader.class);

        when(header.getDescription()).thenReturn("Complete genome");
        when(header.getMoleculeType()).thenReturn("genomic DNA");
        when(header.getTopology()).thenReturn("linear");
        when(header.getChromosomeType()).thenReturn(null);
        when(header.getChromosomeLocation()).thenReturn(null);
        when(header.getChromosomeName()).thenReturn(null);

        return header;
    }

    private static void injectContext(FastaHeaderFormatValidation validation, ValidationContext context)
            throws Exception {
        Field field = FastaHeaderFormatValidation.class.getDeclaredField("context");
        field.setAccessible(true);
        field.set(validation, context);
    }
}
