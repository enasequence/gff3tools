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

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

class CdsTranslationPresenceValidationTest {

    private CdsTranslationPresenceValidation validation;
    private ValidationContext context;
    private TranslationState state;

    @BeforeEach
    void setUp() {
        validation = new CdsTranslationPresenceValidation();
        context = new ValidationContext();
        context.register(TranslationState.class, new TranslationStateProvider());
        state = context.get(TranslationState.class);
        registerSequenceLookup(context, mock(SequenceLookup.class));
        ValidationRegistry.injectContext(validation, context);
    }

    @Test
    void noOpWhenTranslationNotRequested() throws Exception {
        // No SequenceLookup registered => translation was not requested.
        CdsTranslationPresenceValidation noLookup = new CdsTranslationPresenceValidation();
        ValidationContext ctx = new ValidationContext();
        ctx.register(TranslationState.class, new TranslationStateProvider());
        ValidationRegistry.injectContext(noLookup, ctx);

        GFF3Annotation annotation = createAnnotation(createCds("cds1", "seq1", 1, 9));
        // No translation recorded, but must not fire because translation was not requested.
        noLookup.validateTranslationPresence(annotation, 1);
    }

    @Test
    void passesWhenEveryCdsHasTranslation() throws Exception {
        state.record(TranslationState.buildKey("seq1", "cds1"), null, "MK");

        GFF3Annotation annotation = createAnnotation(createCds("cds1", "seq1", 1, 9));
        validation.validateTranslationPresence(annotation, 1);
    }

    @Test
    void throwsWhenCdsHasNoTranslation() {
        GFF3Annotation annotation = createAnnotation(createCds("cds1", "seq1", 1, 9));

        ValidationException ex =
                assertThrows(ValidationException.class, () -> validation.validateTranslationPresence(annotation, 1));
        assertTrue(ex.getMessage().contains("CDS_TRANSLATION_PRESENCE"));
        assertTrue(ex.getMessage().contains("seq1"));
        assertTrue(ex.getMessage().contains("1-9"));
    }

    @Test
    void throwsWhenTranslationRecordedButNewIsNull() {
        // e.g. an old translation was captured but no new translation was generated.
        state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);

        GFF3Annotation annotation = createAnnotation(createCds("cds1", "seq1", 1, 9));
        ValidationException ex =
                assertThrows(ValidationException.class, () -> validation.validateTranslationPresence(annotation, 1));
        assertTrue(ex.getMessage().contains("1-9"));
    }

    @Test
    void exemptsCdsWithExceptionAttribute() throws Exception {
        GFF3Feature cds = createCds("cds1", "seq1", 1, 9);
        cds.addAttribute(GFF3Attributes.EXCEPTION, "ribosomal slippage");
        GFF3Annotation annotation = createAnnotation(cds);

        // No translation recorded, but exception CDS is exempt.
        validation.validateTranslationPresence(annotation, 1);
    }

    @Test
    void exemptsPseudoCds() throws Exception {
        GFF3Feature cds = createCds("cds1", "seq1", 1, 9);
        cds.addAttribute(GFF3Attributes.PSEUDO, "true");
        GFF3Annotation annotation = createAnnotation(cds);

        validation.validateTranslationPresence(annotation, 1);
    }

    @Test
    void throwsForIdlessCds() {
        // CDS with no ID cannot be keyed into TranslationState => unexpected state.
        GFF3Feature cds = new GFF3Feature(
                Optional.empty(),
                Optional.empty(),
                "seq1",
                Optional.empty(),
                ".",
                OntologyTerm.CDS.name(),
                1,
                9,
                ".",
                "+",
                "0");
        GFF3Annotation annotation = createAnnotation(cds);

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, () -> validation.validateTranslationPresence(annotation, 1));
        assertTrue(ex.getMessage().contains("seq1"));
        assertTrue(ex.getMessage().contains("1-9"));
    }

    @Test
    void multipleCdsOneMissingThrowsAndReportsOnlyMissing() {
        state.record(TranslationState.buildKey("seq1", "cds_ok"), null, "MK");

        GFF3Feature ok = createCds("cds_ok", "seq1", 1, 9);
        GFF3Feature missing = createCds("cds_missing", "seq1", 20, 30);
        GFF3Annotation annotation = createAnnotation(ok, missing);

        ValidationException ex =
                assertThrows(ValidationException.class, () -> validation.validateTranslationPresence(annotation, 1));
        assertTrue(ex.getMessage().contains("20-30"), "Should report the missing CDS");
        assertFalse(ex.getMessage().contains("1-9"), "Should not report the CDS that has a translation");
    }

    @Test
    void ignoresNonCdsFeatures() throws Exception {
        GFF3Annotation annotation = createAnnotation(createFeature("gene", "gene1", "seq1", 1, 9));
        validation.validateTranslationPresence(annotation, 1);
    }

    private void registerSequenceLookup(ValidationContext ctx, SequenceLookup lookup) {
        ctx.register(SequenceLookup.class, new ContextProvider<>() {
            @Override
            public SequenceLookup get(ValidationContext c) {
                return lookup;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
    }

    private GFF3Annotation createAnnotation(GFF3Feature... features) {
        GFF3Annotation annotation = new GFF3Annotation();
        for (GFF3Feature feature : features) {
            annotation.addFeature(feature);
        }
        return annotation;
    }

    private GFF3Feature createCds(String featureId, String seqId, long start, long end) {
        return createFeature(OntologyTerm.CDS.name(), featureId, seqId, start, end);
    }

    private GFF3Feature createFeature(String name, String featureId, String seqId, long start, long end) {
        return new GFF3Feature(
                Optional.of(featureId),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                name,
                start,
                end,
                ".",
                "+",
                "0");
    }
}
