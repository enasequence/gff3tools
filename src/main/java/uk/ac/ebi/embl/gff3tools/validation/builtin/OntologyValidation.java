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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.so.SoTerminusClient;
import uk.ac.ebi.embl.gff3tools.validation.FeatureValidation;

public class OntologyValidation implements FeatureValidation {

    public static final String VALIDATION_RULE = "GFF3_ONTOLOGY_FEATURE";

    SoTerminusClient soClient;

    public OntologyValidation() {
        soClient = new SoTerminusClient();
    }

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        if (!soClient.isFeatureSoTerm(feature.getName())) {
            throw new ValidationException(
                    String.format(
                            "Feature name '%s' is not a valid feature SO term. (line %d)", feature.getName(), line),
                    VALIDATION_RULE);
        }
    }
}
