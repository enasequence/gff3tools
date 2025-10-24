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
package uk.ac.ebi.embl.gff3tools.utils;

public enum OntologyTerm {
    FEATURE("SO:0000110"),
    REGION("SO:0000001"),
    CDS("SO:0000316"),
    RRNA("SO:0000252"),
    TRNA("SO:0000253"),
    PSEUDOGENIC_RRNA("SO:0000777"),
    PSEUDOGENE("SO:0000336"),
    GENE("SO:0000704"),
    UNITARY_PSEUDOGENE("SO:0001759");
    public final String ID;

    OntologyTerm(String id) {
        this.ID = id;
    }
}
