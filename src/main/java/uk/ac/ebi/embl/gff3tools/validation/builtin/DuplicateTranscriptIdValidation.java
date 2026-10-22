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
import java.util.HashMap;
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
 * A transcript_id names one transcript across the whole genome annotation. In feature table format
 * it is how a CDS is paired with its mRNA, so a value may be shared by one mRNA and one CDS, and by
 * nothing else of either type.
 *
 * <p>The values seen are kept across annotations until the file is finished, since a duplicate may
 * sit on another sequence or behind a {@code ###} directive.
 */
@Gff3Validation(
        name = "DUPLICATE_TRANSCRIPT_ID",
        description = "A transcript_id may be used by at most one mRNA and one CDS in the genome")
public class DuplicateTranscriptIdValidation implements Validation {

    private static final String RULE = "DUPLICATE_TRANSCRIPT_ID";

    private static final String DUPLICATE_MESSAGE =
            "Transcript_id values must be unique within the full genome annotation, shared only by an "
                    + "mRNA and the CDS paired with it:%s";

    private static final String VIOLATION_MESSAGE = "\ntranscript_id \"%s\" is used by %s %s and by %s %s";

    @InjectContext
    private ValidationContext context;

    private final Map<String, SeenFeature> mrnaByTranscriptId = new HashMap<>();
    private final Map<String, SeenFeature> cdsByTranscriptId = new HashMap<>();

    @ValidationMethod(
            rule = RULE,
            description = "A transcript_id may be used by at most one mRNA and one CDS in the genome",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateTranscriptIdUniqueness(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        for (List<GFF3Feature> segments :
                ValidationUtils.groupFeaturesById(annotation, this::isMrna).values()) {
            claimTranscriptId(mrnaByTranscriptId, "mRNA", segments, violations);
        }
        for (List<GFF3Feature> segments :
                ValidationUtils.groupFeaturesById(annotation, this::isCds).values()) {
            claimTranscriptId(cdsByTranscriptId, "CDS", segments, violations);
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, DUPLICATE_MESSAGE.formatted(String.join("", violations)));
        }
    }

    @ExitMethod
    public void clear() {
        mrnaByTranscriptId.clear();
        cdsByTranscriptId.clear();
    }

    /**
     * Records the feature as the holder of its transcript_id, or reports it where another feature of
     * the same type already holds that value.
     */
    private void claimTranscriptId(
            Map<String, SeenFeature> seen, String type, List<GFF3Feature> segments, List<String> violations) {
        Optional<String> transcriptId = findTranscriptId(segments);
        if (transcriptId.isEmpty()) {
            return;
        }

        SeenFeature feature = SeenFeature.of(segments);
        SeenFeature holder = seen.putIfAbsent(transcriptId.get(), feature);
        // A feature whose lines are split across annotations meets its own earlier claim
        if (holder != null && !holder.isSameFeatureAs(feature)) {
            violations.add(VIOLATION_MESSAGE.formatted(transcriptId.get(), type, holder, type, feature));
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

    /** Just enough of a feature to recognise it again and to report it, kept for the whole file. */
    private record SeenFeature(String accession, String groupKey, String id, long start, long end, int line) {

        static SeenFeature of(List<GFF3Feature> segments) {
            GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);
            long end = segments.stream().mapToLong(GFF3Feature::getEnd).max().orElse(representative.getEnd());
            return new SeenFeature(
                    representative.accession(),
                    ValidationUtils.featureGroupKey(representative),
                    representative.getId().orElse("<no ID>"),
                    representative.getStart(),
                    end,
                    representative.getLine());
        }

        boolean isSameFeatureAs(SeenFeature other) {
            return accession.equals(other.accession) && groupKey.equals(other.groupKey);
        }

        @Override
        public String toString() {
            String location = "\"%s\" at %s:%d..%d".formatted(id, accession, start, end);
            return line >= 0 ? location + " (line %d)".formatted(line) : location;
        }
    }
}
