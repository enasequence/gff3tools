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
import java.util.Objects;
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
 * The overlap rules of the INSDC Annotation Minimum Specification v1.0, section b.iv.
 *
 * <ol>
 *   <li>Intervals comprising a feature's location must not overlap, unless the feature carries
 *       {@code ribosomal_slippage}
 *   <li>Ribosomal RNA features must not overlap CDS features or other rRNAs.
 *   <li>tRNA features must not be completely contained within the exons of a CDS.
 * </ol>
 */
@Gff3Validation(
        name = "FEATURE_OVERLAP",
        description = "INSDC Annotation Minimum Specification b.iv: feature intervals must not overlap"
                + " where the specification forbids it")
public class FeatureOverlapValidation implements Validation {

    private static final String INTERVAL_OVERLAP_RULE = "FEATURE_INTERVAL_OVERLAP";
    private static final String RRNA_OVERLAP_RULE = "RRNA_FEATURE_OVERLAP";
    private static final String TRNA_WITHIN_CDS_RULE = "TRNA_WITHIN_CDS_EXON";

    private static final String INTERVAL_OVERLAP_MESSAGE =
            "The intervals comprising a feature's location must not overlap. Where an overlap is a "
                    + "programmed frameshift, declare it with the ribosomal_slippage attribute:%s";
    private static final String INTERVAL_OVERLAP_VIOLATION =
            "\n%s %s on accession \"%s\": interval %s overlaps interval %s";

    private static final String RRNA_OVERLAP_MESSAGE =
            "Ribosomal RNA features must not overlap CDS features or other rRNA features:%s";
    private static final String RRNA_OVERLAP_VIOLATION =
            "\nrRNA %s overlaps %s %s on accession \"%s\": interval %s overlaps interval %s";

    private static final String TRNA_WITHIN_CDS_MESSAGE =
            "tRNA features must not be completely contained within the exons of a CDS feature:%s";
    private static final String TRNA_WITHIN_CDS_VIOLATION =
            "\ntRNA %s on accession \"%s\" is completely contained within the exons of the CDS %s";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = INTERVAL_OVERLAP_RULE,
            description = "The intervals comprising one feature's location must not overlap each other",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateFeatureIntervalOverlap(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        for (List<GFF3Feature> segments : ValidationUtils.groupFeaturesById(
                        annotation, feature -> feature.getId().isPresent())
                .values()) {
            if (segments.size() < 2 || hasRibosomalSlippage(segments)) {
                continue;
            }
            GFF3Feature[] overlapping = firstOverlappingPair(segments, segments, true);
            if (overlapping != null) {
                GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);
                violations.add(INTERVAL_OVERLAP_VIOLATION.formatted(
                        representative.getName(),
                        formatMultipleFeatureSpan(segments),
                        representative.accession(),
                        formatFeatureSpan(overlapping[0]),
                        formatFeatureSpan(overlapping[1])));
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, INTERVAL_OVERLAP_MESSAGE.formatted(String.join("", violations)));
        }
    }

    @ValidationMethod(
            rule = RRNA_OVERLAP_RULE,
            description = "Ribosomal RNA features must not overlap CDS features or other rRNA features",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateRrnaOverlap(GFF3Annotation annotation, int line) throws ValidationException {
        List<List<GFF3Feature>> rrnas = groupFeaturesById(annotation, OntologyTerm.RRNA.ID);
        if (rrnas.isEmpty()) {
            return;
        }
        List<List<GFF3Feature>> codingRegions = groupFeaturesById(annotation, OntologyTerm.CDS.ID);

        List<String> violations = new ArrayList<>();
        for (int i = 0; i < rrnas.size(); i++) {
            List<GFF3Feature> rrna = rrnas.get(i);

            for (List<GFF3Feature> codingRegion : codingRegions) {
                collectOverlap(rrna, codingRegion, "CDS", violations);
            }
            // Each pair of rRNAs is compared once, from the earlier of the two.
            for (int j = i + 1; j < rrnas.size(); j++) {
                collectOverlap(rrna, rrnas.get(j), "rRNA", violations);
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, RRNA_OVERLAP_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * <p>A tRNA is reported only when it is completely contained, which the specification states as
     * fully overlapping: every one of its intervals has to sit inside one interval of the same CDS.
     * A tRNA that merely runs into a coding region, or that spans an intron and so leaves the exons,
     * is left alone.
     */
    @ValidationMethod(
            rule = TRNA_WITHIN_CDS_RULE,
            description = "tRNA features must not be completely contained within the exons of a CDS feature",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateTrnaWithinCdsExon(GFF3Annotation annotation, int line) throws ValidationException {
        List<List<GFF3Feature>> trnas = groupFeaturesById(annotation, OntologyTerm.TRNA.ID);
        if (trnas.isEmpty()) {
            return;
        }
        List<List<GFF3Feature>> codingRegions = groupFeaturesById(annotation, OntologyTerm.CDS.ID);

        List<String> violations = new ArrayList<>();
        for (List<GFF3Feature> trna : trnas) {
            for (List<GFF3Feature> codingRegion : codingRegions) {
                if (isContainedWithinExons(trna, codingRegion)) {
                    violations.add(TRNA_WITHIN_CDS_VIOLATION.formatted(
                            formatMultipleFeatureSpan(trna),
                            trna.get(0).accession(),
                            formatMultipleFeatureSpan(codingRegion)));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, TRNA_WITHIN_CDS_MESSAGE.formatted(String.join("", violations)));
        }
    }

    private void collectOverlap(
            List<GFF3Feature> rrna, List<GFF3Feature> other, String otherLabel, List<String> violations) {
        GFF3Feature[] overlapping = firstOverlappingPair(rrna, other, false);
        if (overlapping == null) {
            return;
        }
        violations.add(RRNA_OVERLAP_VIOLATION.formatted(
                formatMultipleFeatureSpan(rrna),
                otherLabel,
                formatMultipleFeatureSpan(other),
                overlapping[0].accession(),
                formatFeatureSpan(overlapping[0]),
                formatFeatureSpan(overlapping[1])));
    }

    private GFF3Feature[] firstOverlappingPair(List<GFF3Feature> left, List<GFF3Feature> right, boolean sameGroup) {
        for (int i = 0; i < left.size(); i++) {
            for (int j = sameGroup ? i + 1 : 0; j < right.size(); j++) {
                if (overlaps(left.get(i), right.get(j))) {
                    return new GFF3Feature[] {left.get(i), right.get(j)};
                }
            }
        }
        return null;
    }

    private boolean overlaps(GFF3Feature left, GFF3Feature right) {
        return Objects.equals(left.accession(), right.accession())
                && left.getStart() <= right.getEnd()
                && right.getStart() <= left.getEnd();
    }

    private boolean isContainedWithinExons(List<GFF3Feature> segments, List<GFF3Feature> exons) {
        return segments.stream().allMatch(segment -> exons.stream().anyMatch(exon -> contains(exon, segment)));
    }

    private boolean contains(GFF3Feature outer, GFF3Feature inner) {
        return Objects.equals(outer.accession(), inner.accession())
                && inner.getStart() >= outer.getStart()
                && inner.getEnd() <= outer.getEnd();
    }

    private boolean hasRibosomalSlippage(List<GFF3Feature> segments) {
        return segments.stream().anyMatch(segment -> segment.hasAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE));
    }

    private List<List<GFF3Feature>> groupFeaturesById(GFF3Annotation annotation, String ontologyId) {
        return new ArrayList<>(
                ValidationUtils.groupFeaturesById(annotation, feature -> isSelfOrDescendantOf(feature, ontologyId))
                        .values());
    }

    private boolean isSelfOrDescendantOf(GFF3Feature feature, String ontologyId) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        return ontologyClient
                .findTermByNameOrSynonym(feature.getName())
                .map(soId -> ontologyClient.isSelfOrDescendantOf(soId, ontologyId))
                .orElse(false);
    }

    private String formatFeatureSpan(GFF3Feature feature) {
        return feature.getStart() + ".." + feature.getEnd();
    }

    private String formatMultipleFeatureSpan(List<GFF3Feature> segments) {
        long start = segments.stream().mapToLong(GFF3Feature::getStart).min().orElseThrow();
        long end = segments.stream().mapToLong(GFF3Feature::getEnd).max().orElseThrow();
        return start + ".." + end;
    }
}
