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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import uk.ac.ebi.embl.gff3tools.translation.TranslationException;

/**
 * Parses and represents a codon attribute value.
 * Format: (seq:"codon",aa:AminoAcidName)
 * Examples:
 *   (seq:"tga",aa:Trp)
 *   (seq:"aga",aa:Arg)
 */
@Getter
public class CodonExceptAttribute {

    // Pattern to match codon format with flexible whitespace
    // Groups: 1=codon sequence, 2=amino acid name
    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*\\(\\s*seq\\s*:\\s*\"?([^\"\\s,]+)\"?\\s*,\\s*aa\\s*:\\s*([^\\s,)]+)\\s*\\)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final String codon;
    private final Character aminoAcid;

    /**
     * Parses a codon attribute value.
     *
     * @param value the attribute value, e.g., "(seq:\"tga\",aa:Trp)"
     * @throws TranslationException if the format is invalid
     */
    public CodonExceptAttribute(String value) throws TranslationException {
        if (value == null || value.isBlank()) {
            throw new TranslationException("codon value cannot be null or empty");
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new TranslationException("Invalid codon format: " + value);
        }

        // Parse codon sequence (group 1)
        this.codon = matcher.group(1).toLowerCase();

        if (this.codon.length() != 3) {
            throw new TranslationException("Codon must be exactly 3 bases: " + this.codon);
        }

        // Parse amino acid (group 2)
        this.aminoAcid = AminoAcidExcept.toSingleLetter(matcher.group(2));
    }
}
