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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Validates that every CDS feature received a generated translation when translation was requested.
 *
 * <p>This is a safety net for the translation pass: if the user asked for translation to be
 * generated, no CDS should silently be left without one. Translation is considered "requested"
 * only when a {@link SequenceLookup} is present in the context — the same signal {@code
 * TranslationFix} gates on. If translation was not requested, this validation is a no-op.
 *
 * <p>Runs at {@link ValidationPriority#LOW} — the same tier as {@code TranslationFix}. Within a
 * tier, fixes run before validations, so by the time this validator executes {@code TranslationFix}
 * has already recorded the generated translations in {@link TranslationState}.
 *
 * <p>CDS features that {@code TranslationFix} does not translate are exempt and never flagged:
 *
 * <ul>
 *   <li>CDS carrying an {@code /exception} attribute (e.g. ribosomal slippage),
 *   <li>ID-less CDS, which cannot be keyed into {@link TranslationState},
 *   <li>pseudo CDS (carrying {@code /pseudo} or {@code /pseudogene}).
 * </ul>
 */
@Slf4j
@Gff3Validation(name = "CDS_TRANSLATION_PRESENCE")
public class CdsTranslationPresenceValidation {

    private static final String RULE = "CDS_TRANSLATION_PRESENCE";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE,
            description = "Ensure every translatable CDS received a translation when translation was requested",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR,
            priority = ValidationPriority.LOW)
    public void validateTranslationPresence(GFF3Annotation annotation, int line) throws ValidationException {
        if (!translationWasRequested()) {
            return;
        }
        TranslationState state = context.get(TranslationState.class);

        List<String> missing = new ArrayList<>();
        for (List<GFF3Feature> segments : groupCdsById(annotation).values()) {
            if (isExempt(segments)) {
                continue;
            }
            GFF3Feature representative = representativeOf(segments);

            // ID-less CDS cannot be keyed into TranslationState — skip (treated as exempt).
            String key = TranslationState.buildKey(
                    representative.accession(), representative.getId().orElse(null));
            if (key == null) {
                continue;
            }

            if (!hasGeneratedTranslation(state.get(key))) {
                missing.add("\nNo translation generated for: %s CDS: %d-%d"
                        .formatted(representative.accession(), representative.getStart(), representative.getEnd()));
            }
        }

        if (!missing.isEmpty()) {
            throw new ValidationException(RULE, line, "Missing CDS translation(s): " + String.join("", missing));
        }
    }

    /**
     * Translation was requested only when a sequence source is available. {@link TranslationState}
     * must also be present, since that is where {@code TranslationFix} records its results.
     */
    private boolean translationWasRequested() {
        return context.contains(SequenceLookup.class)
                && context.get(SequenceLookup.class) != null
                && context.contains(TranslationState.class);
    }

    /**
     * Groups CDS features into joins by ID, using the same fallback key as {@code TranslationFix}
     * so that the groups match 1:1.
     */
    private Map<String, List<GFF3Feature>> groupCdsById(GFF3Annotation annotation) {
        return annotation.getFeatures().stream()
                .filter(f -> OntologyTerm.CDS.name().equals(f.getName()))
                .collect(Collectors.groupingBy(f -> f.getId().orElse("__no_id_" + f.getStart() + "_" + f.getEnd())));
    }

    /** The lowest-start segment, matching {@code TranslationFix}'s representative selection. */
    private GFF3Feature representativeOf(List<GFF3Feature> segments) {
        return segments.stream()
                .min(Comparator.comparingLong(GFF3Feature::getStart))
                .orElseThrow();
    }

    private boolean hasGeneratedTranslation(TranslationState.TranslationEntry entry) {
        return entry != null && entry.newTranslation() != null;
    }

    /** A CDS group is exempt when any segment carries an exception or marks the CDS as pseudo. */
    private boolean isExempt(List<GFF3Feature> segments) {
        return segments.stream()
                .anyMatch(s -> s.getAttribute(GFF3Attributes.EXCEPTION).isPresent()
                        || s.hasAttribute(GFF3Attributes.PSEUDO)
                        || s.hasAttribute(GFF3Attributes.PSEUDOGENE));
    }
}
