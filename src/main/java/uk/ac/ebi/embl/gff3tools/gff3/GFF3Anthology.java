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

import java.util.List;
import java.util.Set;

public class GFF3Anthology {

    public static final String MAP_PEPTIDE_FEATURE_NAME = "map_peptide";
    public static final String SIG_PEPTIDE_FEATURE_NAME = "signal_peptide";
    public static final String CDS_FEATURE_NAME = "CDS";
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

    public static final String FF_PRODUCT_QUALIFIER = "product";
    public static final String FF_PSEUDO_QUALIFIER = "pseudo";
    public static final String FF_PSEUDOGENE_QUALIFIER = "pseudogene";
    public static final String FF_NOTE_QUALIFIER = "note";
}
