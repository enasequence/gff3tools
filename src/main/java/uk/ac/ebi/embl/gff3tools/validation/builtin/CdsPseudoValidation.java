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
import java.util.Collection;
import java.util.List;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.PseudoLookup;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.SourceTranslations;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * A pseudo CDS must have neither {@code product} nor a translation. {@code pseudogenic_CDS} is
 * checked too: SO doesn't make it a kind of CDS, but GFF3 to flat file converts it to a
 * {@code CDS} with {@code /pseudo}. An {@code exception} attribute does not exempt a pseudo CDS.
 */
@Gff3Validation(name = "CDS_PSEUDO")
public class CdsPseudoValidation {

    private static final String PRODUCT_RULE = "CDS_PSEUDO_PRODUCT";
    private static final String TRANSLATION_RULE = "CDS_PSEUDO_TRANSLATION";

    private static final String CDS_PSEUDO_PRODUCT_MESSAGE =
            "%s \"%s\" at %s:%s has a \"product\" attribute but is pseudo: %s. A pseudo CDS must not name a"
                    + " product; move the value into \"note\" or add a comment for a curator.";

    private static final String CDS_PSEUDO_TRANSLATION_MESSAGE =
            "%s \"%s\" at %s:%s has a translation but is pseudo: %s. A pseudo CDS must not have a"
                    + " translation; remove it.";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = PRODUCT_RULE,
            description = "A pseudo CDS, or one under a pseudo parent, must not have a product",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateProduct(GFF3Annotation annotation, int line) throws ValidationException {
        PseudoLookup pseudoLookup = PseudoLookup.of(annotation, context);

        List<String> violations = new ArrayList<>();
        for (List<GFF3Feature> segments : cdsGroups(annotation, pseudoLookup)) {
            if (segments.stream().noneMatch(s -> s.hasAttribute(GFF3Attributes.PRODUCT))) {
                continue;
            }
            pseudoLookup
                    .pseudoReason(segments)
                    .ifPresent(reason -> violations.add(describe(CDS_PSEUDO_PRODUCT_MESSAGE, segments, reason)));
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, String.join("\n", violations));
        }
    }

    /**
     * Runs after {@code TranslationFix} so that the recorded translations are the ones that would be
     * written out. The source file's {@code ##FASTA} is consulted last, and only for a pseudo CDS.
     */
    @ValidationMethod(
            rule = TRANSLATION_RULE,
            description = "A pseudo CDS, or one under a pseudo parent, must not have a translation",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR,
            priority = ValidationPriority.LOW)
    public void validateTranslation(GFF3Annotation annotation, int line) throws ValidationException {
        PseudoLookup pseudoLookup = PseudoLookup.of(annotation, context);

        List<String> violations = new ArrayList<>();
        for (List<GFF3Feature> segments : cdsGroups(annotation, pseudoLookup)) {
            pseudoLookup.pseudoReason(segments).ifPresent(reason -> {
                if (hasTranslation(segments)) {
                    violations.add(describe(CDS_PSEUDO_TRANSLATION_MESSAGE, segments, reason));
                }
            });
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, String.join("\n", violations));
        }
    }

    private Collection<List<GFF3Feature>> cdsGroups(GFF3Annotation annotation, PseudoLookup pseudoLookup) {
        return ValidationUtils.groupFeaturesById(annotation, pseudoLookup::isCdsOrPseudogenicCds)
                .values();
    }

    /**
     * Whether the CDS has a translation from any source: an inline attribute (still present when
     * {@code REMOVE_TRANSLATION_ATTRIBUTE} is off), the one recorded for it, or the source file's
     * {@code ##FASTA}. A recorded translation counts whether submitted or generated, matching what
     * {@link TranslationState#forEachResolved} writes out.
     */
    private boolean hasTranslation(List<GFF3Feature> segments) {
        if (segments.stream().anyMatch(s -> s.hasAttribute(GFF3Attributes.TRANSLATION))) {
            return true;
        }

        GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);
        String key = TranslationState.buildKey(
                representative.accession(), representative.getId().orElse(null));
        if (key == null) {
            return false;
        }

        if (context.contains(TranslationState.class)) {
            TranslationState.TranslationEntry entry =
                    context.get(TranslationState.class).get(key);
            if (entry != null && (isPresent(entry.newTranslation()) || isPresent(entry.oldTranslation()))) {
                return true;
            }
        }

        return context.contains(SourceTranslations.class)
                && context.get(SourceTranslations.class).contains(key);
    }

    private static boolean isPresent(String translation) {
        return translation != null && !translation.isBlank();
    }

    private static String describe(String template, List<GFF3Feature> segments, String reason) {
        GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);
        return template.formatted(
                representative.getName(),
                representative.getId().orElse("<no ID>"),
                representative.accession(),
                ValidationUtils.getLocationString(representative),
                reason);
    }
}
