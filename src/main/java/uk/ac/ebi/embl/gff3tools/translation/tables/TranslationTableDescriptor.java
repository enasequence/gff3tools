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
package uk.ac.ebi.embl.gff3tools.translation.tables;

import lombok.Getter;

/**
 * NCBI genetic code table descriptors.
 * Contains all NCBI translation tables (1-33) with their amino acid and start codon definitions.
 *
 * Note: Tables 7, 8, 17-20 were removed by NCBI
 */
@Getter
enum TranslationTableDescriptor {
    STANDARD(
            1,
            "The Standard Code",
            "FFLLSSSSYY**CC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "---M---------------M---------------M----------------------------"),

    VERTEBRATE_MITOCHONDRIAL(
            2,
            "The Vertebrate Mitochondrial Code",
            "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNKKSS**VVVVAAAADDEEGGGG",
            "--------------------------------MMMM---------------M------------"),

    YEAST_MITOCHONDRIAL(
            3,
            "The Yeast Mitochondrial Code",
            "FFLLSSSSYY**CCWWTTTTPPPPHHQQRRRRIIMMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "----------------------------------MM----------------------------"),

    MOLD_PROTOZOAN_COELENTERATE_MITOCHONDRIAL(
            4,
            "The Mold, Protozoan, and Coelenterate Mitochondrial Code and the Mycoplasma/Spiroplasma Code",
            "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "--MM---------------M------------MMMM---------------M------------"),

    INVERTEBRATE_MITOCHONDRIAL(
            5,
            "The Invertebrate Mitochondrial Code",
            "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNKKSSSSVVVVAAAADDEEGGGG",
            "---M----------------------------MMMM---------------M------------"),

    CILIATE_DASYCLADACEAN_HEXAMITA_NUCLEAR(
            6,
            "The Ciliate, Dasycladacean and Hexamita Nuclear Code",
            "FFLLSSSSYYQQCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "-----------------------------------M----------------------------"),

    ECHINODERM_FLATWORM_MITOCHONDRIAL(
            9,
            "The Echinoderm and Flatworm Mitochondrial Code",
            "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNNKSSSSVVVVAAAADDEEGGGG",
            "-----------------------------------M---------------M------------"),

    EUPLOTID_NUCLEAR(
            10,
            "The Euplotid Nuclear Code",
            "FFLLSSSSYY**CCCWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "-----------------------------------M----------------------------"),

    BACTERIAL_PLASTID(
            11,
            "The Bacterial and Plant Plastid Code",
            "FFLLSSSSYY**CC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "---M---------------M------------MMMM---------------M------------"),

    ALTERNATIVE_YEAST_NUCLEAR(
            12,
            "The Alternative Yeast Nuclear Code",
            "FFLLSSSSYY**CC*WLLLSPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "-------------------M---------------M----------------------------"),

    ASCIDIAN_MITOCHONDRIAL(
            13,
            "The Ascidian Mitochondrial Code",
            "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNKKSSGGVVVVAAAADDEEGGGG",
            "---M------------------------------MM---------------M------------"),

    ALTERNATIVE_FLATWORM_MITOCHONDRIAL(
            14,
            "The Alternative Flatworm Mitochondrial Code",
            "FFLLSSSSYYY*CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNNKSSSSVVVVAAAADDEEGGGG",
            "-----------------------------------M----------------------------"),

    BLEPHARISMA_MACRONUCLEAR(
            15,
            "Blepharisma Macronuclear",
            "FFLLSSSSYY*QCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "----------*---*--------------------M----------------------------"),

    CHLOROPHYCEAN_MITOCHONDRIAL(
            16,
            "Chlorophycean Mitochondrial Code",
            "FFLLSSSSYY*LCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "-----------------------------------M----------------------------"),

    TREMATODE_MITOCHONDRIAL(
            21,
            "Trematode Mitochondrial Code",
            "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIMMTTTTNNNKSSSSVVVVAAAADDEEGGGG",
            "-----------------------------------M---------------M------------"),

    SCENEDESMUS_OBLIQUUS_MITOCHONDRIAL(
            22,
            "Scenedesmus obliquus mitochondrial Code",
            "FFLLSS*SYY*LCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "-----------------------------------M----------------------------"),

    THRAUSTOCHYTRIUM_MITOCHONDRIAL(
            23,
            "Thraustochytrium Mitochondrial Code",
            "FF*LSSSSYY**CC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "--------------------------------M--M---------------M------------"),

    PTEROBRANCHIA_MITOCHONDRIAL(
            24,
            "Pterobranchia Mitochondrial Code",
            "FFLLSSSSYY**CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSSKVVVVAAAADDEEGGGG",
            "---M---------------M---------------M----------------------------"),

    CANDIDATE_DIVISION_SR1_GRACILIBACTERIA(
            25,
            "Candidate Division SR1 and Gracilibacteria",
            "FFLLSSSSYY**CCGWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "---M---------------M---------------M----------------------------"),

    PACHYSOLEN_TANNOPHILUS_NUCLEAR(
            26,
            "Pachysolen tannophilus Nuclear",
            "FFLLSSSSYY**CC*WLLLAPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "----------**--*----M---------------M----------------------------"),

    KARYORELICT_NUCLEAR(
            27,
            "Karyorelict Nuclear",
            "FFLLSSSSYYQQCCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "--------------*--------------------M----------------------------"),

    CONDYLOSTOMA_NUCLEAR(
            28,
            "Condylostoma Nuclear",
            "FFLLSSSSYYQQCCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "----------**--*--------------------M----------------------------"),

    MESODINIUM_NUCLEAR(
            29,
            "Mesodinium Nuclear",
            "FFLLSSSSYYYYCC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "--------------*--------------------M----------------------------"),

    PERITRICH_NUCLEAR(
            30,
            "Peritrich Nuclear",
            "FFLLSSSSYYEECC*WLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "--------------*--------------------M----------------------------"),

    BLASTOCRITHIDIA_NUCLEAR(
            31,
            "Blastocrithidia Nuclear",
            "FFLLSSSSYYEECCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSRRVVVVAAAADDEEGGGG",
            "----------**-----------------------M----------------------------"),

    CEPHALODISCIDAE_MITOCHONDRIAL(
            33,
            "Cephalodiscidae Mitochondrial UAA-Tyr Code",
            "FFLLSSSSYYY*CCWWLLLLPPPPHHQQRRRRIIIMTTTTNNKKSSSKVVVVAAAADDEEGGGG",
            "---M-------*-------M---------------M---------------M------------");

    private final int number;
    private final String name;
    private final String aminoAcids;
    private final String starts;

    TranslationTableDescriptor(int number, String name, String aminoAcids, String starts) {
        this.number = number;
        this.name = name;
        this.aminoAcids = aminoAcids;
        this.starts = starts;
    }
}
