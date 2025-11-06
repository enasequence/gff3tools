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

@Gff3Validation(name = "FEATURE_SPECIFIC")
public class FeatureSpecificValidation extends Validation {

    private static final String INVALID_OPERON_MESSAGE =
            "Feature \"%s\" belongs to operon \"%s\", but no other features share this operon. Expected at least one additional member.";

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @ValidationMethod(rule = "OPERON_FEATURE", type = ValidationType.FEATURE)
    public void validateOperonFeatures(GFF3Feature feature, int line) throws ValidationException {
        String operonValue = feature.getAttributeByName(GFF3Attributes.OPERON);
        if (operonValue == null || operonValue.isBlank()) {
            return;
        }

        Optional<String> soIdOpt = ontologyClient.findTermByNameOrSynonym(feature.getName());
        boolean isOperon = soIdOpt.isPresent()
                && (OntologyTerm.OPERON.ID.equals(soIdOpt.get())
                        || ontologyClient.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.OPERON.ID));
        if (isOperon) {
            return;
        }

        throw new ValidationException(line, INVALID_OPERON_MESSAGE.formatted(feature.getName(), operonValue));
    }
}
