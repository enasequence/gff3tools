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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import uk.ac.ebi.embl.gff3tools.validation.provider.SourceTranslations;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

class CdsTranslationValidationTest {

    private CdsTranslationValidation validation;
    private ValidationContext context;
    private TranslationState state;

    @BeforeEach
    void setUp() {
        validation = new CdsTranslationValidation();
        context = new ValidationContext();
        context.register(TranslationState.class, new TranslationStateProvider());
        state = context.get(TranslationState.class);
        registerSequenceLookup(context, mock(SequenceLookup.class));
        ValidationRegistry.injectContext(validation, context);
    }

    @Nested
    class Presence {

        @Test
        void noOpWhenTranslationNotRequested() throws Exception {
            // No SequenceLookup registered => translation was not requested.
            CdsTranslationValidation noLookup = new CdsTranslationValidation();
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

            ValidationException ex = assertThrows(
                    ValidationException.class, () -> validation.validateTranslationPresence(annotation, 1));
            assertTrue(ex.getMessage().contains("CDS_TRANSLATION_PRESENCE"));
            assertTrue(ex.getMessage().contains("seq1"));
            assertTrue(ex.getMessage().contains("1-9"));
        }

        @Test
        void throwsWhenTranslationRecordedButNewIsNull() {
            // e.g. an old translation was captured but no new translation was generated.
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);

            GFF3Annotation annotation = createAnnotation(createCds("cds1", "seq1", 1, 9));
            ValidationException ex = assertThrows(
                    ValidationException.class, () -> validation.validateTranslationPresence(annotation, 1));
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

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class, () -> validation.validateTranslationPresence(annotation, 1));
            assertTrue(ex.getMessage().contains("seq1"));
            assertTrue(ex.getMessage().contains("1-9"));
        }

        @Test
        void multipleCdsOneMissingThrowsAndReportsOnlyMissing() {
            state.record(TranslationState.buildKey("seq1", "cds_ok"), null, "MK");

            GFF3Feature ok = createCds("cds_ok", "seq1", 1, 9);
            GFF3Feature missing = createCds("cds_missing", "seq1", 20, 30);
            GFF3Annotation annotation = createAnnotation(ok, missing);

            ValidationException ex = assertThrows(
                    ValidationException.class, () -> validation.validateTranslationPresence(annotation, 1));
            assertTrue(ex.getMessage().contains("20-30"), "Should report the missing CDS");
            assertFalse(ex.getMessage().contains("1-9"), "Should not report the CDS that has a translation");
        }

        @Test
        void ignoresNonCdsFeatures() throws Exception {
            GFF3Annotation annotation = createAnnotation(createFeature("gene", "gene1", "seq1", 1, 9));
            validation.validateTranslationPresence(annotation, 1);
        }
    }

    @Nested
    class Absence {

        private GFF3Feature pseudoGene(String id) {
            GFF3Feature gene = createFeature("gene", id, "seq1", 1, 30);
            gene.addAttribute(GFF3Attributes.PSEUDOGENE, "unprocessed");
            return gene;
        }

        private void assertReported(GFF3Annotation annotation, String... expected) {
            ValidationException ex =
                    assertThrows(ValidationException.class, () -> validation.validateTranslationAbsence(annotation, 1));
            assertTrue(ex.getMessage().contains("CDS_TRANSLATION_ABSENCE"), ex.getMessage());
            for (String text : expected) {
                assertTrue(ex.getMessage().contains(text), "Expected \"" + text + "\" in: " + ex.getMessage());
            }
        }

        @Test
        void passesWhenNoCdsIsPseudo() throws Exception {
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", "MK");
            GFF3Feature inline = createCds("cds2", "seq1", 20, 28);
            inline.addAttribute(GFF3Attributes.TRANSLATION, "MK");

            validation.validateTranslationAbsence(
                    createAnnotation(
                            createFeature("gene", "gene1", "seq1", 1, 30), createCds("cds1", "seq1", 1, 9), inline),
                    1);
        }

        @Test
        void passesWhenPseudoCdsHasNoTranslation() throws Exception {
            validation.validateTranslationAbsence(
                    createAnnotation(pseudoGene("gene1"), createChild("CDS", "cds1", "gene1", "seq1", 1, 9)), 1);
        }

        @Test
        void throwsForRecordedOldTranslationUnderPseudoParent() {
            // CDS -> mRNA -> gene with pseudogene, translation captured from the inline attribute.
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);
            GFF3Annotation annotation = createAnnotation(
                    pseudoGene("gene1"),
                    createChild("mRNA", "mrna1", "gene1", "seq1", 1, 20),
                    createChild("CDS", "cds1", "mrna1", "seq1", 1, 9));

            assertReported(annotation, "seq1 CDS: 1-9", "its ancestor \"gene1\" has pseudogene=unprocessed");
        }

        @Test
        void throwsForRecordedNewTranslation() {
            // e.g. an exception CDS, whose submitted translation TranslationFix records as the new one.
            state.record(TranslationState.buildKey("seq1", "cds1"), null, "MK");
            GFF3Feature cds = createChild("CDS", "cds1", "gene1", "seq1", 1, 9);
            cds.addAttribute(GFF3Attributes.EXCEPTION, "RNA editing");

            assertReported(createAnnotation(pseudoGene("gene1"), cds), "seq1 CDS: 1-9");
        }

        @Test
        void throwsForInlineTranslationAttributeUnderPseudogeneType() {
            // The attribute is still on the feature when REMOVE_TRANSLATION_ATTRIBUTE is off.
            GFF3Feature cds = createChild("CDS", "cds1", "pg1", "seq1", 1, 9);
            cds.addAttribute(GFF3Attributes.TRANSLATION, "MK");
            GFF3Annotation annotation = createAnnotation(createFeature("pseudogene", "pg1", "seq1", 1, 30), cds);

            assertReported(annotation, "its ancestor \"pg1\" is of type pseudogene");
        }

        @Test
        void throwsForTranslationInSourceFasta() {
            context.register(
                    SourceTranslations.class,
                    new SourceTranslations(() -> Set.of(TranslationState.buildKey("seq1", "cds1"))));
            GFF3Annotation annotation =
                    createAnnotation(pseudoGene("gene1"), createChild("CDS", "cds1", "gene1", "seq1", 1, 9));

            assertReported(annotation, "seq1 CDS: 1-9");
        }

        @Test
        void throwsWhenPseudoIsOnSecondLineOfParent() {
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);
            GFF3Feature geneFirstLine = createFeature("gene", "gene1", "seq1", 1, 10);
            GFF3Feature geneSecondLine = createFeature("gene", "gene1", "seq1", 20, 30);
            geneSecondLine.addAttribute(GFF3Attributes.PSEUDOGENE, "unprocessed");
            GFF3Annotation annotation =
                    createAnnotation(geneFirstLine, geneSecondLine, createChild("CDS", "cds1", "gene1", "seq1", 1, 9));

            assertReported(annotation, "its ancestor \"gene1\" has pseudogene=unprocessed");
        }

        @Test
        void throwsForCdsThatIsItselfPseudo() {
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);
            GFF3Feature cds = createCds("cds1", "seq1", 1, 9);
            cds.addAttribute(GFF3Attributes.PSEUDO, "true");

            assertReported(createAnnotation(cds), "it has pseudo=true");
        }

        @Test
        void throwsForPseudogenicCds() {
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);
            GFF3Annotation annotation = createAnnotation(createFeature("pseudogenic_CDS", "cds1", "seq1", 1, 9));

            assertReported(annotation, "seq1 pseudogenic_CDS: 1-9", "it is of type pseudogenic_CDS");
        }

        @Test
        void throwsForTypesGivenAsSoAccessions() {
            // SO:0000336 is pseudogene, SO:0000316 is CDS.
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);
            GFF3Annotation annotation = createAnnotation(
                    createFeature("SO:0000336", "pg1", "seq1", 1, 30),
                    createChild("SO:0000316", "cds1", "pg1", "seq1", 1, 9));

            assertReported(annotation, "its ancestor \"pg1\" is of type SO:0000336");
        }

        @Test
        void runsWhenTranslationWasNotRequested() {
            // Unlike presence, absence does not need a SequenceLookup.
            CdsTranslationValidation noLookup = new CdsTranslationValidation();
            ValidationContext ctx = new ValidationContext();
            ctx.register(TranslationState.class, new TranslationStateProvider());
            ctx.get(TranslationState.class).record(TranslationState.buildKey("seq1", "cds1"), "MK", null);
            ValidationRegistry.injectContext(noLookup, ctx);
            GFF3Annotation annotation =
                    createAnnotation(pseudoGene("gene1"), createChild("CDS", "cds1", "gene1", "seq1", 1, 9));

            ValidationException ex =
                    assertThrows(ValidationException.class, () -> noLookup.validateTranslationAbsence(annotation, 1));
            assertTrue(ex.getMessage().contains("seq1 CDS: 1-9"));
        }

        @Test
        void multipleCdsReportsOnlyPseudoOnesWithTranslation() {
            state.record(TranslationState.buildKey("seq1", "cds_pseudo"), "MK", null);
            state.record(TranslationState.buildKey("seq1", "cds_ok"), "MK", "MK");
            GFF3Annotation annotation = createAnnotation(
                    pseudoGene("gene1"),
                    createChild("CDS", "cds_pseudo", "gene1", "seq1", 1, 9),
                    createChild("CDS", "cds_pseudo_untranslated", "gene1", "seq1", 12, 20),
                    createFeature("gene", "gene2", "seq1", 40, 60),
                    createChild("CDS", "cds_ok", "gene2", "seq1", 40, 48));

            ValidationException ex =
                    assertThrows(ValidationException.class, () -> validation.validateTranslationAbsence(annotation, 1));
            assertTrue(ex.getMessage().contains("1-9"), "Should report the pseudo CDS with a translation");
            assertFalse(ex.getMessage().contains("12-20"), "Should not report a pseudo CDS without one");
            assertFalse(ex.getMessage().contains("40-48"), "Should not report a CDS that isn't pseudo");
        }

        @Test
        void ignoresBlankRecordedTranslation() throws Exception {
            state.record(TranslationState.buildKey("seq1", "cds1"), " ", "");
            validation.validateTranslationAbsence(
                    createAnnotation(pseudoGene("gene1"), createChild("CDS", "cds1", "gene1", "seq1", 1, 9)), 1);
        }

        @Test
        void terminatesOnParentCycle() throws Exception {
            state.record(TranslationState.buildKey("seq1", "cds1"), "MK", null);
            GFF3Annotation annotation = createAnnotation(
                    createChild("gene", "geneA", "geneB", "seq1", 1, 30),
                    createChild("gene", "geneB", "geneA", "seq1", 1, 30),
                    createChild("CDS", "cds1", "geneA", "seq1", 1, 9));

            // Neither gene is pseudo, so nothing is reported, and the walk must not loop.
            validation.validateTranslationAbsence(annotation, 1);
        }

        @Test
        void reportsUnreadableSourceFastaAsViolation() {
            context.register(SourceTranslations.class, new SourceTranslations(() -> {
                throw new RuntimeException("Invalid GFF3 translation sequence: not a sequence");
            }));
            GFF3Annotation annotation =
                    createAnnotation(pseudoGene("gene1"), createChild("CDS", "cds1", "gene1", "seq1", 1, 9));

            assertReported(annotation, "could not be read", "not a sequence");
        }
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

    private GFF3Feature createChild(
            String name, String featureId, String parentId, String seqId, long start, long end) {
        return new GFF3Feature(
                Optional.of(featureId),
                Optional.of(parentId),
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
