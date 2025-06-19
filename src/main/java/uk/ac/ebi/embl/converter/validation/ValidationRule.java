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

public enum ValidationRule {
    FLATFILE_NO_SOURCE("The flatfile contains no source feature"),
    FLATFILE_NO_ONTOLOGY_FEATURE("The flatfile feature does not exist on the ontology."),
    GFF3_INVALID_RECORD("The record does not conform with the expected gff3 format"),
    GFF3_INVALID_HEADER("Invalid gff3 header"),;

    private String description;

    ValidationRule(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
