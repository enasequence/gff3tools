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
package uk.ac.ebi.embl.converter.fftogff3;

import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.exception.ValidationException;
import uk.ac.ebi.embl.converter.validation.FeatureValidation;

public class UnmappedFFFeatureValidation implements FeatureValidation<Feature> {

    @Override
    public String getValidationRule() {
        return "FLATFILE_NO_ONTOLOGY_FEATURE";
    }

    @Override
    public void validateFeature(Feature feature, int line) throws ValidationException {

        if (FeatureMapping.getGFF3FeatureName(feature).isEmpty()) {
            throw new ValidationException(getValidationRule(), feature.getName());
        }
    }
}
