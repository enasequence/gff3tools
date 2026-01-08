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
package uk.ac.ebi.embl.gff3tools.fasta.headerutils;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.fasta.Topology;

@Getter
@Setter
public class FastaHeader {
    @JsonProperty("description")
    String description; // mandatory

    @JsonProperty("molecule_type")
    @JsonAlias({"molecule-type", "molecule type", "moleculetype"})
    String moleculeType; // mandatory

    @JsonProperty("topology")
    Topology topology; // mandatory

    @JsonProperty("chromosome_type")
    @JsonAlias({"chromosome-type", "chromosome type", "chromosometype"})
    String chromosomeType; // optional (doesn't have to be in the json at all)

    @JsonProperty("chromosome_location")
    @JsonAlias({"chromosome-location", "chromosome location", "chromosomelocation"})
    String chromosomeLocation; // optional

    @JsonProperty("chromosome_name")
    @JsonAlias({"chromosome-name", "chromosome name", "chromosomename"})
    String chromosomeName; // optional
}
