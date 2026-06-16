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

import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(
        name = "FEATURE_LOCATION",
        description = "Validates that feature and sequence-region coordinates are within the sequence bounds")
public class FeatureLocationCheck implements Validation {

    private static final String RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH = "FEATURE_END_EXCEEDS_SEQUENCE_LENGTH";
    private static final String FEATURE_END_EXCEEDS_SEQUENCE_LENGTH =
            "The end position of the location \"%s\" is greater than the length of the sequence (\"%d\").";

    private static final String RULE_FEATURE_START_BELOW_ONE = "FEATURE_START_BELOW_ONE";
    private static final String FEATURE_START_BELOW_ONE = "The start position of the location \"%s\" is less than 1.";

    private static final String RULE_SEQUENCE_REGION_OUT_OF_BOUNDS = "SEQUENCE_REGION_OUT_OF_BOUNDS";
    private static final String SEQUENCE_REGION_START_OUT_OF_BOUNDS =
            "The start position of the sequence region (\"%d\") is less than 1.";
    private static final String SEQUENCE_REGION_END_OUT_OF_BOUNDS =
            "The end position of the sequence region (\"%d\") is greater than the length of the sequence (\"%d\").";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
            description = "Feature end position must not exceed the sequence length",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateFeatureEndWithinSequence(GFF3Annotation annotation, int line) throws ValidationException {
        Long lastBaseIndex = resolveSequenceLength(annotation.getAccession());
        if (lastBaseIndex == null) {
            return;
        }
        for (GFF3Feature feature : annotation.getFeatures()) {
            if (feature.getEnd() > lastBaseIndex) {
                String location = feature.getStart() + ".." + feature.getEnd();
                throw new ValidationException(
                        RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
                        line,
                        FEATURE_END_EXCEEDS_SEQUENCE_LENGTH.formatted(location, lastBaseIndex));
            }
        }
    }

    @ValidationMethod(
            rule = RULE_FEATURE_START_BELOW_ONE,
            description = "Feature start position must be at least 1",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateFeatureStartAboveZero(GFF3Annotation annotation, int line) throws ValidationException {
        Long lastBaseIndex = resolveSequenceLength(annotation.getAccession());
        if (lastBaseIndex == null) {
            return;
        }
        for (GFF3Feature feature : annotation.getFeatures()) {
            if (feature.getStart() < 1) {
                String location = feature.getStart() + ".." + feature.getEnd();
                throw new ValidationException(
                        RULE_FEATURE_START_BELOW_ONE, line, FEATURE_START_BELOW_ONE.formatted(location));
            }
        }
    }

    @ValidationMethod(
            rule = RULE_SEQUENCE_REGION_OUT_OF_BOUNDS,
            description = "Sequence region start and end positions must be within {1, sequenceLength}",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateSequenceRegionWithinSequence(GFF3Annotation annotation, int line) throws ValidationException {
        GFF3SequenceRegion sequenceRegion = annotation.getSequenceRegion();
        if (sequenceRegion == null) {
            return;
        }
        Long lastBaseIndex = resolveSequenceLength(annotation.getAccession());
        if (lastBaseIndex == null) {
            return;
        }
        if (sequenceRegion.start() < 1) {
            throw new ValidationException(
                    RULE_SEQUENCE_REGION_OUT_OF_BOUNDS,
                    line,
                    SEQUENCE_REGION_START_OUT_OF_BOUNDS.formatted(sequenceRegion.start()));
        }
        if (sequenceRegion.end() > lastBaseIndex) {
            throw new ValidationException(
                    RULE_SEQUENCE_REGION_OUT_OF_BOUNDS,
                    line,
                    SEQUENCE_REGION_END_OUT_OF_BOUNDS.formatted(sequenceRegion.end(), lastBaseIndex));
        }
    }

    private Long resolveSequenceLength(String seqId) {
        if (context.contains(SequenceLookup.class)) {
            SequenceLookup lookup = context.get(SequenceLookup.class);
            if (lookup != null) {
                try {
                    return lookup.getSequenceLength(seqId);
                } catch (Exception ex) {
                    throw new IllegalStateException("Unable to resolve sequence length for " + seqId, ex);
                }
            }
        }
        return null;
    }
}
