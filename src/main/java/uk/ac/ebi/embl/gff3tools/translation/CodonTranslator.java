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
 * Translates a codon to an amino acid. The bases are encoded using lower case single letter
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
        addAmbiguousBase('a', 'a');
        addAmbiguousBase('t', 't');
        addAmbiguousBase('c', 'c');
        addAmbiguousBase('g', 'g');

        // IUPAC ambiguous bases
        addAmbiguousBase('r', 'g'); // R = puRine (A or G)
        addAmbiguousBase('r', 'a');
        addAmbiguousBase('y', 't'); // Y = pYrimidine (C or T)
        addAmbiguousBase('y', 'c');
        addAmbiguousBase('m', 'a'); // M = aMino (A or C)
        addAmbiguousBase('m', 'c');
        addAmbiguousBase('k', 'g'); // K = Keto (G or T)
        addAmbiguousBase('k', 't');
        addAmbiguousBase('s', 'g'); // S = Strong (G or C)
        addAmbiguousBase('s', 'c');
        addAmbiguousBase('w', 'a'); // W = Weak (A or T)
        addAmbiguousBase('w', 't');
        addAmbiguousBase('h', 'a'); // H = not G (A, C, or T)
        addAmbiguousBase('h', 'c');
        addAmbiguousBase('h', 't');
        addAmbiguousBase('b', 'g'); // B = not A (G, T, or C)
        addAmbiguousBase('b', 't');
        addAmbiguousBase('b', 'c');
        addAmbiguousBase('v', 'g'); // V = not T (G, C, or A)
        addAmbiguousBase('v', 'c');
        addAmbiguousBase('v', 'a');
        addAmbiguousBase('d', 'g'); // D = not C (G, A, or T)
        addAmbiguousBase('d', 'a');
        addAmbiguousBase('d', 't');
        addAmbiguousBase('n', 'g'); // N = aNy (G, A, T, or C)
        addAmbiguousBase('n', 'a');
        addAmbiguousBase('n', 't');
        addAmbiguousBase('n', 'c');

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

    public CodonTranslator(Integer translationTable) throws TranslationException {
        this.translationTable = TranslationTableFactory.getInstance().getTranslationTable(translationTable);
        if (this.translationTable == null) {
            TranslationException.throwError("Invalid translation table");
        }
    }

    public void addCodonException(String codon, Character aminoAcid) {
        codonExceptionMap.put(codon.toLowerCase(), aminoAcid);
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
            Character newAminoAcid = codonExceptionMap.get(expandedCodon);
            if (newAminoAcid == null) {
                newAminoAcid = codonMap.get(expandedCodon);
            }

            if (newAminoAcid == null) {
                TranslationException.throwError("Unable to translate codon: " + codonString);
            }

            aminoAcid = reconcileAminoAcids(aminoAcid, newAminoAcid);
        }

        if (aminoAcid == null) {
            TranslationException.throwError("Unable to translate codon: " + codonString);
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
                TranslationException.throwError("Unable to translate codon: " + codonString);
            }
            if (newAminoAcid.equals(aminoAcid)) {
                return true;
            }
        }
        return false;
    }

    // All base characters (a, t, c, g and IUPAC ambiguity codes r, y, m, k, s, w, h, b, v, d, n)
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

    private Character reconcileAminoAcids(Character existing, Character newAA) {
        if (existing == null) {
            return newAA;
        }
        if (existing.equals(newAA)) {
            return existing;
        }
        Character ambiguousExisting = AMBIGUOUS_AMINO_ACID_MAP.get(existing);
        Character ambiguousNew = AMBIGUOUS_AMINO_ACID_MAP.get(newAA);
        if (ambiguousExisting != null && ambiguousExisting.equals(ambiguousNew)) {
            return ambiguousExisting;
        }
        return 'X'; // Unknown amino acid
    }
}
