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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

class TranslationAttributeFastaMismatchValidationTest {

    private static final String RULE = "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH";
    private static final String ACCESSION = "seq1";
    private static final String CDS_ID = "cds1";
    private static final String KEY = TranslationState.buildKey(ACCESSION, CDS_ID);
    private static final int LINE = 4;

    private TranslationAttributeFastaMismatchValidation validation;
    private TranslationState state;

    @BeforeEach
    void setUp() {
        validation = new TranslationAttributeFastaMismatchValidation();
        ValidationContext context = new ValidationContext();
        context.register(TranslationState.class, new TranslationStateProvider());
        state = context.get(TranslationState.class);
        TestUtils.injectContext(validation, context);
    }

    // ---tests for TRANSLATION_ATTRIBUTE_FASTA_MISMATCH validation

    @Test
    void attributeDifferentFromFasta_warns() {
        fastaRecorded("MKFGP");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateFeature(cds(CDS_ID, "MKWWW"), LINE));

        assertEquals(RULE, exception.getValidationRule());
        assertEquals(LINE, exception.getLine());
        assertTrue(exception.getMessage().contains("\"" + CDS_ID + "\""));
        assertTrue(exception.getMessage().contains("\"" + ACCESSION + "\""));
        assertTrue(exception.getMessage().contains("the ##FASTA translation is used"));
    }

    @Test
    void attributeEqualToFasta_noWarning() {
        fastaRecorded("MKFGP");

        assertDoesNotThrow(() -> validation.validateFeature(cds(CDS_ID, "MKFGP"), LINE));
    }

    @Test
    void comparisonIsExact_lowerCaseAttributeWarns() {
        fastaRecorded("MKFGP");

        assertThrows(ValidationException.class, () -> validation.validateFeature(cds(CDS_ID, "mkfgp"), LINE));
    }

    @Test
    void noFastaRecorded_noWarning() {
        assertDoesNotThrow(() -> validation.validateFeature(cds(CDS_ID, "MKWWW"), LINE));
    }

    @Test
    void noAttribute_noWarning() {
        fastaRecorded("MKFGP");

        assertDoesNotThrow(() -> validation.validateFeature(cds(CDS_ID, null), LINE));
    }

    @Test
    void featureWithoutId_noWarning() {
        fastaRecorded("MKFGP");

        assertDoesNotThrow(() -> validation.validateFeature(cds(null, "MKWWW"), LINE));
    }

    @Test
    void stateIsNeverChanged() throws Exception {
        fastaRecorded("MKFGP");

        assertThrows(ValidationException.class, () -> validation.validateFeature(cds(CDS_ID, "MKWWW"), LINE));
        validation.validateFeature(
                TestUtils.createGFF3Feature("cds2", OntologyTerm.CDS.name(), ACCESSION, 1, 18, "+"), LINE);

        assertEquals(new TranslationState.TranslationEntry("MKFGP", null), state.get(KEY));
        assertNull(state.get(TranslationState.buildKey(ACCESSION, "cds2")));
    }

    @Test
    void noTranslationStateInContext_noWarning() {
        TranslationAttributeFastaMismatchValidation noState = new TranslationAttributeFastaMismatchValidation();
        TestUtils.injectContext(noState, new ValidationContext());

        assertDoesNotThrow(() -> noState.validateFeature(cds(CDS_ID, "MKWWW"), LINE));
    }

    /** As {@code GFF3FileReader} records it before the feature is validated. */
    private void fastaRecorded(String translation) {
        state.record(KEY, translation, null);
    }

    private static GFF3Feature cds(String id, String translationAttribute) {
        GFF3Feature feature = TestUtils.createGFF3Feature(id, OntologyTerm.CDS.name(), ACCESSION, 1, 18, "+");
        if (translationAttribute != null) {
            feature.setAttributeList(GFF3Attributes.TRANSLATION, List.of(translationAttribute));
        }
        return feature;
    }
}
