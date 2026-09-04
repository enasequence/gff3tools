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
import java.util.Map;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;

/**
 * {@code GFF3Mapper} turns {@code partial} into the {@code <} or {@code >} on that segment's
 * location, so the attribute on an inner segment writes those markers into the middle of a
 * {@code join()}. Presence alone is judged, in file order, and nothing is exempt.
 */
@Gff3Validation(
        name = "JOINED_PARTIAL_ATTRIBUTE",
        description = "Only the terminal segments of a joined feature may carry a partial attribute")
public class JoinedPartialAttributeValidation implements Validation {

    private static final String RULE = "JOINED_PARTIAL_ATTRIBUTE";

    private static final String MISPLACED_PARTIAL_MESSAGE =
            "Only the terminal segments of a joined feature may be partial:%s";

    private static final String VIOLATION_MESSAGE =
            "\nFeature %s %s on accession \"%s\": segment %s carries a partial attribute, which belongs to the "
                    + "segments the join opens and closes with, %s and %s";

    @ValidationMethod(
            rule = RULE,
            description = "A partial attribute may only appear on the segment a joined feature opens or closes with",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateJoinedPartialAttribute(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();
        Map<String, List<GFF3Feature>> joinedFeaturesById = ValidationUtils.groupFeaturesById(
                annotation,
                feature -> feature.getId().isPresent(),
                // Both segments of a two-segment join are terminal, so an interior starts at three.
                joinedFeature -> joinedFeature.size() > 2);

        for (List<GFF3Feature> joinedFeature : joinedFeaturesById.values()) {
            violations.addAll(describeMisplacedPartials(joinedFeature));
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, MISPLACED_PARTIAL_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /** Reports every segment between the first and the last that is marked partial. */
    private List<String> describeMisplacedPartials(List<GFF3Feature> joinedFeature) {
        List<String> violations = new ArrayList<>();

        for (int i = 1; i < joinedFeature.size() - 1; i++) {
            GFF3Feature segment = joinedFeature.get(i);
            if (segment.hasAttribute(GFF3Attributes.PARTIAL)) {
                violations.add(VIOLATION_MESSAGE.formatted(
                        segment.getName(),
                        identify(joinedFeature),
                        segment.accession(),
                        location(segment),
                        location(joinedFeature.get(0)),
                        location(joinedFeature.get(joinedFeature.size() - 1))));
            }
        }
        return violations;
    }

    /** Names the feature by ID, falling back to its first segment's location. */
    private String identify(List<GFF3Feature> joinedFeature) {
        // Every segment answers for the group: they share the ID they were grouped under, and a
        // group without one was keyed on coordinates every member repeats.
        GFF3Feature representative = joinedFeature.get(0);
        return representative.getId().map("\"%s\""::formatted).orElseGet(() -> location(representative));
    }

    private String location(GFF3Feature feature) {
        return "%d..%d".formatted(feature.getStart(), feature.getEnd());
    }
}
