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
package uk.ac.ebi.embl.gff3tools.metadata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a literature reference from MasterEntry JSON.
 * Maps to RF lines in EMBL output.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceData {

    private Integer referenceNumber;
    private String referencePosition;
    private String referenceComment;
    private String title;
    private String consortium;
    private String authors;
    private String location;
}
