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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "ASSEMBLY_GAP")
public class AssemblyGapValidation extends Validation {

    private static final String VALIDATION_RULE = "GAP_FEATURE";

    private static final String NO_ASSEMBLY_GAP_MESSAGE =
            "\"gap_type\" and  \"linkage_evidence\" attributes are only allowed in gap feature";

    private static final String INVALID_GAP_TYPE = "\"gap_type\" value \"%s\" is invalid";
    private static final String LINKAGE_EVIDENCE_MISSING_MESSAGE =
            "\"linkage_evidence\" attributes must exists in feature \"gap\",if attributes \"gap_type\" value equals to \"%s\"";
    private static final String LINKAGE_EVIDENCE_NOT_ALLOWED_MESSAGE =
            "\"linkage_evidence\" attributes is  allowed in \"gap\" feature only when \"gap_type\" attributes value equals to \"within scaffold\" or \"repeat within scaffold\"";
    private static final String INVALID_ATTRIBUTE_MESSAGE = "\"%s\" attributes is not allowed in \"gap\" feature.";
    private static final String INVALID_LOCATION_MESSAGE = "\"gap\" location is invalid";

    private static final Map<String, String> GAP_TYPE = Map.ofEntries(
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

    private static final Set<String> permittedAttributes = Set.of(
            GFF3Attributes.ATTRIBUTE_ID,
            GFF3Attributes.ESTIMATED_LENGTH,
            GFF3Attributes.GAP_TYPE,
            GFF3Attributes.LINKAGE_EVIDENCE);

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @ValidationMethod(rule = VALIDATION_RULE, type = ValidationType.FEATURE)
    public void validateGapFeature(GFF3Feature feature, int line) throws ValidationException {
        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        if (soIdOpt.isPresent()) {
            String soId = soIdOpt.get();
            boolean hasGapType = feature.hasAttribute(GFF3Attributes.GAP_TYPE);
            boolean hasLinkageEvidence = feature.hasAttribute(GFF3Attributes.LINKAGE_EVIDENCE);
            if (OntologyTerm.GAP.ID.equals(soId)) {
                String gapType = feature.getAttributeByName(GFF3Attributes.GAP_TYPE);
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

                if (feature.getStart() == 0 || feature.getEnd() == 0) {
                    throw new ValidationException(VALIDATION_RULE, line, INVALID_LOCATION_MESSAGE);
                }

                for (Map.Entry<String, Object> attribute :
                        feature.getAttributes().entrySet()) {
                    if (!permittedAttributes.contains(attribute.getKey())) {
                        throw new ValidationException(
                                VALIDATION_RULE, line, INVALID_ATTRIBUTE_MESSAGE.formatted(attribute.getKey()));
                    }
                }

            } else if (hasGapType || hasLinkageEvidence) {
                throw new ValidationException(VALIDATION_RULE, line, NO_ASSEMBLY_GAP_MESSAGE);
            }
        }
    }
}
