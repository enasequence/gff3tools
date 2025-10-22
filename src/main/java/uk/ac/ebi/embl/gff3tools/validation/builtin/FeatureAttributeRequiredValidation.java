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

import java.util.HashSet;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation
public class FeatureAttributeRequiredValidation extends Validation {

    public HashSet<String> featuresToValidate = new HashSet<>();
    private static final String NO_QUALIFIERS_MESSAGE =
            "No attributes are present for accession \"%s\" on feature \"%s\" ";

    public FeatureAttributeRequiredValidation() {
        for (String ff_feature : GFF3Anthology.FF_FEATURE_SET_ATTRIBUTES_REQUIRED) {
            // the feature name could be an ID or a name
            GFF3Anthology.FF_FEATURE_SET_ATTRIBUTES_REQUIRED.stream()
                    .flatMap(FeatureMapping::getGFF3FeatureCandidateISOIDsNoQualifiersRequired)
                    .forEach(featuresToValidate::add);
            GFF3Anthology.FF_FEATURE_SET_ATTRIBUTES_REQUIRED.stream()
                    .flatMap(FeatureMapping::getGFF3FeatureCandidateNamesNoQualifiersRequired)
                    .forEach(featuresToValidate::add);
        }
    }

    @ValidationMethod(rule = "ATTRIBUTE_IS_PRESENT", severity = RuleSeverity.WARN, type = ValidationType.FEATURE)
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();

        if (featuresToValidate.contains(featureName)
                && feature.getAttributes().keySet().equals(Set.of("ID", "Parent"))) {
            throw new ValidationException(line, NO_QUALIFIERS_MESSAGE.formatted(feature.accession(), featureName));
        }
    }
}
