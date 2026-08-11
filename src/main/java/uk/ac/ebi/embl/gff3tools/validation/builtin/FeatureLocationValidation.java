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
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ExitMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Slf4j
@Gff3Validation(
        name = "FEATURE_LOCATION",
        description = "Validates that feature and sequence-region coordinates are within the sequence bounds")
public class FeatureLocationValidation implements Validation {

    private static final String RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH = "FEATURE_END_EXCEEDS_SEQUENCE_LENGTH";
    private static final String FEATURE_END_EXCEEDS_SEQUENCE_LENGTH =
            "The end position of the location \"%s\" is greater than the length of the sequence (\"%d\").";
    private static final String FEATURE_END_BELOW_ONE = "The end position of the location \"%s\" is less than 1.";

    private static final String RULE_FEATURE_START_BELOW_ONE = "FEATURE_START_BELOW_ONE";
    private static final String FEATURE_START_BELOW_ONE = "The start position of the location \"%s\" is less than 1.";

    @InjectContext
    private ValidationContext context;

    private final Map<String, Long> sequenceLengthCache = new HashMap<>();

    @ValidationMethod(
            rule = RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
            description = "Feature end position must not exceed the sequence length",
            type = ValidationType.FEATURE,
            priority = ValidationPriority.LOW)
    public void validateFeatureEndWithinSequence(GFF3Feature feature, int line) throws ValidationException {
        if (feature.getEnd() < 1) {
            throw new ValidationException(
                    RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH, line, FEATURE_END_BELOW_ONE.formatted(feature.getEnd()));
        }

        // If no FASTA header source registered for this run -> nothing to validate as we do not know the topology.
        // otherwise get topology from fastaHeader
        if (!context.contains(FastaHeaderProvider.class)
                || context.get(FastaHeaderProvider.class)
                        .getHeader(feature.accession())
                        .isEmpty()) {
            log.warn("No FASTA header found for accession {}", feature.accession());
            return;
        }
        FastaHeader header = context.get(FastaHeaderProvider.class)
                .getHeader(feature.accession())
                .get();
        Optional<ControlledVocabularyUtils.Topology> topologyOpt =
                ControlledVocabularyUtils.Topology.fromValue(header.getTopology());
        if (topologyOpt.isEmpty()) {
            /***this field is mandatory, if not present the issue will be caught by {@link FastaHeaderFormatValidation}***/
            return;
        }
        ControlledVocabularyUtils.Topology topology = topologyOpt.get();

        Long lastBaseIndex = ValidationUtils.resolveSequenceLength(feature.accession(), sequenceLengthCache, context);
        if (lastBaseIndex == null) {
            return;
        }

        if (topology.equals(ControlledVocabularyUtils.Topology.LINEAR) && feature.getEnd() > lastBaseIndex) {
            String location = feature.getStart() + ".." + feature.getEnd();
            throw new ValidationException(
                    RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
                    line,
                    FEATURE_END_EXCEEDS_SEQUENCE_LENGTH.formatted(location, lastBaseIndex));
        }
    }

    @ValidationMethod(
            rule = RULE_FEATURE_START_BELOW_ONE,
            description = "Feature start position must be at least 1",
            type = ValidationType.FEATURE,
            priority = ValidationPriority.LOW)
    public void validateFeatureStartAboveZero(GFF3Feature feature, int line) throws ValidationException {
        if (feature.getStart() < 1) {
            String location = feature.getStart() + ".." + feature.getEnd();
            throw new ValidationException(
                    RULE_FEATURE_START_BELOW_ONE, line, FEATURE_START_BELOW_ONE.formatted(location));
        }
    }

    @ExitMethod
    public void clear() {
        sequenceLengthCache.clear();
    }
}
