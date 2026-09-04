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
 * {@code GFF3Mapper} builds the flat file {@code join()} by appending same-ID segments in the order
 * their lines appear, so segments listed out of coordinate order produce a backwards {@code join()}.
 * Overlap and transcript alignment are separate rules.
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

        Map<String, List<GFF3Feature>> joinedFeaturesById = ValidationUtils.groupFeaturesById(
                annotation,
                feature -> feature.getId().isPresent(),
                joinedFeature -> joinedFeature.size() > 1 && !isExempt(joinedFeature));
        if (joinedFeaturesById.isEmpty()) {
            return;
        }

        boolean circular = isCircularSequence(annotation.getAccession());
        for (List<GFF3Feature> joinedFeature : joinedFeaturesById.values()) {
            String violation = detectRuleViolation(joinedFeature, circular);
            if (violation != null) {
                violations.add(violation);
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, OUT_OF_ORDER_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * The first segment starting before its predecessor in file order, or null where the feature is
     * in order. A circular sequence allows one step back, which is the feature crossing the origin.
     */
    private String detectRuleViolation(List<GFF3Feature> joinedFeature, boolean circular) {
        int allowance = circular ? 1 : 0;

        for (int i = 1; i < joinedFeature.size(); i++) {
            GFF3Feature previous = joinedFeature.get(i - 1);
            GFF3Feature current = joinedFeature.get(i);

            if (current.getStart() >= previous.getStart()) {
                continue;
            }
            if (allowance > 0) {
                allowance--;
                continue;
            }
            return VIOLATION_MESSAGE.formatted(
                    current.getName(),
                    identify(joinedFeature),
                    current.accession(),
                    location(current),
                    location(previous));
        }
        return null;
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

    /**
     * Topology needs a registered FASTA header source; absent or unrecognised counts as non-circular,
     * since circular is always declared explicitly.
     */
    private boolean isCircularSequence(String accession) {
        if (!context.contains(FastaHeaderProvider.class)) {
            return false;
        }
        return context.get(FastaHeaderProvider.class)
                .getHeader(accession)
                .map(FastaHeader::getTopology)
                // Canonicalise: FastaHeaderNormalisationFix runs after this annotation is validated.
                .flatMap(topology ->
                        ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, topology))
                .flatMap(ControlledVocabularyUtils.Topology::fromValue)
                .map(ControlledVocabularyUtils.Topology.CIRCULAR::equals)
                .orElse(false);
    }

    /** Names the feature in a message by ID, falling back to its first segment's location. */
    private String identify(List<GFF3Feature> segments) {
        GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);
        return representative.getId().map("\"%s\""::formatted).orElseGet(() -> location(representative));
    }

    private String location(GFF3Feature feature) {
        return "%d..%d".formatted(feature.getStart(), feature.getEnd());
    }
}
