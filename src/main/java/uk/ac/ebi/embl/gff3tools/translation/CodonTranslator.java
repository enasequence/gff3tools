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
package uk.ac.ebi.embl.gff3tools.translation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import uk.ac.ebi.embl.gff3tools.translation.tables.TranslationTable;
import uk.ac.ebi.embl.gff3tools.translation.tables.TranslationTableFactory;

/**
 * Translates a codon to an amino acid. The bases are encoded using upper case single letter
 * JCBN abbreviations and the amino acids are encoded using upper case single letter JCBN abbreviations.
 */
public class CodonTranslator {

    @Getter
    private TranslationTable translationTable;

    private static final Map<Character, List<Character>> AMBIGUOUS_BASE_MAP = new HashMap<>();
    private static final Map<Character, Character> AMBIGUOUS_AMINO_ACID_MAP = new HashMap<>();

    // Map of exception amino acid for a specific codon
    private final Map<String, Character> codonExceptionMap = new HashMap<>();

    static {
        // Standard bases
        addAmbiguousBase('A', 'A');
        addAmbiguousBase('T', 'T');
        addAmbiguousBase('C', 'C');
        addAmbiguousBase('G', 'G');

        // IUPAC ambiguous bases
        addAmbiguousBase('R', 'G'); // R = puRine (A or G)
        addAmbiguousBase('R', 'A');
        addAmbiguousBase('Y', 'T'); // Y = pYrimidine (C or T)
        addAmbiguousBase('Y', 'C');
        addAmbiguousBase('M', 'A'); // M = aMino (A or C)
        addAmbiguousBase('M', 'C');
        addAmbiguousBase('K', 'G'); // K = Keto (G or T)
        addAmbiguousBase('K', 'T');
        addAmbiguousBase('S', 'G'); // S = Strong (G or C)
        addAmbiguousBase('S', 'C');
        addAmbiguousBase('W', 'A'); // W = Weak (A or T)
        addAmbiguousBase('W', 'T');
        addAmbiguousBase('H', 'A'); // H = not G (A, C, or T)
        addAmbiguousBase('H', 'C');
        addAmbiguousBase('H', 'T');
        addAmbiguousBase('B', 'G'); // B = not A (G, T, or C)
        addAmbiguousBase('B', 'T');
        addAmbiguousBase('B', 'C');
        addAmbiguousBase('V', 'G'); // V = not T (G, C, or A)
        addAmbiguousBase('V', 'C');
        addAmbiguousBase('V', 'A');
        addAmbiguousBase('D', 'G'); // D = not C (G, A, or T)
        addAmbiguousBase('D', 'A');
        addAmbiguousBase('D', 'T');
        addAmbiguousBase('N', 'G'); // N = aNy (G, A, T, or C)
        addAmbiguousBase('N', 'A');
        addAmbiguousBase('N', 'T');
        addAmbiguousBase('N', 'C');

        // Ambiguous amino acids: maps each amino acid to its ambiguous group
        // B = Aspartic acid (D) or Asparagine (N)
        // Z = Glutamic acid (E) or Glutamine (Q)
        // J = Isoleucine (I) or Leucine (L)
        registerAmbiguousAminoAcidGroup('B', 'N', 'D');
        registerAmbiguousAminoAcidGroup('Z', 'Q', 'E');
        registerAmbiguousAminoAcidGroup('J', 'I', 'L');
    }

    private static void addAmbiguousBase(Character ambiguousBase, Character unAmbiguousBase) {
        List<Character> unAmbiguousBases = AMBIGUOUS_BASE_MAP.computeIfAbsent(ambiguousBase, k -> new ArrayList<>());
        unAmbiguousBases.add(unAmbiguousBase);
    }

    private static void registerAmbiguousAminoAcidGroup(Character ambiguousCode, Character... members) {
        AMBIGUOUS_AMINO_ACID_MAP.put(ambiguousCode, ambiguousCode);
        for (Character member : members) {
            AMBIGUOUS_AMINO_ACID_MAP.put(member, ambiguousCode);
        }
    }

    public CodonTranslator(int translationTable) throws TranslationException {
        this.translationTable = TranslationTableFactory.getInstance().getTranslationTable(translationTable);
        if (this.translationTable == null) {
            throw new TranslationException("Invalid translation table");
        }
    }

    public void addCodonException(String codon, Character aminoAcid) {
        codonExceptionMap.put(codon.toUpperCase(), aminoAcid);
    }

    public char translateStartCodon(String codonString) throws TranslationException {
        return translateCodon(codonString, translationTable.getStartCodonMap());
    }

    public char translateOtherCodon(String codonString) throws TranslationException {
        return translateCodon(codonString, translationTable.getOtherCodonMap());
    }

    private char translateCodon(String codonString, Map<String, Character> codonMap) throws TranslationException {
        List<String> expandedCodons = expandToUnambiguousCodons(codonString);
        Character aminoAcid = null;

        for (String expandedCodon : expandedCodons) {

            Character newAminoAcid = codonExceptionMap.getOrDefault(expandedCodon, codonMap.get(expandedCodon));
            if (newAminoAcid == null) {
                throw new TranslationException("Unable to translate codon: " + codonString);
            }

            aminoAcid = reconcileAminoAcids(aminoAcid, newAminoAcid);
        }

        if (aminoAcid == null) {
            throw new TranslationException("Unable to translate codon: " + codonString);
        }

        return aminoAcid;
    }

    public boolean isAmbiguous(String codonString) {
        return expandToUnambiguousCodons(codonString).size() > 1;
    }

    public boolean isDegenerateStartCodon(String codonString) throws TranslationException {
        return isDegenerateCodon(codonString, translationTable.getStartCodonMap(), 'M');
    }

    public boolean isDegenerateStopCodon(String codonString) throws TranslationException {
        return isDegenerateCodon(codonString, translationTable.getOtherCodonMap(), '*');
    }

    private boolean isDegenerateCodon(String codonString, Map<String, Character> codonMap, Character aminoAcid)
            throws TranslationException {
        List<String> expandedCodons = expandToUnambiguousCodons(codonString);
        for (String expandedCodon : expandedCodons) {
            Character newAminoAcid = codonExceptionMap.get(expandedCodon);
            if (newAminoAcid == null) {
                newAminoAcid = codonMap.get(expandedCodon);
            }
            if (newAminoAcid == null) {
                throw new TranslationException("Unable to translate codon: " + codonString);
            }
            if (newAminoAcid.equals(aminoAcid)) {
                return true;
            }
        }
        return false;
    }

    // All base characters (A, T, C, G and IUPAC ambiguity codes R, Y, M, K, S, W, H, B, V, D, N)
    // are guaranteed to be present in AMBIGUOUS_BASE_MAP. Input sequences are validated by
    // Translator.validateSequenceBases before reaching this method.
    private List<String> expandToUnambiguousCodons(String codonString) {
        List<String> result = new ArrayList<>();
        List<Character> bases1 = AMBIGUOUS_BASE_MAP.get(codonString.charAt(0));
        List<Character> bases2 = AMBIGUOUS_BASE_MAP.get(codonString.charAt(1));
        List<Character> bases3 = AMBIGUOUS_BASE_MAP.get(codonString.charAt(2));

        char[] bases = new char[3];
        for (char b1 : bases1) {
            bases[0] = b1;
            for (char b2 : bases2) {
                bases[1] = b2;
                for (char b3 : bases3) {
                    bases[2] = b3;
                    result.add(new String(bases));
                }
            }
        }
        return result;
    }

    private Character reconcileAminoAcids(Character existing, Character incoming) {
        if (existing == null) return incoming;
        if (existing.equals(incoming)) return existing;

        char unknown = 'X'; // Unknown amino acid
        char ambiguousExisting = AMBIGUOUS_AMINO_ACID_MAP.getOrDefault(existing, unknown);
        char ambiguousNew = AMBIGUOUS_AMINO_ACID_MAP.getOrDefault(incoming, unknown);

        return (ambiguousExisting == ambiguousNew) ? ambiguousExisting : unknown;
    }
}
