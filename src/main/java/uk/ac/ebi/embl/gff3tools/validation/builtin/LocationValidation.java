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

import java.util.*;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.*;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

@Gff3Validation(
        name = "LOCATION",
        description = "Validates that features and sequence-region coordinates are within the sequence bounds")
public class LocationValidation implements Validation {

    private static final String RULE_FEATURE_LOCATION_RANGE = "LOCATION_RANGE";
    private static final String INVALID_START_END_MESSAGE =
            "Invalid start/end for accession \"%s\" at location \"%s\".";

    private static final String RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH = "FEATURE_END_EXCEEDS_SEQUENCE_LENGTH";
    private static final String FEATURE_END_EXCEEDS_SEQUENCE_LENGTH =
            "The end position of the location \"%s\" for accession \"%s\" is greater than the length of the sequence (\"%d\").";

    private static final String RULE_FEATURE_END_BELOW_ONE = "FEATURE_END_BELOW_ONE";
    private static final String FEATURE_END_BELOW_ONE =
            "The end position of the location \"%s\" for accession \"%s\" is less than 1.";

    private static final String RULE_FEATURE_START_BELOW_ONE = "FEATURE_START_BELOW_ONE";
    private static final String FEATURE_START_BELOW_ONE =
            "The start position of the location \"%s\" for accession \"%s\" is less than 1.";

    private static final String RULE_CDS_LOCATION_BOUNDARIES = "CDS_LOCATION_BOUNDARIES";
    private static final String INVALID_PROPEPTIDE_CDS_LOCATION_MESSAGE = "Propeptide [%d %d] not inside any CDS";
    private static final String INVALID_PROPEPTIDE_PEPTIDE_LOCATION_MESSAGE =
            "Propeptide [%d %d] overlaps with peptide features";

    private final Map<String, Long> sequenceLengthCache = new HashMap<>();

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(rule = RULE_FEATURE_LOCATION_RANGE, type = ValidationType.FEATURE)
    public void validateLocationRange(GFF3Feature feature, int line) throws ValidationException {
        long start = feature.getStart();
        long end = feature.getEnd();

        if (!isCircularRNA(feature) && end < start) {
            throw new ValidationException(
                    line, INVALID_START_END_MESSAGE.formatted(feature.accession(), location(feature)));
        }
    }

    @ValidationMethod(
            rule = RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
            description = "Feature end position must not exceed the sequence length",
            type = ValidationType.FEATURE)
    public void validateFeatureEndWithinSequence(GFF3Feature feature, int line) throws ValidationException {
        Long lastBaseIndex = ValidationUtils.resolveSequenceLength(feature.accession(), sequenceLengthCache, context);
        if (lastBaseIndex == null) {
            return;
        }

        // Circular molecules may carry origin-spanning features whose end is expressed as
        // "physical end + sequence length", so the end legitimately exceeds the sequence length.
        if (!isCircularSequence(feature.accession()) && feature.getEnd() > lastBaseIndex) {
            throw new ValidationException(
                    RULE_FEATURE_END_EXCEEDS_SEQUENCE_LENGTH,
                    line,
                    FEATURE_END_EXCEEDS_SEQUENCE_LENGTH.formatted(
                            location(feature), feature.accession(), lastBaseIndex));
        }
    }

    @ValidationMethod(
            rule = RULE_FEATURE_END_BELOW_ONE,
            description = "Feature end position must be at least 1",
            type = ValidationType.FEATURE)
    public void validateFeatureEndAboveZero(GFF3Feature feature, int line) throws ValidationException {
        if (feature.getEnd() < 1) {
            throw new ValidationException(
                    RULE_FEATURE_END_BELOW_ONE,
                    line,
                    FEATURE_END_BELOW_ONE.formatted(location(feature), feature.accession()));
        }
    }

    @ValidationMethod(
            rule = RULE_FEATURE_START_BELOW_ONE,
            description = "Feature start position must be at least 1",
            type = ValidationType.FEATURE)
    public void validateFeatureStartAboveZero(GFF3Feature feature, int line) throws ValidationException {
        if (feature.getStart() < 1) {
            throw new ValidationException(
                    RULE_FEATURE_START_BELOW_ONE,
                    line,
                    FEATURE_START_BELOW_ONE.formatted(location(feature), feature.accession()));
        }
    }

    @ValidationMethod(rule = RULE_CDS_LOCATION_BOUNDARIES, type = ValidationType.ANNOTATION)
    public void validateCdsLocation(GFF3Annotation annotation, int line) throws ValidationException {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        List<GFF3Feature> propFeatures = new ArrayList<>();
        List<GFF3Feature> cdsFeatures = new ArrayList<>();
        List<GFF3Feature> peptideFeatures = new ArrayList<>();

        for (GFF3Feature feature : annotation.getFeatures()) {
            String featureName = feature.getName();
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(featureName);
            if (soIdOpt.isEmpty()) {
                continue;
            }
            String soId = soIdOpt.get();
            if (OntologyTerm.PROPEPTIDE_REGION_OF_CDS.ID.equals(soId)) {
                propFeatures.add(feature);
            }
            if (OntologyTerm.CDS_REGION.ID.equals(soId)) {
                cdsFeatures.add(feature);
            }
            if (OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.ID.equals(soId)
                    || OntologyTerm.MATURE_PROTEIN_REGION_OF_CDS.ID.equals(soId)) {
                peptideFeatures.add(feature);
            }
        }

        cdsFeatures.sort(Comparator.comparingLong(GFF3Feature::getStart));
        peptideFeatures.sort(Comparator.comparingLong(GFF3Feature::getStart));

        for (GFF3Feature propFeature : propFeatures) {

            long start = propFeature.getStart();
            long end = propFeature.getEnd();

            // TODO: Need to separate the below validation - after confirmation on parent child
            // Must be inside at least one CDS
            boolean insideCds = false;
            for (GFF3Feature cds : cdsFeatures) {
                if (cds.getEnd() < start) continue;
                if (cds.getStart() > end) break;
                if (start >= cds.getStart() && end <= cds.getEnd()) {
                    insideCds = true;
                    break;
                }
            }

            if (!insideCds) {
                throw new ValidationException(line, INVALID_PROPEPTIDE_CDS_LOCATION_MESSAGE.formatted(start, end));
            }

            // Must not overlap any peptide features
            for (GFF3Feature peptide : peptideFeatures) {
                if (peptide.getEnd() < start) continue;
                if (peptide.getStart() > end) break;
                if (start < peptide.getEnd() && end > peptide.getStart()) {
                    throw new ValidationException(
                            line, INVALID_PROPEPTIDE_PEPTIDE_LOCATION_MESSAGE.formatted(start, end));
                }
            }
        }
    }

    @ExitMethod
    public void clear() {
        sequenceLengthCache.clear();
    }

    private static String location(GFF3Feature feature) {
        return feature.getStart() + ".." + feature.getEnd();
    }

    private static boolean isCircularRNA(GFF3Feature feature) {
        return Boolean.TRUE
                .toString()
                .equalsIgnoreCase(
                        feature.getAttribute(GFF3Attributes.CIRCULAR_RNA).orElse("false"));
    }

    /**
     * Topology is only known when a FASTA header source is registered for the run. An absent or
     * unrecognised topology is treated as non-circular: circular is always explicitly declared, and
     * a missing mandatory topology is reported by {@link FastaHeaderFormatValidation}.
     */
    private boolean isCircularSequence(String accession) {
        if (!context.contains(FastaHeaderProvider.class)) {
            return false;
        }
        return context.get(FastaHeaderProvider.class)
                .getHeader(accession)
                .map(FastaHeader::getTopology)
                // Canonicalise rather than matching the raw value: FastaHeaderNormalisationFix is
                // annotation-scoped and runs only after this annotation's features are validated.
                .flatMap(topology ->
                        ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, topology))
                .flatMap(ControlledVocabularyUtils.Topology::fromValue)
                .map(ControlledVocabularyUtils.Topology.CIRCULAR::equals)
                .orElse(false);
    }
}
