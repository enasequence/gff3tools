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
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;

/**
 * A joined feature is stored as one GFF3 line per interval, all lines sharing an ID, and the flat
 * file {@code join()} is built by appending those intervals in the order the lines appear. The
 * intervals must therefore already be listed in ascending coordinate order, or the conversion
 * writes a {@code join()} that steps backwards.
 *
 * <p>Only the order of the intervals is judged here. Whether they overlap, and whether they line up
 * against the transcript they belong to, are separate rules.
 */
@Gff3Validation(
        name = "JOINED_LOCATION_ORDER",
        description = "The segments of a joined feature must be listed in ascending coordinate order")
public class JoinedLocationOrderValidation implements Validation {

    private static final String RULE = "JOINED_LOCATION_ORDER";

    private static final String OUT_OF_ORDER_MESSAGE =
            "Joined features must list their segments in ascending coordinate order:%s";

    private static final String VIOLATION_MESSAGE =
            "\nFeature %s %s on accession \"%s\": segment %s starts before the preceding segment %s";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE,
            description = "The segments making up a joined feature must appear in ascending coordinate order",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateJoinedLocationOrder(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        for (List<GFF3Feature> segments :
                ValidationUtils.groupFeaturesById(annotation, feature -> true).values()) {
            if (segments.size() < 2 || isExempt(segments) || spansMultipleAccessions(segments)) {
                continue;
            }
            String violation = describeDisorder(segments);
            if (violation != null) {
                violations.add(violation);
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, OUT_OF_ORDER_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * The first segment that starts before the one listed ahead of it, or null where the feature is
     * in order.
     *
     * <p>The segments are walked as the file lists them rather than sorted, because it is that order
     * the {@code join()} is built from. A feature crossing the origin of a circular sequence steps
     * back exactly once and legitimately, so on a circular sequence the first step back is allowed.
     */
    private String describeDisorder(List<GFF3Feature> segments) {
        int allowance = isCircularSequence(segments.get(0).accession()) ? 1 : 0;

        for (int i = 1; i < segments.size(); i++) {
            GFF3Feature previous = segments.get(i - 1);
            GFF3Feature current = segments.get(i);

            if (current.getStart() >= previous.getStart()) {
                continue;
            }
            if (allowance > 0) {
                allowance--;
                continue;
            }
            return VIOLATION_MESSAGE.formatted(
                    current.getName(), identify(segments), current.accession(), location(current), location(previous));
        }
        return null;
    }

    /**
     * A trans-spliced feature is assembled from intervals that need not follow the coordinate order
     * of the sequence, so the order it lists them in carries meaning and is left alone.
     */
    private boolean isExempt(List<GFF3Feature> segments) {
        return segments.stream().anyMatch(segment -> segment.hasAttribute(GFF3Attributes.TRANS_SPLICING));
    }

    /**
     * Segments sharing an ID across sequences were never on one coordinate system, so there is no
     * order to judge them against.
     */
    private boolean spansMultipleAccessions(List<GFF3Feature> segments) {
        return segments.stream().map(GFF3Feature::accession).distinct().count() > 1;
    }

    /**
     * Topology is only known when a FASTA header source is registered for the run. An absent or
     * unrecognised topology is treated as non-circular: circular is always explicitly declared, and
     * a missing mandatory topology is reported by {@link FastaHeaderFormatValidation}.
     */
    private boolean isCircularSequence(String accession) {
        if (!context.contains(FastaHeaderProvider.class)) {
            return false;
        }
        return context.get(FastaHeaderProvider.class)
                .getHeader(accession)
                .map(FastaHeader::getTopology)
                // Canonicalise rather than matching the raw value: FastaHeaderNormalisationFix is
                // annotation-scoped and runs only after this annotation's features are validated.
                .flatMap(topology ->
                        ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, topology))
                .flatMap(ControlledVocabularyUtils.Topology::fromValue)
                .map(ControlledVocabularyUtils.Topology.CIRCULAR::equals)
                .orElse(false);
    }

    /** How the feature as a whole is named in a message: its ID, or its first segment's location. */
    private String identify(List<GFF3Feature> segments) {
        GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);
        return representative.getId().map("\"%s\""::formatted).orElseGet(() -> location(representative));
    }

    private String location(GFF3Feature feature) {
        return "%d..%d".formatted(feature.getStart(), feature.getEnd());
    }
}
