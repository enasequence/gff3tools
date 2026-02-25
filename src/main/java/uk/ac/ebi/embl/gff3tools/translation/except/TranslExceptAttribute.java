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
 * Parses and represents a transl_except attribute value.
 * Format: (pos:start..end,aa:AminoAcidName)
 * Examples:
 *   (pos:213..215,aa:Trp)
 *   (pos:213..215,aa:Sec)
 *   (pos:1,aa:Met)
 */
@Getter
public class TranslExceptAttribute {

    // Pattern to match transl_except format with flexible whitespace
    // Groups: 1=position (e.g., "213..215" or "213"), 2=amino acid name
    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*\\(\\s*pos\\s*:\\s*([^,]+)\\s*,\\s*aa\\s*:\\s*([^\\s,)]+)\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);

    // Pattern to parse position: either "start..end" or just "start"
    private static final Pattern POSITION_PATTERN = Pattern.compile("^\\s*(\\d+)(?:\\s*\\.\\.\\s*(\\d+))?\\s*$");

    private final Integer startPosition;
    private final Integer endPosition;
    private final String aminoAcidCode;
    private final Character aminoAcidLetter;

    /**
     * Parses a transl_except attribute value.
     *
     * @param value the attribute value, e.g., "(pos:213..215,aa:Trp)"
     * @throws TranslationException if the format is invalid
     */
    public TranslExceptAttribute(String value) throws TranslationException {
        if (value == null || value.isBlank()) {
            throw new TranslationException("transl_except value cannot be null or empty");
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new TranslationException("Invalid transl_except format: " + value);
        }

        try {
            // Parse position (group 1)
            String positionStr = matcher.group(1);
            Matcher posMatcher = POSITION_PATTERN.matcher(positionStr);
            if (!posMatcher.matches()) {
                throw new TranslationException("Invalid position in transl_except: " + positionStr);
            }

            this.startPosition = Integer.parseInt(posMatcher.group(1));
            String endGroup = posMatcher.group(2);
            this.endPosition = endGroup != null ? Integer.parseInt(endGroup) : startPosition;

            this.aminoAcidCode = matcher.group(2).trim();
            // Parse amino acid (group 2)
            this.aminoAcidLetter = AminoAcidExcept.getAminoAcidLetter(matcher.group(2));

        } catch (NumberFormatException e) {
            throw new TranslationException("Invalid position in transl_except: " + value, e);
        }
    }
}
