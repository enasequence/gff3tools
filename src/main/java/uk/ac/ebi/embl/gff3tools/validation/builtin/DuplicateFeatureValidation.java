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
import java.util.Objects;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.FeatureValidation;

public class DuplicateFeatureValidation implements FeatureValidation {

    private record ProteinAttributePair(String proteinId, String attributeId) {

        public ProteinAttributePair {
            Objects.requireNonNull(proteinId, "Protein ID must not be null");
            Objects.requireNonNull(attributeId, "Attribute ID must not be null");
        }
    }

    public static final String VALIDATION_RULE = "GFF3_DUPLICATE_FEATURE_VALIDATION";
    private final Map<String, Integer> proteinIdMap = new HashMap<>();
    private final Map<ProteinAttributePair, Integer> proteinAttributeMap = new HashMap<>();
    private static final String DUPLICATE_PROTEIN_ID_MESSAGE =
            "Duplicate Protein Id \"%s\" found. First occurrence at line %d, conflicting occurrence at line %d";

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        String proteinId = feature.getAttributeByName(GFF3Attributes.PROTEIN_ID);
        String attributeId = feature.getAttributeByName(GFF3Attributes.ATTRIBUTE_ID);

        if (proteinId != null && attributeId != null) {
            ProteinAttributePair proteinAttributePair = new ProteinAttributePair(proteinId, attributeId);
            if (proteinIdMap.containsKey(proteinId)) {
                if (!proteinAttributeMap.containsKey(proteinAttributePair)) {
                    int prevLine = proteinIdMap.get(proteinId);
                    throw new ValidationException(
                            VALIDATION_RULE, line, DUPLICATE_PROTEIN_ID_MESSAGE.formatted(proteinId, prevLine, line));
                }
            } else {
                proteinIdMap.put(proteinId, line);
            }
            proteinAttributeMap.put(proteinAttributePair, line);
        }
    }
}
