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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

/**
 * Checks the sequence-region id, which on FASTA and GFF3 input is the submitter's own sequence
 * identifier. On flatfile input it is an ENA accession, which always passes.
 */
@Gff3Validation(name = "SUBMITTER_SEQ_ID")
public class SubmitterSeqIdValidation implements Validation {
    public static final String SUBMITTER_SEQ_ID_FORMAT_RULE = "SUBMITTER_SEQ_ID_FORMAT";

    static final int MAX_LENGTH = 50;

    private static final Pattern ILLEGAL_CHARACTER = Pattern.compile("[\\s>\\[\\]|\"]");

    /**
     * An annotation without a sequence region is skipped: the GFF3 reader already reports its
     * undefined seqid.
     */
    @ValidationMethod(
            rule = SUBMITTER_SEQ_ID_FORMAT_RULE,
            description =
                    "Check that the submitter's sequence identifier is shorter than 51 characters and uses only characters permitted by INSDC",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void validateSubmitterSeqIdFormat(GFF3Annotation annotation, int line) throws ValidationException {
        GFF3SequenceRegion sequenceRegion = annotation.getSequenceRegion();
        if (sequenceRegion == null) {
            return;
        }

        String seqId = sequenceRegion.accessionId();
        List<String> errors = new ArrayList<>();

        if (seqId.length() > MAX_LENGTH) {
            errors.add("it is %d characters long, but must be fewer than %d characters"
                    .formatted(seqId.length(), MAX_LENGTH + 1));
        }

        Set<String> illegalCharacters = findIllegalCharacters(seqId);
        if (!illegalCharacters.isEmpty()) {
            errors.add(
                    "it contains characters not permitted by INSDC: %s (spaces, '>', '[', ']', '|' and '\"' are not allowed)"
                            .formatted(String.join(", ", illegalCharacters)));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(
                    SUBMITTER_SEQ_ID_FORMAT_RULE,
                    line,
                    "Sequence name '%s' has the following violations: %s.".formatted(seqId, String.join("; ", errors)));
        }
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
        return Character.isWhitespace(character.charAt(0)) ? "whitespace" : "'" + character + "'";
    }
}
