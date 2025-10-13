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
package uk.ac.ebi.embl.gff3tools.gff3;

import java.util.Set;

public class GFF3Anthology {

    public static final String MAP_PEPTIDE_FEATURE_NAME = "map_peptide";
    public static final String SIG_PEPTIDE_FEATURE_NAME = "signal_peptide";
    public static final String CDS_FEATURE_NAME = "CDS";
    public static final String PSEUDOGENIC_CDS_FEATURE_NAME = "pseudogenic_CDS";
    public static final String PROPETIDE_FEATURE_NAME = "propeptide";

    public static final String EXON_FEATURE_NAME = "exon";
    public static final String CODING_EXON_FEATURE_NAME = "coding_exon";
    public static final String NONCODING_EXON_FEATURE_NAME = "noncoding_exon";
    public static final String FIVE_PRIME_CODING_EXON_FEATURE_NAME = "five_prime_coding_exon";
    public static final String INTERIOR_CODING_EXON_FEATURE_NAME = "interior_coding_exon";

    public static final String INTRON_FEATURE_NAME = "intron";
    public static final String SPLICEOSOMAL_INTRON_FEATURE_NAME = "spliceosomal_intron";
    public static final String AUTOCATALYTICALLY_SPLICED_INTRON_FEATURE_NAME = "autocatalytically_spliced_intron";

    public static final Set<String> EXON_EQUIVALENTS = Set.of(
            EXON_FEATURE_NAME,
            CODING_EXON_FEATURE_NAME,
            NONCODING_EXON_FEATURE_NAME,
            FIVE_PRIME_CODING_EXON_FEATURE_NAME,
            INTERIOR_CODING_EXON_FEATURE_NAME);

    public static final Set<String> INTRON_EQUIVALENTS = Set.of(
            INTRON_FEATURE_NAME, SPLICEOSOMAL_INTRON_FEATURE_NAME, AUTOCATALYTICALLY_SPLICED_INTRON_FEATURE_NAME);

    public static final String OLD_SEQUENCE_FEATURE_NAME = "old_sequence";

    public static final String PROCESSED_PSEUDOGENE_FEATURE_NAME = "processed_pseudogene";
    public static final String NON_PROCESSED_PSEUDOGENE_FEATURE_NAME = "non_processed_pseudogene";
    public static final String UNITARY_PSEUDOGENE_FEATURE_NAME = "unitary_pseudogene";
    public static final String ALLELIC_PSEUDOGENE_FEATURE_NAME = "allelic_pseudogene";
    public static final String PSEUDOGENE_FEATURE_NAME = "pseudogene";
    public static final String NCRNA_GENE_FEATURE_NAME = "ncRNA_gene";

    public static final Set<String> GENE_EQUIVALENTS = Set.of(
            PROCESSED_PSEUDOGENE_FEATURE_NAME,
            NON_PROCESSED_PSEUDOGENE_FEATURE_NAME,
            UNITARY_PSEUDOGENE_FEATURE_NAME,
            ALLELIC_PSEUDOGENE_FEATURE_NAME,
            PSEUDOGENE_FEATURE_NAME,
            NCRNA_GENE_FEATURE_NAME);

    public static final Set<String> CDS_EQUIVALENTS = Set.of(CDS_FEATURE_NAME, PSEUDOGENIC_CDS_FEATURE_NAME);

    public static final String R_RNA_FEATURE_NAME = "rRNA";
    public static final String PSEUDOGENIC_R_RNA_FEATURE_NAME = "pseudogenic_rRNA";
    public static final String PROCESSED_R_RNA_FEATURE_NAME = "processed_pseudogenic_rRNA";
    public static final String UNPROCESSED_R_RNA_FEATURE_NAME = "unprocessed_pseudogenic_rRNA";
    public static final String UNITARY_R_RNA_FEATURE_NAME = "unitary_pseudogenic_rRNA";
    public static final String ALLELIC_R_RNA_FEATURE_NAME = "allelic_pseudogenic_rRNA";

    public static final Set<String> R_RNA_EQUIVALENTS = Set.of(
            R_RNA_FEATURE_NAME,
            PSEUDOGENIC_R_RNA_FEATURE_NAME,
            PROCESSED_R_RNA_FEATURE_NAME,
            UNPROCESSED_R_RNA_FEATURE_NAME,
            UNITARY_R_RNA_FEATURE_NAME,
            ALLELIC_R_RNA_FEATURE_NAME);

    public static final String T_RNA_FEATURE_NAME = "tRNA";
    public static final String TRANS_SPLICED_TRANSCRIPT_T_RNA_FEATURE_NAME = "trans_spliced_transcript";
    public static final String PSEUDOGENIC_T_RNA_FEATURE_NAME = "pseudogenic_tRNA";
    public static final String PROCESSED_T_RNA_FEATURE_NAME = "processed_pseudogenic_tRNA";
    public static final String UNPROCESSED_T_RNA_FEATURE_NAME = "unprocessed_pseudogenic_tRNA";
    public static final String UNITARY_T_RNA_FEATURE_NAME = "unitary_pseudogenic_tRNA";
    public static final String ALLELIC_T_RNA_FEATURE_NAME = "allelic_pseudogenic_tRNA";

    public static final Set<String> T_RNA_EQUIVALENTS = Set.of(
            T_RNA_FEATURE_NAME,
            TRANS_SPLICED_TRANSCRIPT_T_RNA_FEATURE_NAME,
            PSEUDOGENIC_T_RNA_FEATURE_NAME,
            PROCESSED_T_RNA_FEATURE_NAME,
            UNPROCESSED_T_RNA_FEATURE_NAME,
            UNITARY_T_RNA_FEATURE_NAME,
            ALLELIC_T_RNA_FEATURE_NAME);
}
