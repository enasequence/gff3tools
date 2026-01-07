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
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.*;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "DUPLICATE_FEATURE")
public class DuplicateFeatureValidation extends Validation {

    private static final String DUPLICATE_PROTEIN_ID_MESSAGE =
            "Duplicate Protein Id \"%s\" found in the \"%s\" at location \"%s\"";

    @ValidationMethod(rule = "DUPLICATE_FEATURE", type = ValidationType.ANNOTATION)
    public void validateDuplicateProtein(GFF3Annotation annotation, int line) throws ValidationException {

        Map<String, String> proteinToAttribute = new HashMap<>();

        for (GFF3Feature feature : annotation.getFeatures()) {

            String proteinId = feature.getAttribute(GFF3Attributes.PROTEIN_ID).orElse(null);
            String attributeId =
                    feature.getAttribute(GFF3Attributes.ATTRIBUTE_ID).orElse(null);

            if (proteinId == null || attributeId == null) continue;

            String existingAttribute = proteinToAttribute.get(proteinId);

            if (existingAttribute == null) {
                proteinToAttribute.put(proteinId, attributeId);
            } else if (!existingAttribute.equals(attributeId)) {
                throw new ValidationException(
                        line,
                        DUPLICATE_PROTEIN_ID_MESSAGE.formatted(
                                proteinId, feature.getName(), feature.getStart() + " " + feature.getEnd()));
            }
        }
    }
}
