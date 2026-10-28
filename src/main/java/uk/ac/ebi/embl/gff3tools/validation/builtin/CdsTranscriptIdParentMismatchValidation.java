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
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

/**
 * A CDS names its mRNA twice where it carries both a {@code Parent} and a transcript_id. GFF3 pairs
 * it by the Parent and a flat file by the transcript_id, so the two must agree: a CDS whose
 * transcript_id differs from its parent mRNA's would be paired with a different transcript, or
 * none, once converted.
 *
 * <p>Nothing is compared where either feature carries no transcript_id. A flat file cannot produce
 * the mismatch, as its conversion always makes a CDS's parent the gene.
 */
@Gff3Validation(
        name = "CDS_TRANSCRIPT_ID_PARENT_MISMATCH",
        description = "A CDS's transcript_id must match the transcript_id of its parent mRNA")
public class CdsTranscriptIdParentMismatchValidation implements Validation {

    private static final String RULE = "CDS_TRANSCRIPT_ID_PARENT_MISMATCH";

    private static final String MISMATCH_MESSAGE =
            "A CDS must carry the same transcript_id as the mRNA its Parent names, or a flat file pairs it "
                    + "with a different transcript:%s";

    private static final String VIOLATION_MESSAGE =
            "\nCDS \"%s\" at %s has transcript_id \"%s\", but its parent mRNA \"%s\" has \"%s\"";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE,
            description = "A CDS's transcript_id must match the transcript_id of its parent mRNA",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateCdsTranscriptIdMatchesParent(GFF3Annotation annotation, int line) throws ValidationException {
        Map<String, List<GFF3Feature>> mrnaById = ValidationUtils.groupFeaturesById(annotation, this::isMrna);
        if (mrnaById.isEmpty()) {
            return;
        }

        List<String> violations = new ArrayList<>();
        for (List<GFF3Feature> cdsSegments :
                ValidationUtils.groupFeaturesById(annotation, this::isCds).values()) {
            GFF3Feature cds = ValidationUtils.representativeOfFeatureGroup(cdsSegments);
            // Only the parent getParentId() holds is considered, as in CDS_MRNA_LOCATION
            String parentId = cdsSegments.get(0).getParentId().orElse(null);
            List<GFF3Feature> mrnaSegments = parentId == null ? null : mrnaById.get(parentId);
            if (mrnaSegments == null) {
                continue;
            }

            Optional<String> cdsTranscriptId = findTranscriptId(cdsSegments);
            Optional<String> mrnaTranscriptId = findTranscriptId(mrnaSegments);
            if (cdsTranscriptId.isPresent()
                    && mrnaTranscriptId.isPresent()
                    && !cdsTranscriptId.get().equals(mrnaTranscriptId.get())) {
                violations.add(VIOLATION_MESSAGE.formatted(
                        cds.getId().orElse("<no ID>"),
                        describeLocation(cds, cdsSegments),
                        cdsTranscriptId.get(),
                        parentId,
                        mrnaTranscriptId.get()));
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, MISMATCH_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /** The first transcript_id carried by any line of the feature. */
    private Optional<String> findTranscriptId(List<GFF3Feature> segments) {
        return segments.stream()
                .map(segment ->
                        segment.getAttribute(GFF3Attributes.TRANSCRIPT_ID).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
    }

    /** The span the feature covers, with the line of its first segment where it was read from a file. */
    private String describeLocation(GFF3Feature representative, List<GFF3Feature> segments) {
        long end = segments.stream().mapToLong(GFF3Feature::getEnd).max().orElse(representative.getEnd());
        String location = "%s:%d..%d".formatted(representative.accession(), representative.getStart(), end);
        return representative.getLine() >= 0 ? location + " (line %d)".formatted(representative.getLine()) : location;
    }

    private boolean isCds(GFF3Feature feature) {
        return isSoTerm(feature, OntologyTerm.CDS.ID);
    }

    private boolean isMrna(GFF3Feature feature) {
        return isSoTerm(feature, OntologyTerm.MRNA.ID);
    }

    /**
     * Whether the feature is typed as exactly this SO term, by name or by accession. Descendants are
     * not accepted, as in {@code CDS_MRNA_LOCATION}: the pairing is between an mRNA and a CDS only.
     */
    private boolean isSoTerm(GFF3Feature feature, String ontologyId) {
        if (feature == null) {
            return false;
        }
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        String name = feature.getName();
        Optional<String> termId = ontologyClient.isValidOntologyId(name)
                ? Optional.of(name)
                : ontologyClient.findTermByNameOrSynonym(name);
        return termId.filter(ontologyId::equals).isPresent();
    }
}
