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

import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.fasta.Topology;

@Getter
@Setter
public class FastaHeader {
    String description; // mandatory (can be empty if you insist)
    String moleculeType; // mandatory (can be null if empty allowed)
    Topology topology; // mandatory (can be null if empty allowed)
    Optional<String> chromosomeType; // optional (doesnt have to be a json)
    Optional<String> chromosomeLocation; // optional
    Optional<String> chromosomeName; // optional
}
