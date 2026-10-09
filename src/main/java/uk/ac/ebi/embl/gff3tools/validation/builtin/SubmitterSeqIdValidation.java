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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

@Gff3Validation(name = "SUBMITTER_SEQ_ID")
public class SubmitterSeqIdValidation implements Validation {
    public static final String SUBMITTER_SEQ_ID_FORMAT_RULE = "SUBMITTER_SEQ_ID_FORMAT";

    static final int MAX_LENGTH = 50;

    private static final Pattern ILLEGAL_CHARACTER = Pattern.compile("[\\s>\\[\\]|\"]");

    @ValidationMethod(
            rule = SUBMITTER_SEQ_ID_FORMAT_RULE,
            description =
                    "Check that the submitter's sequence identifier is shorter than 51 characters and uses only characters permitted by INSDC",
            type = ANNOTATION,
            severity = RuleSeverity.OFF,
            priority = ValidationPriority.CRITICAL)
    public void validateSubmitterSeqIdFormat(GFF3Annotation annotation, int line) throws ValidationException {
        String seqId = submitterSeqId(annotation);
        if (seqId == null || seqId.isEmpty()) {
            throw new ValidationException(
                    SUBMITTER_SEQ_ID_FORMAT_RULE, line, "The submitter's sequence identifier must not be empty.");
        }

        if (seqId.length() > MAX_LENGTH) {
            throw new ValidationException(
                    SUBMITTER_SEQ_ID_FORMAT_RULE,
                    line,
                    "Sequence name '%s' is %d characters long. The submitter's sequence identifier must be fewer than %d characters."
                            .formatted(seqId, seqId.length(), MAX_LENGTH + 1));
        }

        Set<String> illegalCharacters = findIllegalCharacters(seqId);
        if (!illegalCharacters.isEmpty()) {
            throw new ValidationException(
                    SUBMITTER_SEQ_ID_FORMAT_RULE,
                    line,
                    "Sequence name '%s' contains characters not permitted by INSDC: %s. The submitter's sequence identifier must not contain spaces, '>', '[', ']', '|' or '\"'."
                            .formatted(seqId, String.join(", ", illegalCharacters)));
        }
    }

    private static String submitterSeqId(GFF3Annotation annotation) {
        GFF3SequenceRegion sequenceRegion = annotation.getSequenceRegion();
        return sequenceRegion != null ? sequenceRegion.accessionId() : annotation.getAccession();
    }

    private static Set<String> findIllegalCharacters(String seqId) {
        Set<String> illegalCharacters = new LinkedHashSet<>();
        Matcher matcher = ILLEGAL_CHARACTER.matcher(seqId);
        while (matcher.find()) {
            illegalCharacters.add(describe(matcher.group()));
        }
        return illegalCharacters;
    }

    private static String describe(String character) {
        return character.chars().anyMatch(Character::isWhitespace) ? "whitespace" : "'" + character + "'";
    }
}
