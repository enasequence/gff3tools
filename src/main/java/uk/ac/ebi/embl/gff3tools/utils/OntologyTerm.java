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
    OPERON("SO:0000178"),
    CDS("SO:0000316"),
    CDS_REGION("SO:0000851"),
    PSEUDOGENIC_CDS("SO:0002087"),
    SIGNAL_PEPTIDE("SO:0000418"),
    TRANSIT_PEPTIDE("SO:0000725"),
    PSEUDOGENIC_RRNA("SO:0000777"),
    PSEUDOGENE("SO:0000336"),
    GENE("SO:0000704"),
    UNITARY_PSEUDOGENE("SO:0001759"),
    MATURE_PROTEIN_REGION_OF_CDS("SO:0002249"),
    PROPEPTIDE("SO:0001062"),
    PROPEPTIDE_REGION_OF_CDS("SO:0002250"),
    SIGNAL_PEPTIDE_REGION_OF_CDS("SO:0002251"),
    TRANSIT_PEPTIDE_REGION_OF_CDS("SO:0002252"),
    INTRON("SO:0000188"),
    EXON("SO:0000147"),
    SPLICEOSOMAL_INTRON("SO:0000662"),
    CODING_EXON("SO:0000195"),
    MRNA("SO:0000234"),
    RRNA("SO:0000252"),
    TRNA("SO:0000253"),
    GAP("SO:0000730"),
    POLYPEPTIDE_REGION("SO:0000839"),
    PSEUDOGENIC_REGION("SO:0000462");

    public final String ID;

    OntologyTerm(String id) {
        this.ID = id;
    }
}
