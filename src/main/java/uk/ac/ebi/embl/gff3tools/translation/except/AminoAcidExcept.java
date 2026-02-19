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
package uk.ac.ebi.embl.gff3tools.translation.except;

import java.util.Map;
import uk.ac.ebi.embl.gff3tools.exception.TranslationException;

/**
 * Utility class for amino acid that is to be mapped when present in transl_except attribute.
 */
public class AminoAcidExcept {

    private static final Map<String, Character> AMINO_ACID_MAP = Map.ofEntries(
            Map.entry("ALA", 'A'),
            Map.entry("ARG", 'R'),
            Map.entry("ASN", 'N'),
            Map.entry("ASP", 'D'),
            Map.entry("CYS", 'C'),
            Map.entry("GLN", 'Q'),
            Map.entry("GLU", 'E'),
            Map.entry("GLY", 'G'),
            Map.entry("HIS", 'H'),
            Map.entry("ILE", 'I'),
            Map.entry("LEU", 'L'),
            Map.entry("LYS", 'K'),
            Map.entry("MET", 'M'),
            Map.entry("PHE", 'F'),
            Map.entry("PRO", 'P'),
            Map.entry("SER", 'S'),
            Map.entry("THR", 'T'),
            Map.entry("TRP", 'W'),
            Map.entry("TYR", 'Y'),
            Map.entry("VAL", 'V'),
            Map.entry("SEC", 'U'), // Selenocysteine
            Map.entry("PYL", 'O'), // Pyrrolysine
            Map.entry("TERM", '*'), // Stop codon
            Map.entry("TER", '*'), // Stop codon (alternative)
            Map.entry("OTHER", 'X') // Unknown
            );

    /**
     * Converts a 3-letter amino acid name to its single-letter code.
     *
     * @param name the amino acid name (case-insensitive), e.g., "Trp", "SEC", "TERM"
     * @return the single-letter code
     * @throws TranslationException if the amino acid name is unknown
     */
    public static Character toSingleLetter(String name) throws TranslationException {
        Character aminoAcid = AMINO_ACID_MAP.get(name.toUpperCase());
        if (aminoAcid == null) {
            throw new TranslationException("Unknown amino acid: " + name);
        }
        return aminoAcid;
    }
}
