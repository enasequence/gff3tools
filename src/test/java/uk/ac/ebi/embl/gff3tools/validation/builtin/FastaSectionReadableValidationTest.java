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
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.GFF3TranslationReaderProvider;

class FastaSectionReadableValidationTest {

    private static final String RULE = FastaSectionReadableValidation.RULE;
    private static final int LINE = 3;
    private static final String READ_ERROR =
            "Error reading translations from in.gff3: the FASTA header \">seq1|cds1\" appears more than once";

    private FastaSectionReadableValidation validation;
    private ValidationContext context;
    private GFF3TranslationReader reader;

    @BeforeEach
    void setUp() throws ReadException {
        validation = new FastaSectionReadableValidation();
        context = new ValidationContext();
        reader = mock(GFF3TranslationReader.class);
        when(reader.getTranslationOffsets()).thenReturn(Map.of("seq1|cds1", 0L));
        context.register(GFF3TranslationReader.class, new GFF3TranslationReaderProvider(reader));
        TestUtils.injectContext(validation, context);
    }

    // ---tests for FASTA_SECTION_UNREADABLE validation

    @Test
    void readableFasta_noError() {
        assertDoesNotThrow(() -> validation.validateAnnotation(annotation("seq1"), LINE));
    }

    @Test
    void noFastaSection_noError() throws Exception {
        when(reader.getTranslationOffsets()).thenReturn(Map.of());

        assertDoesNotThrow(() -> validation.validateAnnotation(annotation("seq1"), LINE));
    }

    @Test
    void unreadableFasta_isReportedWithTheReadersReason() throws Exception {
        when(reader.getTranslationOffsets()).thenThrow(new ReadException(READ_ERROR));

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation("seq1"), LINE));

        assertEquals(RULE, exception.getValidationRule());
        assertEquals(LINE, exception.getLine());
        assertTrue(exception.getMessage().contains(READ_ERROR), exception.getMessage());
    }

    @Test
    void unreadableFasta_isReportedOnlyOnTheFirstAnnotation() throws Exception {
        when(reader.getTranslationOffsets()).thenThrow(new ReadException(READ_ERROR));

        assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation("seq1"), LINE));
        assertDoesNotThrow(() -> validation.validateAnnotation(annotation("seq2"), LINE + 10));

        verify(reader, times(1)).getTranslationOffsets();
    }

    @Test
    void readableFasta_isCheckedOnlyOnce() throws Exception {
        validation.validateAnnotation(annotation("seq1"), LINE);
        validation.validateAnnotation(annotation("seq2"), LINE + 10);

        verify(reader, times(1)).getTranslationOffsets();
    }

    @Test
    void newReaderOnTheSameContext_isCheckedAgain() throws Exception {
        validation.validateAnnotation(annotation("seq1"), LINE);
        GFF3TranslationReader secondReader = mock(GFF3TranslationReader.class);
        when(secondReader.getTranslationOffsets()).thenThrow(new ReadException(READ_ERROR));
        context.register(GFF3TranslationReader.class, new GFF3TranslationReaderProvider(secondReader));

        assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation("seq1"), LINE));
    }

    @Test
    void proteinsAreNeverRead() throws Exception {
        validation.validateAnnotation(annotation("seq1"), LINE);

        verify(reader, never()).readTranslation(anyLong());
        verify(reader, never()).getTranslation(any(), any());
    }

    @Test
    void noReaderInTheContext_noError() {
        FastaSectionReadableValidation noReader = new FastaSectionReadableValidation();
        TestUtils.injectContext(noReader, new ValidationContext());

        assertDoesNotThrow(() -> noReader.validateAnnotation(annotation("seq1"), LINE));
    }

    private static GFF3Annotation annotation(String accession) {
        return TestUtils.createGFF3Annotation(accession, 1, 100);
    }
}
