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

import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Records the submitted translation of a feature as its old translation in {@link TranslationState},
 * and warns when the feature's translation attribute differs from its {@code ##FASTA} translation.
 *
 * <p>The {@code ##FASTA} protein wins over the attribute: in a submitted GFF3 that is where we expect
 * it, while the attribute mostly comes from flat-file conversion. {@code TRANSLATION_COMPARISON}
 * later compares the recorded old translation with the one {@code TranslationFix} computes.
 *
 * <p>Runs at {@link ValidationPriority#HIGH}, before {@code REMOVE_TRANSLATION_ATTRIBUTE} (NORMAL)
 * removes the attribute: within a tier fixes run before validations, so the two can't share one.
 */
@Slf4j
@Gff3Validation(name = "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH")
public class TranslationAttributeFastaMismatchValidation {

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH",
            description =
                    "Record the submitted translation (##FASTA, else the translation attribute) and warn when the attribute differs from the ##FASTA translation",
            type = FEATURE,
            severity = RuleSeverity.WARN,
            priority = ValidationPriority.HIGH)
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        String featureId = feature.getId().orElse(null);
        String translationInAttribute =
                feature.getAttribute(GFF3Attributes.TRANSLATION).orElse(null);
        String translationInGff3Fasta = lookupFastaTranslation(feature.accession(), featureId);
        String submittedTranslation = translationInGff3Fasta != null ? translationInGff3Fasta : translationInAttribute;

        // Recorded before any warning is thrown, so the comparison sees it whatever the severity.
        if (submittedTranslation != null && context.contains(TranslationState.class)) {
            String key = TranslationState.buildKey(feature.accession(), featureId);
            if (key != null) {
                // The new translation is computed later by TranslationFix
                context.get(TranslationState.class).record(key, submittedTranslation, null);
            }
        }

        if (translationInGff3Fasta != null
                && translationInAttribute != null
                && !translationInGff3Fasta.equals(translationInAttribute)) {
            throw new ValidationException(
                    "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH",
                    line,
                    "The translation attribute of feature \"%s\" on \"%s\" differs from its ##FASTA translation; the ##FASTA translation is used"
                            .formatted(featureId, feature.accession()));
        }
    }

    /**
     * The submitted {@code ##FASTA} protein for this feature, or null when there is none, there is no
     * GFF3 reader (flat file and TSV conversion), or it cannot be read. A read failure is not reported
     * here: {@code FASTA_SECTION_UNREADABLE} reports it.
     */
    private String lookupFastaTranslation(String accession, String featureId) {
        if (featureId == null || !context.contains(GFF3TranslationReader.class)) {
            return null;
        }
        try {
            return context.get(GFF3TranslationReader.class)
                    .getTranslation(accession, featureId)
                    .orElse(null);
        } catch (ReadException e) {
            log.debug("##FASTA translations could not be read: {}", e.getMessage());
            return null;
        }
    }
}
