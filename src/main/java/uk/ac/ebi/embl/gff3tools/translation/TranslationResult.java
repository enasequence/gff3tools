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
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Contains the result of a CDS translation operation, including the translated amino acid sequence
 * and any validation messages.
 */
@Getter
@Setter(AccessLevel.PROTECTED)
public class TranslationResult {

    private boolean fixedFivePrimePartial = false;

    private boolean fixedThreePrimePartial = false;

    private boolean fixedPseudo = false;

    private boolean fixedDegenerateStartCodon = false;

    private List<Codon> codons;

    private String trailingBases;

    private int conceptualTranslationCodons = 0;

    private int translationLength;

    private int baseCount;

    private final List<String> errors = new ArrayList<>();

    /**
     * Returns the DNA/RNA sequence (codons + trailing bases).
     */
    public String getSequence() {
        if (codons == null) {
            return "";
        }
        StringBuilder sequence = new StringBuilder();
        for (Codon codon : codons) {
            sequence.append(codon.getCodon());
        }
        if (trailingBases != null) {
            sequence.append(trailingBases);
        }
        return sequence.toString();
    }

    /**
     * Returns the full translation including stop codons and trailing bases.
     *
     * @return the translation including stop codons and trailing bases
     */
    public String getTranslation() {
        if (codons == null) {
            return "";
        }
        StringBuilder translation = new StringBuilder();
        for (Codon codon : codons) {
            translation.append(codon.getAminoAcid());
        }
        return translation.toString();
    }

    /**
     * Returns the conceptual translation excluding stop codons and trailing bases.
     * This is the protein sequence that would be produced.
     *
     * @return the translation excluding stop codons and trailing bases
     */
    public String getConceptualTranslation() {
        if (codons == null) {
            return "";
        }
        StringBuilder translation = new StringBuilder();
        for (int i = 0; i < conceptualTranslationCodons; ++i) {
            Codon codon = codons.get(i);
            translation.append(codon.getAminoAcid());
        }
        return translation.toString();
    }

    public void setTranslationBaseCount(int baseCount) {
        this.baseCount = baseCount;
    }

    // Error handling methods

    public void addError(String error) {
        if (error != null && !error.isBlank()) {
            errors.add(error);
        }
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isValid() {
        return !hasErrors();
    }

    public String getErrorMessages() {
        return String.join("\n", errors);
    }
}
