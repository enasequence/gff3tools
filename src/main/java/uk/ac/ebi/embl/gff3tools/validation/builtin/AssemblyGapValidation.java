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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.AnnotationValidation;

public class AssemblyGapValidation implements AnnotationValidation {

    public static final String VALIDATION_RULE = "GFF3_ASSEMBLY_GAP_VALIDATION";

    private static final String NO_ASSEMBLY_GAP_MESSAGE =
            "\"gap_type\" and  \"linkage_evidence\" qualifiers are only allowed in assembly_gap feature";

    private static final String GAP_FEATURE_MESSAGE = "\"assembly_gap\" and  \"gap\" feature are mutually exclusive";

    private static final String INVALID_GAP_TYPE = "\"gap_type\" value \"%s\" is invalid";
    private static final String LINKAGE_EVIDENCE_MISSING_MESSAGE =
            "\"linkage_evidence\" qualifier must exists in feature \"assembly_gap\",if qualifier \"gap_type\" value equals to \"%s\"";
    private static final String LINKAGE_EVIDENCE_NOT_ALLOWED_MESSAGE =
            "\"linkage_evidence\" qualifier is  allowed in \"assembly_gap\" feature only when \"gap_type\" qualifier value equals to \"within scaffold\" or \"repeat within scaffold\"";
    private static final String INVALID_QUALIFIER_MESSAGE =
            "\"%s\" qualifier is not allowed in \"assembly_gap\" feature.";
    private static final String INVALID_LOCATION_MESSAGE = "\"assembly_gap\" location is invalid";

    public static final Map<String, String> GAP_TYPE = Map.ofEntries(
            Map.entry("within scaffold", "scaffold"),
            Map.entry("between scaffolds", "contig"),
            Map.entry("between scaffold", "contig"),
            Map.entry("centromere", "centromere"),
            Map.entry("short arm", "short_arm"),
            Map.entry("heterochromatin", "heterochromatin"),
            Map.entry("telomere", "telomere"),
            Map.entry("repeat within scaffold", "repeat"),
            Map.entry("unknown", "unknown"),
            Map.entry("repeat between scaffolds", "repeat"),
            Map.entry("contamination", "contamination"));

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {

        List<GFF3Feature> gapFeatures = annotation.getFeaturesByName(GFF3Anthology.GAP_FEATURE_NAME);
        List<GFF3Feature> assemblyGapFeature = annotation.getFeaturesByName(GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME);

        List<GFF3Feature> gapTypeFeatures = annotation.getFeatures().stream()
                .filter(f -> f.isAttributeExists(GFF3Attributes.GAP_TYPE))
                .toList();

        List<GFF3Feature> linkageEvidenceFeatures = annotation.getFeatures().stream()
                .filter(f -> f.isAttributeExists(GFF3Attributes.LINKAGE_EVIDENCE))
                .toList();

        Set<String> permittedAttributes = Set.of(
                GFF3Attributes.ATTRIBUTE_ID,
                GFF3Attributes.ESTIMATED_LENGTH,
                GFF3Attributes.GAP_TYPE,
                GFF3Attributes.LINKAGE_EVIDENCE);

        if (assemblyGapFeature.isEmpty() && (!gapTypeFeatures.isEmpty() || !linkageEvidenceFeatures.isEmpty())) {
            throw new ValidationException(VALIDATION_RULE, line, NO_ASSEMBLY_GAP_MESSAGE);
        }

        if (!assemblyGapFeature.isEmpty() && !gapFeatures.isEmpty()) {
            throw new ValidationException(VALIDATION_RULE, line, GAP_FEATURE_MESSAGE);
        }

        for (GFF3Feature assemblyGap : assemblyGapFeature) {

            String gapType = assemblyGap.getAttributeByName(GFF3Attributes.GAP_TYPE);
            boolean hasLinkageEvidence = assemblyGap.isAttributeExists(GFF3Attributes.LINKAGE_EVIDENCE);

            if (gapType != null && !GAP_TYPE.containsKey(gapType)) {
                throw new ValidationException(VALIDATION_RULE, line, INVALID_GAP_TYPE.formatted(gapType));
            }

            if ((gapType != null
                    && (gapType.equalsIgnoreCase("within scaffold")
                            || gapType.equalsIgnoreCase("repeat within scaffold")
                            || gapType.equalsIgnoreCase("contamination")))) {

                if (!hasLinkageEvidence) {
                    throw new ValidationException(
                            VALIDATION_RULE, line, LINKAGE_EVIDENCE_MISSING_MESSAGE.formatted(gapType));
                }

            } else {
                if (hasLinkageEvidence) {
                    throw new ValidationException(VALIDATION_RULE, line, LINKAGE_EVIDENCE_NOT_ALLOWED_MESSAGE);
                }
            }

            if (assemblyGap.getStart() == 0 || assemblyGap.getEnd() == 0) {
                throw new ValidationException(VALIDATION_RULE, line, INVALID_LOCATION_MESSAGE);
            }

            for (Map.Entry<String, Object> attribute :
                    assemblyGap.getAttributes().entrySet()) {
                if (!permittedAttributes.contains(attribute.getKey())) {
                    throw new ValidationException(
                            VALIDATION_RULE, line, INVALID_QUALIFIER_MESSAGE.formatted(attribute.getKey()));
                }
            }
        }
    }
}
