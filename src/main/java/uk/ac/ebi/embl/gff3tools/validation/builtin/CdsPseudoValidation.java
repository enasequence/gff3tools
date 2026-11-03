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

/**
 * A pseudo CDS must not have a {@code product}; {@code CDS_TRANSLATION_ABSENCE} checks its
 * translation. {@code pseudogenic_CDS} is checked too: SO doesn't make it a kind of CDS, but GFF3 to
 * flat file converts it to a {@code CDS} with {@code /pseudo}.
 */
@Gff3Validation(name = "CDS_PSEUDO")
public class CdsPseudoValidation {

    private static final String PRODUCT_RULE = "CDS_PSEUDO_PRODUCT";

    private static final String CDS_PSEUDO_PRODUCT_MESSAGE =
            "%s \"%s\" at %s:%s has a \"product\" attribute but is pseudo: %s. A pseudo CDS must not name a"
                    + " product; move the value into \"note\" or add a comment for a curator.";

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
            // MUTUALLY_EXCLUSIVE_ATTRIBUTES already reports pseudo and product on the same line
            if (segments.stream().anyMatch(CdsPseudoValidation::hasPseudoAndProduct)) {
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

    private static boolean hasPseudoAndProduct(GFF3Feature line) {
        return line.hasAttribute(GFF3Attributes.PRODUCT)
                && (line.hasAttribute(GFF3Attributes.PSEUDO) || line.hasAttribute(GFF3Attributes.PSEUDOGENE));
    }

    private Collection<List<GFF3Feature>> cdsGroups(GFF3Annotation annotation, PseudoLookup pseudoLookup) {
        return ValidationUtils.groupFeaturesById(annotation, pseudoLookup::isCdsOrPseudogenicCds)
                .values();
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
