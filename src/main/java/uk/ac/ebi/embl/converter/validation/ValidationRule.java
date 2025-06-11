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
package uk.ac.ebi.embl.converter.validation;

import java.util.HashMap;
import java.util.Map;

public enum ValidationRule {
    unmapped_flatfile_feature("The flatfile feature does not exist on the ontology."),
    test_feature("Just a feature for testing purposes.");

    private String description;

    public static Map<ValidationRule, RuleSeverity> VALIDATION_SEVERITIES = new HashMap<>() {
        {
            put(unmapped_flatfile_feature, RuleSeverity.WARN);
        }
    };

    ValidationRule(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
