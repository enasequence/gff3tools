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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class FastaHeaderMappingValidationTest {

    private static final int LINE = 42;
    private static final String ACCESSION = "CM000001.1";

    private FastaHeaderMappingValidation validation;
    private ValidationContext context;
    private FastaHeaderProvider fastaHeaderProvider;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() throws Exception {
        validation = new FastaHeaderMappingValidation();
        context = mock(ValidationContext.class);
        fastaHeaderProvider = mock(FastaHeaderProvider.class);
        annotation = mock(GFF3Annotation.class);

        when(context.contains(FastaHeaderProvider.class)).thenReturn(true);
        when(context.get(FastaHeaderProvider.class)).thenReturn(fastaHeaderProvider);
        when(annotation.getAccession()).thenReturn(ACCESSION);

        injectContext(validation, context);
    }

    @Test
    void doesNotThrowWhenNoFastaHeaderProviderRegistered() {
        // No FastaHeaderProvider on the context (e.g. a header-less conversion) -> nothing to map.
        when(context.contains(FastaHeaderProvider.class)).thenReturn(false);

        assertDoesNotThrow(() -> validation.validateFastaHeaderMapping(annotation, LINE));
    }

    @Test
    void doesNotThrowWhenHeaderResolvesForAccession() {
        when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.of(new FastaHeader()));

        assertDoesNotThrow(() -> validation.validateFastaHeaderMapping(annotation, LINE));
    }

    @Test
    void throwsWhenProviderHasNoHeaderForAccession() {
        when(fastaHeaderProvider.getHeader(ACCESSION)).thenReturn(Optional.empty());

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateFastaHeaderMapping(annotation, LINE));

        String message = exception.getMessage();
        assertTrue(message.contains("FASTA_HEADER_MAPPING"));
        assertTrue(message.contains(ACCESSION));
        assertEquals(LINE, exception.getLine());
    }

    private static void injectContext(FastaHeaderMappingValidation validation, ValidationContext context)
            throws Exception {
        Field field = FastaHeaderMappingValidation.class.getDeclaredField("context");
        field.setAccessible(true);
        field.set(validation, context);
    }
}
