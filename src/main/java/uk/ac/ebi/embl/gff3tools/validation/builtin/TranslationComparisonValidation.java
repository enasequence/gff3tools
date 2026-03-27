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
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Validates that newly computed translations match any pre-existing translation attribute.
 *
 * <p>Runs at {@link ValidationPriority#LOW} — the same tier as {@code TranslationFix}.
 * Within a tier, fixes run before validations, so by the time this validator executes,
 * {@code TranslationFix} has already recorded old and new translations in
 * {@link TranslationState}.
 *
 * <p>If no old translation existed, this validation is a no-op.
 */
@Gff3Validation(name = "TRANSLATION_COMPARISON")
public class TranslationComparisonValidation {

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = "TRANSLATION_COMPARISON",
            description = "Compare existing translation with newly computed translation",
            type = ANNOTATION,
            severity = RuleSeverity.WARN,
            priority = ValidationPriority.LOW)
    public void validateTranslation(GFF3Annotation annotation, int line) throws ValidationException {
        if (!context.contains(TranslationState.class)) {
            return;
        }
        TranslationState state = context.get(TranslationState.class);

        // Group CDS features by ID, using the same fallback key as TranslationFix so that
        // groups match 1:1. This relies on no fix modifying feature coordinates before these
        // LOW-priority rules execute.
        Map<String, List<GFF3Feature>> cdsGroups = annotation.getFeatures().stream()
                .filter(f -> OntologyTerm.CDS.name().equals(f.getName()))
                .collect(Collectors.groupingBy(f -> f.getId().orElse("__no_id_" + f.getStart() + "_" + f.getEnd())));

        List<String> mismatches = new ArrayList<>();

        for (Map.Entry<String, List<GFF3Feature>> entry : cdsGroups.entrySet()) {
            // Sort by start position to match TranslationFix's representative selection
            GFF3Feature representative = entry.getValue().stream()
                    .min(Comparator.comparingLong(GFF3Feature::getStart))
                    .orElseThrow();
            String key = TranslationState.buildKey(
                    representative.getSeqId(), representative.getId().orElse(null), line);
            TranslationState.TranslationEntry translationEntry = state.get(key);
            if (translationEntry == null || translationEntry.oldTranslation() == null) {
                continue;
            }

            if (!translationEntry.oldTranslation().equals(translationEntry.newTranslation())) {
                String featureId = representative.getId().orElse("unknown");
                mismatches.add(
                        ("CDS \"%s\" on %s: existing translation length %d differs from computed translation length %d")
                                .formatted(
                                        featureId,
                                        representative.accession(),
                                        translationEntry.oldTranslation().length(),
                                        translationEntry.newTranslation().length()));
            }
        }

        if (!mismatches.isEmpty()) {
            throw new ValidationException(
                    "TRANSLATION_COMPARISON", line, "Translation mismatch(es): " + String.join("; ", mismatches));
        }
    }
}
