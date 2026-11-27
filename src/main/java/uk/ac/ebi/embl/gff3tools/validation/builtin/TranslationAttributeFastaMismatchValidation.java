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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.FEATURE;

import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Warns when a feature's translation attribute differs from its {@code ##FASTA} translation, which
 * {@code GFF3FileReader} has already recorded as the old translation in {@link TranslationState}.
 * The {@code ##FASTA} protein is kept; the attribute is removed by {@code REMOVE_TRANSLATION_ATTRIBUTE}.
 *
 * <p>Runs at {@link ValidationPriority#HIGH}, before {@code REMOVE_TRANSLATION_ATTRIBUTE} (NORMAL)
 * removes the attribute.
 */
@Gff3Validation(name = "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH")
public class TranslationAttributeFastaMismatchValidation {

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH",
            description = "Warn when the translation attribute differs from the ##FASTA translation",
            type = FEATURE,
            severity = RuleSeverity.WARN,
            priority = ValidationPriority.HIGH)
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        String translationInAttribute =
                feature.getAttribute(GFF3Attributes.TRANSLATION).orElse(null);
        if (translationInAttribute == null || !context.contains(TranslationState.class)) {
            return;
        }
        String featureId = feature.getId().orElse(null);
        String key = TranslationState.buildKey(feature.accession(), featureId);
        TranslationState.TranslationEntry entry =
                key == null ? null : context.get(TranslationState.class).get(key);
        String translationInGff3Fasta = entry == null ? null : entry.oldTranslation();

        if (translationInGff3Fasta != null
                && !translationInGff3Fasta.isEmpty()
                && !translationInGff3Fasta.equals(translationInAttribute)) {
            throw new ValidationException(
                    "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH",
                    line,
                    "The translation attribute of feature \"%s\" on \"%s\" differs from its ##FASTA translation; the ##FASTA translation is used"
                            .formatted(featureId, feature.accession()));
        }
    }
}
