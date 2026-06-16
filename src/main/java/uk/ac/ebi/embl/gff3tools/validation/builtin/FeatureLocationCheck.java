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
        description = "Validates that feature end coordinates do not exceed the sequence length")
public class FeatureLocationCheck implements Validation {

    private static final String RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH = "FEATURE_END_EXCEEDS_SEQUENCE_LENGTH";
    private static final String FEATURE_END_EXCEEDS_SEQUENCE_LENGTH =
            "The end position of the location \"%s\" is greater than the length of the sequence (\"%d\").";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
            description = "Feature end position must not exceed the sequence length",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateFeatureEndWithinSequence(GFF3Annotation annotation, int line) throws ValidationException {
        String seqId = annotation.getAccession();
        Long sequenceLength = resolveSequenceLength(seqId);
        if (sequenceLength == null) {
            return;
        }
        for (GFF3Feature feature : annotation.getFeatures()) {
            if (feature.getEnd() > sequenceLength) {
                String location = feature.getStart() + ".." + feature.getEnd();
                throw new ValidationException(
                        RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
                        line,
                        FEATURE_END_EXCEEDS_SEQUENCE_LENGTH.formatted(location, sequenceLength));
            }
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
