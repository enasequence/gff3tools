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
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.GFF3TranslationReaderProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

class TranslationAttributeFastaMismatchValidationTest {

    private static final String RULE = "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH";
    private static final String ACCESSION = "seq1";
    private static final String CDS_ID = "cds1";
    private static final String KEY = TranslationState.buildKey(ACCESSION, CDS_ID);
    private static final int LINE = 4;

    private TranslationAttributeFastaMismatchValidation validation;
    private ValidationContext context;
    private TranslationState state;
    private GFF3TranslationReader reader;

    @BeforeEach
    void setUp() throws ReadException {
        validation = new TranslationAttributeFastaMismatchValidation();
        context = new ValidationContext();
        context.register(TranslationState.class, new TranslationStateProvider());
        state = context.get(TranslationState.class);
        reader = mock(GFF3TranslationReader.class);
        when(reader.getTranslation(any(), any())).thenReturn(Optional.empty());
        context.register(GFF3TranslationReader.class, new GFF3TranslationReaderProvider(reader));
        TestUtils.injectContext(validation, context);
    }

    // ---tests for TRANSLATION_ATTRIBUTE_FASTA_MISMATCH validation

    @Test
    void fastaOnly_recordsFastaAsOldTranslation() throws Exception {
        fastaHas("MKFGP");

        validation.validateFeature(cds(CDS_ID, null), LINE);

        assertEquals("MKFGP", state.get(KEY).oldTranslation());
        assertNull(state.get(KEY).newTranslation());
    }

    @Test
    void attributeOnly_recordsAttributeAsOldTranslation() throws Exception {
        validation.validateFeature(cds(CDS_ID, "MKFGP"), LINE);

        assertEquals("MKFGP", state.get(KEY).oldTranslation());
    }

    @Test
    void attributeEqualToFasta_recordsFastaWithoutWarning() {
        fastaHas("MKFGP");

        assertDoesNotThrow(() -> validation.validateFeature(cds(CDS_ID, "MKFGP"), LINE));

        assertEquals("MKFGP", state.get(KEY).oldTranslation());
    }

    @Test
    void attributeDifferentFromFasta_warnsAndRecordsFasta() {
        fastaHas("MKFGP");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateFeature(cds(CDS_ID, "MKWWW"), LINE));

        assertEquals(RULE, exception.getValidationRule());
        assertEquals(LINE, exception.getLine());
        assertTrue(exception.getMessage().contains("\"" + CDS_ID + "\""));
        assertTrue(exception.getMessage().contains("\"" + ACCESSION + "\""));
        assertTrue(exception.getMessage().contains("the ##FASTA translation is used"));
        // Recorded before the exception, so the comparison sees it whatever the severity.
        assertEquals("MKFGP", state.get(KEY).oldTranslation());
    }

    @Test
    void comparisonIsExact_lowerCaseAttributeWarns() {
        fastaHas("MKFGP");

        assertThrows(ValidationException.class, () -> validation.validateFeature(cds(CDS_ID, "mkfgp"), LINE));
    }

    @Test
    void neitherAttributeNorFasta_recordsNothing() throws Exception {
        validation.validateFeature(cds(CDS_ID, null), LINE);

        assertNull(state.get(KEY));
    }

    @Test
    void featureWithoutId_recordsNothingAndDoesNotReadFasta() throws Exception {
        validation.validateFeature(cds(null, "MKFGP"), LINE);

        verify(reader, never()).getTranslation(any(), any());
        state.forEach((key, entry) -> fail("Nothing should be recorded, found " + key));
    }

    @Test
    void noReaderInContext_recordsAttribute() throws Exception {
        TranslationAttributeFastaMismatchValidation noReaderValidation =
                new TranslationAttributeFastaMismatchValidation();
        ValidationContext noReaderContext = new ValidationContext();
        noReaderContext.register(TranslationState.class, new TranslationStateProvider());
        TestUtils.injectContext(noReaderValidation, noReaderContext);

        noReaderValidation.validateFeature(cds(CDS_ID, "MKFGP"), LINE);

        assertEquals(
                "MKFGP", noReaderContext.get(TranslationState.class).get(KEY).oldTranslation());
    }

    @Test
    void noTranslationStateInContext_stillWarns() throws Exception {
        TranslationAttributeFastaMismatchValidation noStateValidation =
                new TranslationAttributeFastaMismatchValidation();
        ValidationContext noStateContext = new ValidationContext();
        noStateContext.register(GFF3TranslationReader.class, new GFF3TranslationReaderProvider(reader));
        TestUtils.injectContext(noStateValidation, noStateContext);
        fastaHas("MKFGP");

        assertThrows(ValidationException.class, () -> noStateValidation.validateFeature(cds(CDS_ID, "MKWWW"), LINE));
    }

    @Test
    void unreadableFasta_recordsAttributeWithoutWarning() throws Exception {
        when(reader.getTranslation(ACCESSION, CDS_ID)).thenThrow(new ReadException("bad ##FASTA"));

        assertDoesNotThrow(() -> validation.validateFeature(cds(CDS_ID, "MKWWW"), LINE));

        assertEquals("MKWWW", state.get(KEY).oldTranslation());
    }

    // ---tests for TRANSLATION_ATTRIBUTE_FASTA_MISMATCH with REMOVE_TRANSLATION_ATTRIBUTE in one engine

    @Test
    void engine_ruleSeesAttributeBeforeFixRemovesIt() throws Exception {
        ValidationEngine engine = engineBuilder().build();
        fastaHas("MKFGP");
        GFF3Feature feature = cds(CDS_ID, "MKWWW");

        engine.validate(feature, LINE);

        assertEquals(1, ruleCount(engine.getParsingWarnings()));
        assertEquals(0, ruleCount(engine.getCollectedErrors()));
        assertFalse(feature.hasAttribute(GFF3Attributes.TRANSLATION));
        // The fix leaves the rule's ##FASTA translation alone.
        assertEquals("MKFGP", stateOf(engine).get(KEY).oldTranslation());
    }

    @Test
    void engine_ruleSetToError_reportsMismatchAsError() throws Exception {
        ValidationEngine engine = engineBuilder()
                .overrideMethodRules(Map.of(RULE, RuleSeverity.ERROR))
                .build();
        fastaHas("MKFGP");

        engine.validate(cds(CDS_ID, "MKWWW"), LINE);

        assertEquals(1, ruleCount(engine.getCollectedErrors()));
        assertEquals(0, ruleCount(engine.getParsingWarnings()));
        assertEquals("MKFGP", stateOf(engine).get(KEY).oldTranslation());
    }

    @Test
    void engine_ruleOff_fixRecordsAttributeAsOnMain() throws Exception {
        ValidationEngine engine = engineBuilder()
                .overrideMethodRules(Map.of(RULE, RuleSeverity.OFF))
                .build();
        fastaHas("MKFGP");
        GFF3Feature feature = cds(CDS_ID, "MKWWW");

        engine.validate(feature, LINE);

        assertEquals(0, ruleCount(engine.getParsingWarnings()));
        assertFalse(feature.hasAttribute(GFF3Attributes.TRANSLATION));
        assertEquals("MKWWW", stateOf(engine).get(KEY).oldTranslation());
    }

    @Test
    void engine_fixOff_ruleStillRecordsAndWarns() throws Exception {
        ValidationEngine engine = engineBuilder()
                .overrideMethodFixs(Map.of("REMOVE_TRANSLATION_ATTRIBUTE", false))
                .build();
        fastaHas("MKFGP");
        GFF3Feature feature = cds(CDS_ID, "MKWWW");

        engine.validate(feature, LINE);

        assertEquals(1, ruleCount(engine.getParsingWarnings()));
        assertTrue(feature.hasAttribute(GFF3Attributes.TRANSLATION));
        assertEquals("MKFGP", stateOf(engine).get(KEY).oldTranslation());
    }

    private void fastaHas(String translation) {
        try {
            when(reader.getTranslation(ACCESSION, CDS_ID)).thenReturn(Optional.of(translation));
        } catch (ReadException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GFF3Feature cds(String id, String translationAttribute) {
        GFF3Feature feature = TestUtils.createGFF3Feature(id, OntologyTerm.CDS.name(), ACCESSION, 1, 18, "+");
        if (translationAttribute != null) {
            feature.setAttributeList(GFF3Attributes.TRANSLATION, List.of(translationAttribute));
        }
        return feature;
    }

    /**
     * The engine as the CLI builds it: every rule, fix and provider found on the classpath, with only
     * the ##FASTA reader replaced by the mock. Other rules may report on the bare test CDS, so the
     * engine tests count only this rule's reports.
     */
    private ValidationEngineBuilder engineBuilder() {
        return new ValidationEngineBuilder()
                .withProvider(new GFF3TranslationReaderProvider(reader))
                .failFast(false);
    }

    private static long ruleCount(List<ValidationException> reports) {
        return reports.stream()
                .filter(report -> RULE.equals(report.getValidationRule()))
                .count();
    }

    private static TranslationState stateOf(ValidationEngine engine) {
        return engine.getContext().get(TranslationState.class);
    }
}
