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
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

public class AssemblyGapValidation extends Validation {

    public static final String VALIDATION_RULE = "GAP_FEATURE";

    private static final String NO_ASSEMBLY_GAP_MESSAGE =
            "\"gap_type\" and  \"linkage_evidence\" qualifiers are only allowed in gap feature";

    private static final String GAP_FEATURE_MESSAGE = "\"assembly_gap\" and  \"gap\" feature are mutually exclusive";

    private static final String INVALID_GAP_TYPE = "\"gap_type\" value \"%s\" is invalid";
    private static final String LINKAGE_EVIDENCE_MISSING_MESSAGE =
            "\"linkage_evidence\" qualifier must exists in feature \"gap\",if qualifier \"gap_type\" value equals to \"%s\"";
    private static final String LINKAGE_EVIDENCE_NOT_ALLOWED_MESSAGE =
            "\"linkage_evidence\" qualifier is  allowed in \"gap\" feature only when \"gap_type\" qualifier value equals to \"within scaffold\" or \"repeat within scaffold\"";
    private static final String INVALID_QUALIFIER_MESSAGE = "\"%s\" qualifier is not allowed in \"gap\" feature.";
    private static final String INVALID_LOCATION_MESSAGE = "\"gap\" location is invalid";

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

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @ValidationMethod(rule = VALIDATION_RULE, type = ValidationType.ANNOTATION)
    public void validateGapFeature(GFF3Annotation annotation, int line) throws ValidationException {
        List<GFF3Feature> gapFeatures = new ArrayList<>();
        for (GFF3Feature feature : annotation.getFeatures()) {
            Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
            if (soIdOpt.isPresent()) {
                String soId = soIdOpt.get();
                boolean hasGapType = feature.isAttributeExists(GFF3Attributes.GAP_TYPE);
                boolean hasLinkageEvidence = feature.isAttributeExists(GFF3Attributes.LINKAGE_EVIDENCE);
                if (OntologyTerm.GAP.ID.equals(soId)) {
                    gapFeatures.add(feature);
                } else if (hasGapType || hasLinkageEvidence) {
                    throw new ValidationException(VALIDATION_RULE, line, NO_ASSEMBLY_GAP_MESSAGE);
                }
            }
        }

        Set<String> permittedAttributes = Set.of(
                GFF3Attributes.ATTRIBUTE_ID,
                GFF3Attributes.ESTIMATED_LENGTH,
                GFF3Attributes.GAP_TYPE,
                GFF3Attributes.LINKAGE_EVIDENCE);

        for (GFF3Feature gap : gapFeatures) {

            String gapType = gap.getAttributeByName(GFF3Attributes.GAP_TYPE);
            boolean hasLinkageEvidence = gap.isAttributeExists(GFF3Attributes.LINKAGE_EVIDENCE);

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

            if (gap.getStart() == 0 || gap.getEnd() == 0) {
                throw new ValidationException(VALIDATION_RULE, line, INVALID_LOCATION_MESSAGE);
            }

            for (Map.Entry<String, Object> attribute : gap.getAttributes().entrySet()) {
                if (!permittedAttributes.contains(attribute.getKey())) {
                    throw new ValidationException(
                            VALIDATION_RULE, line, INVALID_QUALIFIER_MESSAGE.formatted(attribute.getKey()));
                }
            }
        }
    }
}
