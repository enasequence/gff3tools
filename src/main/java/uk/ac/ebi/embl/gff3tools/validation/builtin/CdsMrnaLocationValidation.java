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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;

/**
 * A CDS and an mRNA are each stored as a list of intervals - one GFF3 line per interval, all lines
 * sharing an ID. Where a CDS is linked to an mRNA by {@code Parent} or {@code transcript_id}, its
 * intervals must line up against consecutive mRNA intervals with every shared edge matching
 * exactly, and only its outermost start and end may sit inside the mRNA's.
 */
@Gff3Validation(name = "CDS_MRNA_LOCATION", description = "Paired CDS and mRNA features must have compatible locations")
public class CdsMrnaLocationValidation implements Validation {

    private static final String RULE = "CDS_MRNA_LOCATION";

    private static final String INCOMPATIBLE_LOCATION_MESSAGE =
            "Paired CDS and mRNA features must have compatible locations. A coding region must lie "
                    + "within the joined location of the mRNA it is paired with and share every internal "
                    + "boundary with it:%s";

    private static final String VIOLATION_MESSAGE = "\nCDS %s paired with mRNA %s on accession \"%s\": %s";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE,
            description = "A CDS paired with an mRNA must be contained within the joined mRNA location "
                    + "and share its internal interval boundaries",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateCdsMrnaLocation(GFF3Annotation annotation, int line) throws ValidationException {
        Map<String, List<GFF3Feature>> mrnaGroups = ValidationUtils.groupFeaturesById(annotation, this::isMrna);
        if (mrnaGroups.isEmpty()) {
            return;
        }

        // The two ways a CDS names its mRNA: the ID a Parent points at, and the transcript_id both
        // carry when the annotation came from feature table format.
        Map<String, List<GFF3Feature>> mrnaById = new LinkedHashMap<>();
        Map<String, List<GFF3Feature>> mrnaByTranscriptId = new LinkedHashMap<>();
        for (List<GFF3Feature> segments : mrnaGroups.values()) {
            ValidationUtils.representativeOfFeatureGroup(segments)
                    .getId()
                    .ifPresent(id -> mrnaById.putIfAbsent(id, segments));
            transcriptId(segments).ifPresent(transcriptId -> mrnaByTranscriptId.putIfAbsent(transcriptId, segments));
        }

        List<String> violations = new ArrayList<>();
        for (List<GFF3Feature> cdsSegments :
                ValidationUtils.groupFeaturesById(annotation, this::isCds).values()) {
            List<GFF3Feature> mrnaSegments = findPairedMrna(cdsSegments, mrnaById, mrnaByTranscriptId);
            if (mrnaSegments == null || isExempt(cdsSegments) || isExempt(mrnaSegments)) {
                continue;
            }

            List<GFF3Feature> sortedCds = sortedByStart(cdsSegments);
            List<GFF3Feature> sortedMrna = sortedByStart(mrnaSegments);

            String reason = describeIncompatibility(sortedCds, sortedMrna);
            if (reason != null) {
                violations.add(VIOLATION_MESSAGE.formatted(
                        span(sortedCds), span(sortedMrna), sortedCds.get(0).accession(), reason));
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, INCOMPATIBLE_LOCATION_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * The mRNA a CDS is explicitly paired with, by {@code Parent} where that names one and by
     * {@code transcript_id} otherwise, or null where it is paired with none.
     *
     * <p>Only the parent {@code getParentId()} holds is considered.
     */
    private List<GFF3Feature> findPairedMrna(
            List<GFF3Feature> cdsSegments,
            Map<String, List<GFF3Feature>> mrnaById,
            Map<String, List<GFF3Feature>> mrnaByTranscriptId) {
        List<GFF3Feature> paired = cdsSegments.stream()
                .map(cds -> cds.getParentId().orElse(null))
                .filter(Objects::nonNull)
                .map(mrnaById::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() ->
                        transcriptId(cdsSegments).map(mrnaByTranscriptId::get).orElse(null));

        if (paired == null || paired.isEmpty()) {
            return null;
        }
        // An ID shared across sequences pairs features that were never on the same location, so
        // there is nothing to compare.
        if (!Objects.equals(paired.get(0).accession(), cdsSegments.get(0).accession())) {
            return null;
        }
        return paired;
    }

    /**
     * Why the CDS does not fit the transcript, or null where it does. The CDS is aligned to the mRNA
     * at the segment holding its first base, and from there each segment must sit in the one beside
     * it.
     *
     * <p>Both are ordered by coordinate rather than reading direction, so the outermost segments are
     * the ones free to stop short whichever strand the feature is on.
     */
    private String describeIncompatibility(List<GFF3Feature> cdsSegments, List<GFF3Feature> mrnaSegments) {
        if (mrnaSegments.size() == 1) {
            return describeContainment(cdsSegments, mrnaSegments.get(0));
        }

        int offset = indexOfContainingSegment(cdsSegments.get(0), mrnaSegments);
        if (offset < 0) {
            return "the coding segment %s is not contained within any segment of the mRNA"
                    .formatted(location(cdsSegments.get(0)));
        }
        if (offset + cdsSegments.size() > mrnaSegments.size()) {
            return "the coding region has %d segments from %s onwards, more than the %d the mRNA has there"
                    .formatted(cdsSegments.size(), location(mrnaSegments.get(offset)), mrnaSegments.size() - offset);
        }

        for (int i = 0; i < cdsSegments.size(); i++) {
            GFF3Feature cds = cdsSegments.get(i);
            GFF3Feature mrna = mrnaSegments.get(offset + i);

            if (!isContainedWithin(cds, mrna)) {
                return "the coding segment %s is not contained within the mRNA segment %s"
                        .formatted(location(cds), location(mrna));
            }
            // Every boundary between two CDS segments is a splice site the mRNA must share.
            if (i > 0 && cds.getStart() != mrna.getStart()) {
                return "the coding segment %s does not start where the mRNA segment %s starts"
                        .formatted(location(cds), location(mrna));
            }
            if (i < cdsSegments.size() - 1 && cds.getEnd() != mrna.getEnd()) {
                return "the coding segment %s does not end where the mRNA segment %s ends"
                        .formatted(location(cds), location(mrna));
            }
        }
        return null;
    }

    /** Every coding segment must fall inside the one interval the mRNA covers. */
    private String describeContainment(List<GFF3Feature> cdsSegments, GFF3Feature mrna) {
        for (GFF3Feature cds : cdsSegments) {
            if (!isContainedWithin(cds, mrna)) {
                return "the coding segment %s is not contained within the mRNA %s"
                        .formatted(location(cds), location(mrna));
            }
        }
        return null;
    }

    private int indexOfContainingSegment(GFF3Feature cds, List<GFF3Feature> mrnaSegments) {
        for (int i = 0; i < mrnaSegments.size(); i++) {
            if (isContainedWithin(cds, mrnaSegments.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isContainedWithin(GFF3Feature inner, GFF3Feature outer) {
        return inner.getStart() >= outer.getStart() && inner.getEnd() <= outer.getEnd();
    }

    /**
     * Cases where a CDS is allowed to deviate from its transcript: assembled across strands or
     * sequences, read through a slippage or other documented exception, or already declared
     * unreliable.
     */
    private boolean isExempt(List<GFF3Feature> segments) {
        return segments.stream()
                .anyMatch(segment -> segment.hasAttribute(GFF3Attributes.TRANS_SPLICING)
                        || segment.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE)
                        || segment.hasAttribute(GFF3Attributes.EXCEPTION)
                        || segment.hasAttribute(GFF3Attributes.ARTIFICIAL_LOCATION));
    }

    private Optional<String> transcriptId(List<GFF3Feature> segments) {
        return segments.stream()
                .map(segment ->
                        segment.getAttribute(GFF3Attributes.TRANSCRIPT_ID).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private List<GFF3Feature> sortedByStart(List<GFF3Feature> segments) {
        List<GFF3Feature> sorted = new ArrayList<>(segments);
        sorted.sort(Comparator.comparingLong(GFF3Feature::getStart));
        return sorted;
    }

    private String location(GFF3Feature feature) {
        return "%d..%d".formatted(feature.getStart(), feature.getEnd());
    }

    /** The outermost coordinates a group of segments covers, for reporting the feature as a whole. */
    private String span(List<GFF3Feature> sortedSegments) {
        long end = sortedSegments.stream()
                .mapToLong(GFF3Feature::getEnd)
                .max()
                .orElse(sortedSegments.get(0).getEnd());
        return "%d..%d".formatted(sortedSegments.get(0).getStart(), end);
    }

    private boolean isCds(GFF3Feature feature) {
        return isSoTerm(feature, OntologyTerm.CDS.ID);
    }

    private boolean isMrna(GFF3Feature feature) {
        return isSoTerm(feature, OntologyTerm.MRNA.ID);
    }

    /**
     * Whether the feature is typed as exactly this SO term. Descendants are not accepted because
     * none of those of CDS or mRNA map to an INSDC feature, so admitting them would only widen the
     * rule onto types the conversion cannot represent.
     */
    private boolean isSoTerm(GFF3Feature feature, String ontologyId) {
        if (feature == null) {
            return false;
        }
        return context.get(OntologyClient.class)
                .findTermByNameOrSynonym(feature.getName())
                .filter(ontologyId::equals)
                .isPresent();
    }
}
