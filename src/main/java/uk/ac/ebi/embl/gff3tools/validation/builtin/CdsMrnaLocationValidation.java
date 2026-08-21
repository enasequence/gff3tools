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
 * Validates that a CDS explicitly paired with an mRNA has a location the mRNA can carry.
 *
 * <p>A coding region is only meaningful against the transcript it is translated from, so where the
 * two are explicitly paired - by a {@code Parent} reference in GFF3, or by a shared
 * {@code transcript_id} carried over from feature table format - their locations must agree. The
 * coding region must lie inside the joined mRNA location, and every internal boundary it has must
 * be a boundary the mRNA also has: a CDS may begin part-way into the exon that holds the start
 * codon and end part-way into the exon that holds the stop codon (the untranslated regions), but it
 * may not splice anywhere the transcript does not.
 *
 * <p>Runs at ANNOTATION level because both features are discontiguous: the segments of one spliced
 * feature are separate GFF3 lines sharing an {@code ID}, and neither location is knowable until
 * they are grouped back together.
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

        // The two ways a CDS names the mRNA it belongs to: the ID a Parent reference points at, and
        // the transcript_id both features carry when the annotation came from feature table format.
        Map<String, List<GFF3Feature>> mrnaById = new LinkedHashMap<>();
        Map<String, List<GFF3Feature>> mrnaByTranscriptId = new LinkedHashMap<>();
        for (List<GFF3Feature> segments : mrnaGroups.values()) {
            ValidationUtils.representativeOfFeatureGroup(segments)
                    .getId()
                    .ifPresent(id -> mrnaById.putIfAbsent(id, segments));
            transcriptId(segments).ifPresent(transcriptId -> mrnaByTranscriptId.putIfAbsent(transcriptId, segments));
        }

        Map<String, List<GFF3Feature>> exonsByParentId = exonsByParentId(annotation);

        List<String> violations = new ArrayList<>();
        for (List<GFF3Feature> cdsSegments :
                ValidationUtils.groupFeaturesById(annotation, this::isCds).values()) {
            List<GFF3Feature> mrnaSegments = findPairedMrna(cdsSegments, mrnaById, mrnaByTranscriptId);
            if (mrnaSegments == null || isExempt(cdsSegments) || isExempt(mrnaSegments)) {
                continue;
            }

            List<GFF3Feature> sortedCds = sortedByStart(cdsSegments);
            List<GFF3Feature> joinedMrna = joinedMrnaLocation(mrnaSegments, exonsByParentId);

            String reason = describeIncompatibility(sortedCds, joinedMrna);
            if (reason != null) {
                violations.add(VIOLATION_MESSAGE.formatted(
                        span(sortedCds), span(joinedMrna), sortedCds.get(0).accession(), reason));
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, INCOMPATIBLE_LOCATION_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * The mRNA a coding region is explicitly paired with, or null where it is paired with none.
     *
     * <p>A {@code Parent} reference is the explicit pairing where one exists; a shared
     * {@code transcript_id} is the pairing feature table format expresses instead. A {@code Parent}
     * naming something other than an mRNA - a gene, as the flat file conversion writes it - is not a
     * pairing with an mRNA, so the transcript_id is still consulted.
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
        // An ID or transcript_id shared across sequences pairs features that were never on the same
        // location to begin with, so there is nothing to compare.
        if (!Objects.equals(paired.get(0).accession(), cdsSegments.get(0).accession())) {
            return null;
        }
        return paired;
    }

    /**
     * The intervals that make up the joined mRNA location.
     *
     * <p>Segments sharing an ID are the joined location where the mRNA has more than one. A single
     * mRNA line carries no splicing of its own, and in that case canonical GFF3 spells the splicing
     * out in the exon features parented to it - those exons are then the transcript's real
     * intervals, and measuring a spliced CDS against the unsplit span instead would reject it for
     * boundaries the mRNA line never had.
     */
    private List<GFF3Feature> joinedMrnaLocation(
            List<GFF3Feature> mrnaSegments, Map<String, List<GFF3Feature>> exonsByParentId) {
        List<GFF3Feature> sorted = sortedByStart(mrnaSegments);
        if (sorted.size() > 1) {
            return sorted;
        }
        List<GFF3Feature> exons =
                sorted.get(0).getId().map(exonsByParentId::get).orElse(null);
        return exons == null || exons.isEmpty() ? sorted : sortedByStart(exons);
    }

    /**
     * Why the coding region does not fit the transcript, or null where it does.
     *
     * <p>The coding region is aligned to the mRNA at the segment holding its first base, and from
     * there each coding segment must sit in the mRNA segment beside it. Both locations are ordered
     * by coordinate rather than by reading direction, so the first and last segments here are the
     * ones free to stop short of the transcript - which end of the coding region each of them is
     * depends on the strand, and the rule is the same for both.
     */
    private String describeIncompatibility(List<GFF3Feature> cdsSegments, List<GFF3Feature> mrnaSegments) {
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
            // Only the outermost ends of the coding region may stop short of the transcript; every
            // boundary between two coding segments is a splice site the mRNA must share.
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
     * Locations that a CDS is allowed to deviate from its transcript on: a coding region assembled
     * across strands or sequences, one read through a slippage or another documented exception, and
     * one whose coordinates are already declared unreliable.
     */
    private boolean isExempt(List<GFF3Feature> segments) {
        return segments.stream()
                .anyMatch(segment -> segment.hasAttribute(GFF3Attributes.TRANS_SPLICING)
                        || segment.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE)
                        || segment.hasAttribute(GFF3Attributes.EXCEPTION)
                        || segment.hasAttribute(GFF3Attributes.ARTIFICIAL_LOCATION));
    }

    private Map<String, List<GFF3Feature>> exonsByParentId(GFF3Annotation annotation) {
        Map<String, List<GFF3Feature>> exons = new LinkedHashMap<>();
        for (GFF3Feature feature : annotation.getFeatures()) {
            if (feature == null || !isExon(feature)) {
                continue;
            }
            feature.getParentId().ifPresent(parentId -> exons.computeIfAbsent(parentId, key -> new ArrayList<>())
                    .add(feature));
        }
        return exons;
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
        return isSelfOrDescendantOf(feature, OntologyTerm.CDS.ID);
    }

    private boolean isMrna(GFF3Feature feature) {
        return isSelfOrDescendantOf(feature, OntologyTerm.MRNA.ID);
    }

    private boolean isExon(GFF3Feature feature) {
        return isSelfOrDescendantOf(feature, OntologyTerm.EXON.ID);
    }

    private boolean isSelfOrDescendantOf(GFF3Feature feature, String ontologyId) {
        if (feature == null) {
            return false;
        }
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        return ontologyClient
                .findTermByNameOrSynonym(feature.getName())
                .map(soId -> ontologyId.equals(soId) || ontologyClient.isSelfOrDescendantOf(soId, ontologyId))
                .orElse(false);
    }
}
