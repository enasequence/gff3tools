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
public class AntiCodonAttribute {

    // Pattern to match anticodon value
    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*\\(\\s*pos:\\s*([^,]+)\\s*,\\s*aa\\s*:\\s*([^,\\s]+)(?:\\s*,\\s*seq\\s*:\\s*([^\\s)]+))?\\s*\\)\\s*$",
            Pattern.CASE_INSENSITIVE);

    // Pattern to parse position: either "start..end" or just "start"
    private static final Pattern POSITION_PATTERN = Pattern.compile("^\\s*(\\d+)(?:\\s*\\.\\.\\s*(\\d+))?\\s*$");

    // Handles complement
    private static final Pattern COMPLEMENT_PATTERN =
            Pattern.compile("^complement\\((.+)\\)$", Pattern.CASE_INSENSITIVE);

    private final long startPosition;
    private final long endPosition;
    private final String aminoAcidCode;
    /**
     * Parses a anti codon attribute value.
     *
     * @param value the attribute value, e.g., "(seq:\"tga\",aa:Trp)"
     * @throws TranslationException if the format is invalid
     */
    public AntiCodonAttribute(String value) throws TranslationException {
        if (value == null || value.isBlank()) {
            throw new TranslationException("Anticodon value cannot be null or empty");
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new TranslationException("Invalid Anticodon format: " + value);
        }

        try {
            Matcher posMatcher = getPositionMatcher(matcher);
            this.startPosition = Long.parseLong(posMatcher.group(1));
            String endGroup = posMatcher.group(2);
            this.endPosition = endGroup != null ? Long.parseLong(endGroup) : this.startPosition;

        } catch (NumberFormatException e) {
            throw new TranslationException("Invalid numeric value in anticodon: " + value);
        }

        this.aminoAcidCode = matcher.group(2).trim();
    }

    private static Matcher getPositionMatcher(Matcher matcher) throws TranslationException {
        String positionString = matcher.group(1).trim();

        // Remove complement(...) if present
        Matcher complementMatcher = COMPLEMENT_PATTERN.matcher(positionString);
        if (complementMatcher.matches()) {
            positionString = complementMatcher.group(1);
        }

        Matcher posMatcher = POSITION_PATTERN.matcher(positionString);
        if (!posMatcher.matches()) {
            throw new TranslationException("Invalid anticodon position: " + positionString);
        }
        return posMatcher;
    }
}
