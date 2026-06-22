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

    private boolean translationWasRequested() {
        return context.contains(SequenceLookup.class)
                && context.get(SequenceLookup.class) != null
                && context.contains(TranslationState.class);
    }

    private Map<String, List<GFF3Feature>> groupCdsById(GFF3Annotation annotation) {
        return annotation.getFeatures().stream()
                .filter(f -> OntologyTerm.CDS.name().equals(f.getName()))
                .collect(Collectors.groupingBy(f -> f.getId().orElse("__no_id_" + f.getStart() + "_" + f.getEnd())));
    }

    private GFF3Feature representativeOf(List<GFF3Feature> segments) {
        return segments.stream()
                .min(Comparator.comparingLong(GFF3Feature::getStart))
                .orElseThrow();
    }

    private boolean hasGeneratedTranslation(TranslationState.TranslationEntry entry) {
        return entry != null && entry.newTranslation() != null;
    }

    private boolean isExempt(List<GFF3Feature> segments) {
        return segments.stream()
                .anyMatch(s -> s.getAttribute(GFF3Attributes.EXCEPTION).isPresent()
                        || s.hasAttribute(GFF3Attributes.PSEUDO)
                        || s.hasAttribute(GFF3Attributes.PSEUDOGENE));
    }
}
