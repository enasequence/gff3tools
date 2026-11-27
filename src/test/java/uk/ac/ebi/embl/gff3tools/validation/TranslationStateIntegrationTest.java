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
package uk.ac.ebi.embl.gff3tools.validation;

import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.ebi.embl.gff3tools.TestUtils.reportsOf;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * The translation chain through the full engine, as the CLI builds it: {@code GFF3FileReader}
 * records the {@code ##FASTA} protein as old, {@code TRANSLATION_ATTRIBUTE_FASTA_MISMATCH} (HIGH)
 * compares the attribute with it, {@code REMOVE_TRANSLATION_ATTRIBUTE} (NORMAL) records the
 * attribute when nothing is recorded and removes it, the {@code TRANSLATION} fix (LOW) records the
 * generated one and {@code TRANSLATION_COMPARISON} (LOW) compares the two.
 *
 * <p>{@link #run} records the {@code ##FASTA} protein as the reader does; the sequences are in
 * memory, and every rule, fix and provider comes from the classpath. Other rules may report on the
 * bare test CDS, so assertions count only the reports of the translation rules.
 */
class TranslationStateIntegrationTest {

    private static final String MISMATCH = "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH";
    private static final String COMPARISON = "TRANSLATION_COMPARISON";
    private static final String ACCESSION = "seq1";
    private static final String CDS_ID = "cds1";
    private static final String KEY = TranslationState.buildKey(ACCESSION, CDS_ID);

    // ATG AAA TTT GGG CCC TAA (1-18) translates to MKFGP.
    private static final String SEQUENCE = "ATGAAATTTGGGCCCTAAGGGGGGGGGGGG";
    // The same ORF split by CCC (10-12): ATGAAATTT (1-9) + GGGCCCTAA (13-21).
    private static final String JOINED_SEQUENCE = "ATGAAATTTCCCGGGCCCTAAGGGGGGGGG";
    private static final String RIGHT = "MKFGP";
    private static final String WRONG = "MKWWW";

    /** The submitted ##FASTA protein of cds1, or null for none. */
    private String fasta;

    // ---tests for the submitted translation against the generated one

    @Test
    void fastaRight_recordsBothAndPasses() throws Exception {
        fastaHas(RIGHT);

        ValidationEngine engine = run(engineBuilder(SEQUENCE), cds());

        assertEntry(engine, RIGHT, RIGHT);
        assertReports(engine, 0, 0, 0);
    }

    @Test
    void fastaWrong_failsComparison() throws Exception {
        fastaHas(WRONG);

        ValidationEngine engine = run(engineBuilder(SEQUENCE), cds());

        assertEntry(engine, WRONG, RIGHT);
        assertReports(engine, 0, 0, 1);
    }

    @Test
    void attributeOnlyWrong_failsComparisonAndAttributeIsRemoved() throws Exception {
        GFF3Feature feature = cds(WRONG);

        ValidationEngine engine = run(engineBuilder(SEQUENCE), feature);

        assertEntry(engine, WRONG, RIGHT);
        assertReports(engine, 0, 0, 1);
        assertFalse(feature.hasAttribute(GFF3Attributes.TRANSLATION));
    }

    @Test
    void attributeWrongFastaRight_warnsAndFastaWins() throws Exception {
        fastaHas(RIGHT);
        GFF3Feature feature = cds(WRONG);

        ValidationEngine engine = run(engineBuilder(SEQUENCE), feature);

        assertEntry(engine, RIGHT, RIGHT);
        assertReports(engine, 1, 0, 0);
        assertFalse(feature.hasAttribute(GFF3Attributes.TRANSLATION));
    }

    @Test
    void attributeRightFastaWrong_warnsAndFailsComparison() throws Exception {
        fastaHas(WRONG);

        ValidationEngine engine = run(engineBuilder(SEQUENCE), cds(RIGHT));

        assertEntry(engine, WRONG, RIGHT);
        assertReports(engine, 1, 0, 1);
    }

    // ---tests for CDS features the Translator treats differently

    @Test
    void exceptionCds_keepsTheSubmittedTranslationAsNew() throws Exception {
        fastaHas(WRONG);
        GFF3Feature feature = cds();
        feature.addAttribute(GFF3Attributes.EXCEPTION, "ribosomal slippage");

        ValidationEngine engine = run(engineBuilder(SEQUENCE), feature);

        assertEntry(engine, WRONG, WRONG);
        assertReports(engine, 0, 0, 0);
    }

    @Test
    void joinedCds_isTranslatedAcrossSegments() throws Exception {
        fastaHas(RIGHT);
        GFF3Feature first = cds(1, 9);
        GFF3Feature second = cds(13, 21);

        ValidationEngine engine = run(engineBuilder(JOINED_SEQUENCE), first, second);

        assertEntry(engine, RIGHT, RIGHT);
        assertReports(engine, 0, 0, 0);
    }

    @Test
    void joinedCds_wrongFasta_failsComparisonOnce() throws Exception {
        fastaHas(WRONG);

        ValidationEngine engine = run(engineBuilder(JOINED_SEQUENCE), cds(1, 9), cds(13, 21));

        assertEntry(engine, WRONG, RIGHT);
        assertReports(engine, 0, 0, 1);
    }

    @Test
    void pseudoCdsWithFasta_noNewTranslation_failsComparison() throws Exception {
        // Today's behaviour, pending open question Q4 in the TTENA-339 design.
        fastaHas(RIGHT);
        GFF3Feature feature = cds();
        feature.addAttribute(GFF3Attributes.PSEUDO, "true");

        ValidationEngine engine = run(engineBuilder(SEQUENCE), feature);

        assertEntry(engine, RIGHT, null);
        assertReports(engine, 0, 0, 1);
        assertTrue(reportsOf(engine.getCollectedErrors(), COMPARISON)
                .get(0)
                .getMessage()
                .contains("No new translation"));
    }

    // ---tests for runs without sequences

    @Test
    void noSequences_wrongFastaIsRecordedButNotCompared() throws Exception {
        fastaHas(WRONG);

        ValidationEngine engine = run(engineBuilderWithoutSequences(), cds());

        assertEntry(engine, WRONG, null);
        assertReports(engine, 0, 0, 0);
    }

    // ---tests for rule and fix settings

    @Test
    void mismatchRuleOff_fastaIsStillCompared() throws Exception {
        fastaHas(WRONG);

        ValidationEngine engine =
                run(engineBuilder(SEQUENCE).overrideMethodRules(Map.of(MISMATCH, RuleSeverity.OFF)), cds());

        assertEntry(engine, WRONG, RIGHT);
        assertReports(engine, 0, 0, 1);
    }

    @Test
    void mismatchRuleOff_fastaStillWinsWithoutWarning() throws Exception {
        fastaHas(RIGHT);
        GFF3Feature feature = cds(WRONG);

        ValidationEngine engine =
                run(engineBuilder(SEQUENCE).overrideMethodRules(Map.of(MISMATCH, RuleSeverity.OFF)), feature);

        assertEntry(engine, RIGHT, RIGHT);
        assertReports(engine, 0, 0, 0);
        assertFalse(feature.hasAttribute(GFF3Attributes.TRANSLATION));
    }

    @Test
    void mismatchRuleError_reportsTheMismatchAsAnError() throws Exception {
        fastaHas(RIGHT);

        ValidationEngine engine =
                run(engineBuilder(SEQUENCE).overrideMethodRules(Map.of(MISMATCH, RuleSeverity.ERROR)), cds(WRONG));

        assertEntry(engine, RIGHT, RIGHT);
        assertReports(engine, 0, 1, 0);
    }

    @Test
    void removeAttributeFixOff_fastaIsStillRecordedAndAttributeStays() throws Exception {
        fastaHas(WRONG);
        GFF3Feature feature = cds(RIGHT);

        ValidationEngine engine =
                run(engineBuilder(SEQUENCE).overrideMethodFixs(Map.of("REMOVE_TRANSLATION_ATTRIBUTE", false)), feature);

        assertEntry(engine, WRONG, RIGHT);
        assertReports(engine, 1, 0, 1);
        assertTrue(feature.hasAttribute(GFF3Attributes.TRANSLATION));
    }

    @Test
    void translationFixOff_withSequences_noNewTranslation_failsComparison() throws Exception {
        fastaHas(RIGHT);

        ValidationEngine engine = run(engineBuilder(SEQUENCE).overrideMethodFixs(Map.of("TRANSLATION", false)), cds());

        assertEntry(engine, RIGHT, null);
        assertReports(engine, 0, 0, 1);
    }

    private void fastaHas(String translation) {
        fasta = translation;
    }

    /** One CDS over the whole 18 bp ORF, without a translation attribute. */
    private static GFF3Feature cds() {
        return cds(1, 18);
    }

    /** One CDS over the whole 18 bp ORF, with the given translation attribute. */
    private static GFF3Feature cds(String translationAttribute) {
        GFF3Feature feature = cds();
        feature.setAttributeList(GFF3Attributes.TRANSLATION, List.of(translationAttribute));
        return feature;
    }

    /** One CDS segment; segments sharing {@link #CDS_ID} form a join. */
    private static GFF3Feature cds(long start, long end) {
        GFF3Feature feature = TestUtils.createGFF3Feature(CDS_ID, OntologyTerm.CDS.name(), ACCESSION, start, end, "+");
        feature.addAttribute(GFF3Attributes.TRANSL_TABLE, "11");
        return feature;
    }

    /**
     * Runs the engine as {@code GFF3FileReader} does: for each feature, records the {@code ##FASTA}
     * protein as old once per feature ID, then validates it; then validates the annotation.
     */
    private ValidationEngine run(ValidationEngineBuilder builder, GFF3Feature... features) throws ValidationException {
        ValidationEngine engine = builder.build();
        TranslationState state = engine.getContext().get(TranslationState.class);
        int line = 2;
        for (GFF3Feature feature : features) {
            if (fasta != null && state.get(KEY) == null) {
                state.record(KEY, fasta, null);
            }
            engine.validate(feature, line++);
        }
        GFF3Annotation annotation = TestUtils.createGFF3Annotation(ACCESSION, 1, 30, features);
        engine.validate(annotation, line);
        return engine;
    }

    private ValidationEngineBuilder engineBuilder(String sequence) {
        CompositeSequenceProvider sequences = new CompositeSequenceProvider();
        sequences.addSource(TestUtils.inMemorySequenceSource(Map.of(ACCESSION, sequence)));
        return engineBuilderWithoutSequences().withProvider(sequences);
    }

    private ValidationEngineBuilder engineBuilderWithoutSequences() {
        return new ValidationEngineBuilder().failFast(false);
    }

    private static void assertEntry(ValidationEngine engine, String oldTranslation, String newTranslation) {
        TranslationState.TranslationEntry entry =
                engine.getContext().get(TranslationState.class).get(KEY);
        assertNotNull(entry, "a TranslationState entry for " + KEY);
        assertEquals(oldTranslation, entry.oldTranslation(), "old translation");
        assertEquals(newTranslation, entry.newTranslation(), "new translation");
    }

    private static void assertReports(
            ValidationEngine engine, int mismatchWarnings, int mismatchErrors, int comparisonErrors) {
        assertEquals(
                mismatchWarnings,
                reportsOf(engine.getParsingWarnings(), MISMATCH).size(),
                MISMATCH + " warnings");
        assertEquals(
                mismatchErrors, reportsOf(engine.getCollectedErrors(), MISMATCH).size(), MISMATCH + " errors");
        assertEquals(
                comparisonErrors,
                reportsOf(engine.getCollectedErrors(), COMPARISON).size(),
                COMPARISON + " errors");
    }
}
