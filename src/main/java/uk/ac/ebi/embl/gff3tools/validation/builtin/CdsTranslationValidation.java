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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.PseudoLookup;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.SourceTranslations;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Checks the translations CDS features end up with after {@code TranslationFix}: every CDS that
 * should be translated has one ({@code CDS_TRANSLATION_PRESENCE}), and no CDS that must not be
 * translated has one ({@code CDS_TRANSLATION_ABSENCE}).
 */
@Gff3Validation(name = "CDS_TRANSLATION")
public class CdsTranslationValidation {

    private static final String PRESENCE_RULE = "CDS_TRANSLATION_PRESENCE";
    private static final String ABSENCE_RULE = "CDS_TRANSLATION_ABSENCE";

    private static final String UNREADABLE_SOURCE_TRANSLATIONS_MESSAGE =
            "Could not check the translations of pseudo CDS features, because the ##FASTA section could not be"
                    + " read: %s";

    @InjectContext
    private ValidationContext context;

    /**
     * Runs only when translation was requested, and only for features named {@code CDS}, the ones
     * {@code TranslationFix} translates. A CDS with an {@code exception} attribute, or a pseudo CDS,
     * is exempt.
     */
    @ValidationMethod(
            rule = PRESENCE_RULE,
            description = "Ensure every translatable CDS received a translation when translation was requested",
            type = ANNOTATION,
            priority = ValidationPriority.LOW)
    public void validateTranslationPresence(GFF3Annotation annotation, int line) throws ValidationException {
        if (!translationWasRequested()) {
            return;
        }
        TranslationState state = context.get(TranslationState.class);
        PseudoLookup pseudoLookup = PseudoLookup.of(annotation, context);

        List<String> missing = new ArrayList<>();
        for (List<GFF3Feature> segments : ValidationUtils.groupFeaturesById(
                        annotation, f -> OntologyTerm.CDS.name().equals(f.getName()))
                .values()) {
            if (isExempt(segments, pseudoLookup)) {
                continue;
            }
            GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);

            String key = translationKey(representative);
            if (key == null) {
                throw new IllegalStateException(
                        "Cannot build a translation key for CDS without an ID on accession '%s' at %d-%d"
                                .formatted(
                                        representative.accession(),
                                        representative.getStart(),
                                        representative.getEnd()));
            }

            if (!hasGeneratedTranslation(state.get(key))) {
                missing.add("\nNo translation generated for: %s CDS: %d-%d"
                        .formatted(representative.accession(), representative.getStart(), representative.getEnd()));
            }
        }

        if (!missing.isEmpty()) {
            throw new ValidationException(
                    PRESENCE_RULE, line, "Missing CDS translation(s): " + String.join("", missing));
        }
    }

    /**
     * Ensures translations that were exempt from translation were - indeed - not translated
     */
    @ValidationMethod(
            rule = ABSENCE_RULE,
            description = "Ensure no non-translatable CDS (pseudo, or under a pseudo parent) has a translation",
            type = ANNOTATION,
            priority = ValidationPriority.LOW)
    public void validateTranslationAbsence(GFF3Annotation annotation, int line) throws ValidationException {
        PseudoLookup pseudoLookup = PseudoLookup.of(annotation, context);

        List<String> unexpected = new ArrayList<>();
        for (List<GFF3Feature> segments : ValidationUtils.groupFeaturesById(
                        annotation, pseudoLookup::isCdsOrPseudogenicCds)
                .values()) {
            if (!requiresNoTranslation(segments, pseudoLookup)) {
                continue;
            }
            GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);

            if (hasTranslation(segments, translationKey(representative), line)) {
                unexpected.add("\nTranslation provided for: %s %s: %d-%d (%s)"
                        .formatted(
                                representative.accession(),
                                representative.getName(),
                                representative.getStart(),
                                representative.getEnd(),
                                pseudoLookup.pseudoReason(segments).orElseThrow()));
            }
        }

        if (!unexpected.isEmpty()) {
            throw new ValidationException(
                    ABSENCE_RULE, line, "Unexpected CDS translation(s): " + String.join("", unexpected));
        }
    }

    private boolean translationWasRequested() {
        return context.contains(SequenceLookup.class)
                && context.get(SequenceLookup.class) != null
                && context.contains(TranslationState.class);
    }

    private boolean hasGeneratedTranslation(TranslationState.TranslationEntry entry) {
        return entry != null
                && entry.newTranslation() != null
                && !entry.newTranslation().isBlank();
    }

    private boolean isExempt(List<GFF3Feature> segments, PseudoLookup pseudoLookup) {
        boolean hasException = segments.stream()
                .anyMatch(s -> s.getAttribute(GFF3Attributes.EXCEPTION).isPresent());
        boolean isPseudo = requiresNoTranslation(segments, pseudoLookup);
        return hasException || isPseudo;
    }

    /** A pseudo CDS, pseudo itself or through an ancestor, must not be translated. */
    private boolean requiresNoTranslation(List<GFF3Feature> segments, PseudoLookup pseudoLookup) {
        return pseudoLookup.isPseudo(segments);
    }

    /** The key a CDS's translation is recorded under, or {@code null} for a CDS without an ID. */
    private static String translationKey(GFF3Feature representative) {
        return TranslationState.buildKey(
                representative.accession(), representative.getId().orElse(null));
    }

    /**
     * Whether the CDS has a translation from any source: an inline attribute (still present when
     * {@code REMOVE_TRANSLATION_ATTRIBUTE} is off), the one recorded for it, or the source file's
     * {@code ##FASTA}. A recorded translation counts whether submitted or generated, matching what
     * {@link TranslationState#forEachResolved} writes out.
     */
    private boolean hasTranslation(List<GFF3Feature> segments, String key, int line) throws ValidationException {
        if (segments.stream().anyMatch(s -> s.hasAttribute(GFF3Attributes.TRANSLATION))) {
            return true;
        }
        if (key == null) {
            return false;
        }
        if (context.contains(TranslationState.class)
                && hasRecordedTranslation(context.get(TranslationState.class).get(key))) {
            return true;
        }
        return context.contains(SourceTranslations.class) && hasSourceTranslation(key, line);
    }

    private boolean hasRecordedTranslation(TranslationState.TranslationEntry entry) {
        return entry != null && (isPresent(entry.newTranslation()) || isPresent(entry.oldTranslation()));
    }

    /**
     * Reading the source file's {@code ##FASTA} fails with an unchecked exception when the section is
     * malformed. Report-only validation has no other reason to read it, so the failure is reported
     * here as a violation rather than ending the run with a stack trace.
     */
    private boolean hasSourceTranslation(String key, int line) throws ValidationException {
        try {
            return context.get(SourceTranslations.class).contains(key);
        } catch (RuntimeException e) {
            throw new ValidationException(
                    ABSENCE_RULE, line, UNREADABLE_SOURCE_TRANSLATIONS_MESSAGE.formatted(e.getMessage()));
        }
    }

    private static boolean isPresent(String translation) {
        return translation != null && !translation.isBlank();
    }
}
