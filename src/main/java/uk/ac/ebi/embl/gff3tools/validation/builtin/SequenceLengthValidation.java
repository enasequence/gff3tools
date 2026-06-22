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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

@Gff3Validation(
        name = "SEQUENCE_LENGTH",
        description = "Validates sequence length constraints and ##sequence-region consistency")
public class SequenceLengthValidation implements Validation {

    private static final String RULE_SEQUENCE_REGION_OUT_OF_BOUNDS = "SEQUENCE_REGION_OUT_OF_BOUNDS";
    private static final String RULE_SEQUENCE_TOO_SHORT = "SEQUENCE_TOO_SHORT";
    private static final String RULE_LNCRNA_TOO_SHORT = "LNCRNA_TOO_SHORT";

    private static final Integer UNIVERSAL_MINIMUM_SEQUENCE_LENGTH = 100;
    private static final Integer LNCRNA_MINIMUM_SEQUENCE_LENGTH = 200;

    private static final String MESSAGE_SEQUENCE_REGION_START_OUT_OF_BOUNDS =
            "The start position of the sequence region (\"%d\") is not equal to 1.";
    private static final String MESSAGE_SEQUENCE_REGION_END_OUT_OF_BOUNDS =
            "The end position of the sequence region (\"%d\") is not equal to the length of the sequence (\"%d\").";
    private static final String MESSAGE_SEQUENCE_TOO_SHORT =
            "Sequence does not fall under the accepted short sequence categories (ancient DNA, non-coding RNA, microsatellites"
                    + " or complete exons) and therefore cannot be accepted for submission into ENA's EMBL-Bank."
                    + " Exceptions require the submitter to demonstrate that a peer-reviewed journal has accepted"
                    + " a manuscript confirming the relevance of the short sequences to the scientific community."
                    + " Please contact ENA helpdesk if you can demonstrate this, or if your sequence belongs to the"
                    + " 'ancient DNA' or 'complete exon' category.";
    private static final String MESSAGE_LNCRNA_TOO_SHORT =
            "lncRNA sequences usually have a length greater than 200bp. Please check that you are certain about this annotation.";

    // TODO: WGS minimum 1000 nt (3.3) — requires dataclass context, no defined source in GFF3 submissions yet

    @InjectContext
    private ValidationContext context;

    private final Map<String, Long> sequenceLengthCache = new HashMap<>();

    @ValidationMethod(
            rule = RULE_SEQUENCE_REGION_OUT_OF_BOUNDS,
            description = "Sequence region start and end positions must be exactly {1, sequenceLength}",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateSequenceRegionAgainstSequence(GFF3Annotation annotation, int line) throws ValidationException {
        GFF3SequenceRegion region = annotation.getSequenceRegion();
        if (region == null) {
            return;
        }
        Long firstBaseIndex = 1L;
        Long lastBaseIndex = resolveSequenceLength(annotation.getAccession());
        if (lastBaseIndex == null) {
            return;
        }
        if (!firstBaseIndex.equals(annotation.getSequenceRegion().start())) {
            throw new ValidationException(
                    RULE_SEQUENCE_REGION_OUT_OF_BOUNDS,
                    line,
                    MESSAGE_SEQUENCE_REGION_START_OUT_OF_BOUNDS.formatted(region.start()));
        }
        if (!lastBaseIndex.equals(annotation.getSequenceRegion().end())) {
            throw new ValidationException(
                    RULE_SEQUENCE_REGION_OUT_OF_BOUNDS,
                    line,
                    MESSAGE_SEQUENCE_REGION_END_OUT_OF_BOUNDS.formatted(region.end(), lastBaseIndex));
        }
    }

    @ValidationMethod(
            rule = RULE_SEQUENCE_TOO_SHORT,
            description = "Sequence must be at least 100 nt unless it is an ncRNA or has microsatellite attribute",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateMinimumLength(GFF3Annotation annotation, int line) throws ValidationException {
        Long length = resolveSequenceLength(annotation.getAccession());
        if (length == null) {
            return;
        }
        if (length < UNIVERSAL_MINIMUM_SEQUENCE_LENGTH && !hasMinimumLengthException(annotation)) {
            throw new ValidationException(RULE_SEQUENCE_TOO_SHORT, line, MESSAGE_SEQUENCE_TOO_SHORT);
        }
    }

    @ValidationMethod(
            rule = RULE_LNCRNA_TOO_SHORT,
            description = "long non coding RNA (lncRNA) sequences should be at least 200 nt",
            type = ValidationType.ANNOTATION,
            severity = RuleSeverity.WARN,
            priority = ValidationPriority.NORMAL)
    public void validateLncRnaLength(GFF3Annotation annotation, int line) throws ValidationException {
        if (findLncRnaFeature(annotation).isEmpty()) {
            return;
        }
        Long length = resolveSequenceLength(annotation.getAccession());
        if (length != null && length < LNCRNA_MINIMUM_SEQUENCE_LENGTH) {
            throw new ValidationException(RULE_LNCRNA_TOO_SHORT, line, MESSAGE_LNCRNA_TOO_SHORT);
        }
    }

    private Optional<GFF3Feature> findLncRnaFeature(GFF3Annotation annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        for (GFF3Feature feature : annotation.getFeatures()) {
            Optional<String> soId = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soId.isPresent() && ontologyClient.isSelfOrDescendantOf(soId.get(), OntologyTerm.LNCRNA.ID)) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }

    private Long resolveSequenceLength(String seqId) {
        if (sequenceLengthCache.containsKey(seqId)) {
            return sequenceLengthCache.get(seqId);
        }
        if (context.contains(SequenceLookup.class)) {
            SequenceLookup lookup = context.get(SequenceLookup.class);
            if (lookup != null) {
                try {
                    Long length = lookup.getSequenceLength(seqId, SequenceRangeOption.WITHOUT_EDGE_N_BASES);
                    sequenceLengthCache.put(seqId, length);
                    return length;
                } catch (Exception ex) {
                    throw new IllegalStateException("Unable to resolve sequence length for " + seqId, ex);
                }
            }
        }
        return null;
    }

    private boolean hasMinimumLengthException(GFF3Annotation annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        for (GFF3Feature feature : annotation.getFeatures()) {
            Optional<String> soId = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soId.isPresent()
                    && (ontologyClient.isSelfOrDescendantOf(soId.get(), OntologyTerm.NCRNA_GENE.ID)
                            || ontologyClient.isSelfOrDescendantOf(soId.get(), OntologyTerm.NCRNA.ID)
                            || ontologyClient.isSelfOrDescendantOf(soId.get(), OntologyTerm.MICROSATELLITE.ID))) {
                return true;
            }
        }
        return false;
    }
}
