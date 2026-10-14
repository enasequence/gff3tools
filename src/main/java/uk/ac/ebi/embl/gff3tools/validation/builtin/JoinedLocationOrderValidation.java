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
import uk.ac.ebi.embl.gff3tools.utils.TopologyUtils;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;

/**
 * {@code GFF3Mapper} builds the flat file {@code join()} by appending same-ID segments in the order
 * their lines appear, so segments listed out of coordinate order produce a backwards {@code join()}.
 * Overlap and transcript alignment are separate rules.
 *
 * <p>This holds on circular sequences too: INSDC GFF3 numbers everything past the origin in virtual
 * space (past the sequence length), so a feature crossing the origin still ascends (e.g. {@code 901
 * 1000} then {@code 1001 1200}). Restarting at 1 ({@code 1 200}) is not allowed. On a circular
 * sequence a joined feature must also be shorter than the sequence, or it would lap its own start.
 */
@Gff3Validation(
        name = "JOINED_LOCATION_ORDER",
        description = "The segments of a joined feature must be listed in ascending coordinate order")
public class JoinedLocationOrderValidation implements Validation {

    private static final String RULE = "JOINED_LOCATION_ORDER";

    private static final String OUT_OF_ORDER_MESSAGE =
            "Joined features must list their segments in ascending coordinate order:%s";

    private static final String VIOLATION_MESSAGE =
            "\nFeature %s \"%s\" on accession \"%s\": segment %s starts before the preceding segment %s";

    private static final String CIRCULAR_HINT =
            " (on a circular sequence, segments past the origin continue past the sequence length: write %s as %d..%d)";

    private static final String LONGER_THAN_SEQUENCE_MESSAGE =
            "\nFeature %s \"%s\" on accession \"%s\": segments %s to %s span %d bases, more than the circular"
                    + " sequence length (%d)";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE,
            description = "The segments making up a joined feature must appear in ascending coordinate order",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateJoinedLocationOrder(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        Map<String, List<GFF3Feature>> joinedFeaturesById = ValidationUtils.groupFeaturesById(
                annotation,
                feature -> feature.getId().isPresent(),
                joinedFeature -> joinedFeature.size() > 1 && !isExempt(joinedFeature));
        if (joinedFeaturesById.isEmpty()) {
            return;
        }

        Long circularSequenceLength = TopologyUtils.isCircularSequence(annotation.getAccession(), context)
                        && annotation.getSequenceRegion() != null
                ? annotation.getSequenceRegion().end()
                : null;
        for (Map.Entry<String, List<GFF3Feature>> joinedFeature : joinedFeaturesById.entrySet()) {
            String violation =
                    detectRuleViolation(joinedFeature.getKey(), joinedFeature.getValue(), circularSequenceLength);
            if (violation != null) {
                violations.add(violation);
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, OUT_OF_ORDER_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * The first segment starting before its predecessor in file order, or, on a circular sequence
     * ({@code circularSequenceLength} not null), a feature longer than the sequence; null where the
     * feature is in order.
     */
    private String detectRuleViolation(String id, List<GFF3Feature> joinedFeature, Long circularSequenceLength) {
        for (int i = 1; i < joinedFeature.size(); i++) {
            GFF3Feature previous = joinedFeature.get(i - 1);
            GFF3Feature current = joinedFeature.get(i);

            if (current.getStart() < previous.getStart()) {
                return violation(id, current, previous, circularSequenceLength);
            }
        }

        if (circularSequenceLength != null) {
            GFF3Feature first = joinedFeature.get(0);
            GFF3Feature last = joinedFeature.get(joinedFeature.size() - 1);
            long end =
                    joinedFeature.stream().mapToLong(GFF3Feature::getEnd).max().orElse(last.getEnd());
            long span = end - first.getStart() + 1;
            if (span > circularSequenceLength) {
                return LONGER_THAN_SEQUENCE_MESSAGE.formatted(
                        first.getName(),
                        id,
                        first.accession(),
                        ValidationUtils.getLocationString(first),
                        ValidationUtils.getLocationString(last),
                        span,
                        circularSequenceLength);
            }
        }
        return null;
    }

    private String violation(String id, GFF3Feature current, GFF3Feature previous, Long circularSequenceLength) {
        String violation = VIOLATION_MESSAGE.formatted(
                current.getName(),
                id,
                current.accession(),
                ValidationUtils.getLocationString(current),
                ValidationUtils.getLocationString(previous));
        if (circularSequenceLength == null) {
            return violation;
        }
        return violation
                + CIRCULAR_HINT.formatted(
                        ValidationUtils.getLocationString(current),
                        current.getStart() + circularSequenceLength,
                        current.getEnd() + circularSequenceLength);
    }

    /**
     * A trans-spliced feature orders its segments by biology, not by coordinate. One marked segment
     * exempts the whole feature, since the attribute need not be repeated on every segment.
     */
    private boolean isExempt(List<GFF3Feature> segments) {
        for (GFF3Feature segment : segments) {
            if (segment.hasAttribute(GFF3Attributes.TRANS_SPLICING)) {
                return true;
            }
        }
        return false;
    }
}
