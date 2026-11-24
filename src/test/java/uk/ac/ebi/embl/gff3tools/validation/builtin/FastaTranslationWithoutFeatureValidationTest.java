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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.GFF3TranslationReaderProvider;

class FastaTranslationWithoutFeatureValidationTest {

    private static final String RULE = FastaTranslationWithoutFeatureValidation.RULE;
    private static final String ACCESSION = "seq1";
    private static final int LINE = 7;

    private FastaTranslationWithoutFeatureValidation validation;
    private GFF3TranslationReader reader;

    @BeforeEach
    void setUp() throws ReadException {
        validation = new FastaTranslationWithoutFeatureValidation();
        reader = mock(GFF3TranslationReader.class);
        when(reader.getTranslationOffsetsForAccession(any())).thenReturn(Map.of());
        ValidationContext context = new ValidationContext();
        context.register(GFF3TranslationReader.class, new GFF3TranslationReaderProvider(reader));
        TestUtils.injectContext(validation, context);
    }

    // ---tests for FASTA_TRANSLATION_WITHOUT_FEATURE validation

    @Test
    void everyRecordMatchesAFeature_noWarning() throws Exception {
        fastaHeaders("seq1|cds1", "seq1|cds2");

        assertDoesNotThrow(() -> validation.validateAnnotation(annotation(cds("cds1"), cds("cds2")), LINE));
    }

    @Test
    void recordForAnIdNotInTheAnnotation_isReported() throws Exception {
        fastaHeaders("seq1|cds1", "seq1|cds99");

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateAnnotation(annotation(cds("cds1")), LINE));

        assertEquals(RULE, exception.getValidationRule());
        assertEquals(LINE, exception.getLine());
        assertTrue(
                exception.getMessage().contains("1 ##FASTA record(s) for \"seq1\" match no feature"),
                exception.getMessage());
        assertTrue(exception.getMessage().contains("seq1|cds99"), exception.getMessage());
        assertFalse(exception.getMessage().contains("seq1|cds1,"), exception.getMessage());
    }

    @Test
    void sequenceRegionWithoutFeatures_reportsAllItsRecords() throws Exception {
        fastaHeaders("seq1|cds1", "seq1|cds2");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation(), LINE));

        assertTrue(exception.getMessage().contains("2 ##FASTA record(s)"), exception.getMessage());
    }

    @Test
    void onlyThisAnnotationsAccessionIsLookedUp() throws Exception {
        fastaHeaders("seq1|cds1");

        validation.validateAnnotation(annotation(cds("cds1")), LINE);

        verify(reader).getTranslationOffsetsForAccession(ACCESSION);
        verify(reader, never()).getTranslationOffsets();
    }

    @Test
    void proteinsAreNeverRead() throws Exception {
        fastaHeaders("seq1|cds1", "seq1|cds99");

        assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation(cds("cds1")), LINE));

        verify(reader, never()).readTranslation(anyLong());
        verify(reader, never()).getTranslation(any(), any());
    }

    @Test
    void anyFeatureTypeClaimsItsRecord() throws Exception {
        fastaHeaders("seq1|gene1");
        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", OntologyTerm.GENE.name(), ACCESSION, 1, 18, "+");

        assertDoesNotThrow(() -> validation.validateAnnotation(annotation(gene), LINE));
    }

    @Test
    void featureWithoutId_claimsNothing() throws Exception {
        fastaHeaders("seq1|cds1");

        assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation(cds(null)), LINE));
    }

    @Test
    void idWithReservedCharacter_matchesItsEncodedHeader() throws Exception {
        fastaHeaders("seq1|cds%3B1");

        assertDoesNotThrow(() -> validation.validateAnnotation(annotation(cds("cds;1")), LINE));
    }

    @Test
    void manyUnmatchedRecords_namesTheFirstTenAndCountsTheRest() throws Exception {
        String[] headers = new String[12];
        for (int i = 0; i < headers.length; i++) {
            headers[i] = "seq1|cds%02d".formatted(i);
        }
        fastaHeaders(headers);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation(), LINE));

        assertTrue(exception.getMessage().contains("12 ##FASTA record(s)"), exception.getMessage());
        assertTrue(exception.getMessage().contains("seq1|cds09"), exception.getMessage());
        assertFalse(exception.getMessage().contains("seq1|cds10"), exception.getMessage());
        assertTrue(exception.getMessage().endsWith(" and 2 more"), exception.getMessage());
    }

    @Test
    void noFastaRecordsForThisAccession_noWarning() {
        assertDoesNotThrow(() -> validation.validateAnnotation(annotation(cds("cds1")), LINE));
    }

    @Test
    void noReaderInTheContext_noWarning() {
        FastaTranslationWithoutFeatureValidation noReader = new FastaTranslationWithoutFeatureValidation();
        TestUtils.injectContext(noReader, new ValidationContext());

        assertDoesNotThrow(() -> noReader.validateAnnotation(annotation(cds("cds1")), LINE));
    }

    @Test
    void unreadableFasta_noWarning() throws Exception {
        when(reader.getTranslationOffsetsForAccession(ACCESSION)).thenThrow(new ReadException("bad ##FASTA"));

        assertDoesNotThrow(() -> validation.validateAnnotation(annotation(cds("cds1")), LINE));
    }

    private void fastaHeaders(String... headers) throws ReadException {
        Map<String, Long> offsets = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            offsets.put(headers[i], (long) i);
        }
        when(reader.getTranslationOffsetsForAccession(ACCESSION)).thenReturn(offsets);
    }

    private static GFF3Annotation annotation(GFF3Feature... features) {
        return TestUtils.createGFF3Annotation(ACCESSION, 1, 100, features);
    }

    private static GFF3Feature cds(String id) {
        return TestUtils.createGFF3Feature(id, OntologyTerm.CDS.name(), ACCESSION, 1, 18, "+");
    }
}
