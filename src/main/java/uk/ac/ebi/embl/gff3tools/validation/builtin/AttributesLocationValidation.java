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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.translation.TranslationException;
import uk.ac.ebi.embl.gff3tools.translation.except.AminoAcidExcept;
import uk.ac.ebi.embl.gff3tools.translation.except.AntiCodonAttribute;
import uk.ac.ebi.embl.gff3tools.translation.except.TranslExceptAttribute;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

@Gff3Validation(name = "ATTRIBUTES_LOCATION")
public class AttributesLocationValidation implements Validation {

    private static final String INVALID_AMINO_ACID = "%s contains an invalid amino acid \"%s\" at location %s..%s";
    private static final String INVALID_LOCATION_RANGE = "%s location %s..%s is outside feature range %s..%s";
    private static final String INVALID_LOCATION_VALUE =
            "Invalid %s location: start must be > 0 and less than end at %s..%s";
    private static final String INVALID_LOCATION_SPAN = "%s location span must be \"%s\" at location %s";
    private static final String COMPLEMENT_STRAND_CONFLICT =
            "%s location is wrapped in complement() but the containing feature is on strand \"%s\": %s";

    /** Detects a {@code complement(...)} wrapper that survived {@code TRANSL_EXCEPT_COMPLEMENT}. */
    private static final Pattern POS_COMPLEMENT_PATTERN =
            Pattern.compile("\\(\\s*pos\\s*:\\s*complement\\(", Pattern.CASE_INSENSITIVE);

    @ValidationMethod(rule = "ANTI_CODON_LOCATION", type = ValidationType.ANNOTATION)
    public void validateAntiCodon(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        Map<String, List<GFF3Feature>> grouped = gff3Annotation.getFeatures().stream()
                .filter(f -> f.hasAttribute(ANTI_CODON))
                .collect(Collectors.groupingBy(feature -> feature.getId().orElse(feature.hashCodeString())));

        validateCodonAttribute(grouped, ANTI_CODON, line);
    }

    @ValidationMethod(
            rule = "TRANSL_EXCEPT_LOCATION",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void validateTranslExcept(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        Map<String, List<GFF3Feature>> grouped = gff3Annotation.getFeatures().stream()
                .filter(f -> f.hasAttribute(TRANSL_EXCEPT))
                .collect(Collectors.groupingBy(feature -> feature.getId().orElse(feature.hashCodeString())));

        validateCodonAttribute(grouped, TRANSL_EXCEPT, line);
    }

    @ValidationMethod(
            rule = "TRANSL_EXCEPT_STRAND_CONFLICT",
            description =
                    "Check that a complement(...) wrapper in a transl_except attribute location agrees with the strand",
            type = ValidationType.FEATURE,
            severity = RuleSeverity.ERROR)
    public void validateTranslExceptStrandConflict(GFF3Feature feature, int line) throws ValidationException {

        /** a "-" strand feature row can have a complement but does not have to,
         *  normalised in {@link uk.ac.ebi.embl.gff3tools.validation.fix.TranslExceptComplementFix} **/
        if (feature.isComplement()) {
            return;
        }

        for (String value : feature.getAttributeList(TRANSL_EXCEPT).orElse(List.of())) {

            if (value == null || !POS_COMPLEMENT_PATTERN.matcher(value).find()) {
                continue;
            }

            TranslExceptAttribute attribute;
            try {
                attribute = new TranslExceptAttribute(value);
            } catch (TranslationException e) {
                // Malformed values are already reported by {@link TRANSL_EXCEPT_LOCATION}.
                continue;
            }

            // A join is written as several rows sharing an ID, and the same attribute value is
            // copied onto every one of them. Only the row that actually spans the position owns
            // that value, so without this check a mixed-strand join would be rejected because of a
            // copy sitting on a row the position has nothing to do with.
            //
            // TRANSL_EXCEPT_LOCATION does not cover this: it asks whether *any* row of the group
            // spans the position, which stays true for all of them.
            if (attribute.getStartPosition() >= feature.getStart() && attribute.getEndPosition() <= feature.getEnd()) {
                throw new ValidationException(
                        line, COMPLEMENT_STRAND_CONFLICT.formatted(TRANSL_EXCEPT, feature.getStrand(), value));
            }
        }
    }

    private void validateCodonAttribute(Map<String, List<GFF3Feature>> groupedFeatures, String attribute, int line)
            throws ValidationException {
        try {
            for (Map.Entry<String, List<GFF3Feature>> entry : groupedFeatures.entrySet()) {

                List<GFF3Feature> fragments = entry.getValue();

                // Collect distinct attribute values in the features [Anticodon/Transl_except]
                List<String> values = fragments.stream()
                        .flatMap(f -> f.getAttributeList(attribute).orElse(List.of()).stream())
                        .distinct()
                        .toList();

                if (values.isEmpty()) continue;

                // To get min range from the fragments list
                long fullStart = fragments.stream()
                        .mapToLong(GFF3Feature::getStart)
                        .min()
                        .orElseThrow(); // as it is already guarded on the fragment list level

                // To get max range from the fragments list
                long fullEnd = fragments.stream()
                        .mapToLong(GFF3Feature::getEnd)
                        .max()
                        .orElseThrow(); // as it is already guarded on the fragment list level

                for (String value : values) {
                    String aminoAcidCode;
                    long start;
                    long end;

                    if (attribute.equalsIgnoreCase(ANTI_CODON)) {
                        AntiCodonAttribute a = new AntiCodonAttribute(value);
                        aminoAcidCode = a.getAminoAcidCode();
                        start = a.getStartPosition();
                        end = a.getEndPosition();
                    } else if (attribute.equalsIgnoreCase(TRANSL_EXCEPT)) {
                        TranslExceptAttribute t = new TranslExceptAttribute(value);
                        aminoAcidCode = t.getAminoAcidCode();
                        start = t.getStartPosition();
                        end = t.getEndPosition();
                    } else {
                        return;
                    }

                    if (!AminoAcidExcept.isValidAminoAcid(aminoAcidCode)) {
                        throw new ValidationException(
                                line, INVALID_AMINO_ACID.formatted(attribute, aminoAcidCode, fullStart, fullEnd));
                    }

                    long length = Math.max(end - start + 1, 0);

                    // Logical defensive check
                    if (start <= 0 || end <= 0 || start > end) {
                        throw new ValidationException(line, INVALID_LOCATION_VALUE.formatted(attribute, start, end));
                    }

                    // Fragment-level containment check
                    boolean insideFragment =
                            fragments.stream().anyMatch(f -> start >= f.getStart() && end <= f.getEnd());

                    if (!insideFragment) {
                        throw new ValidationException(
                                line, INVALID_LOCATION_RANGE.formatted(attribute, start, end, fullStart, fullEnd));
                    }

                    // Length must be 3 unless transl_except TERM
                    if (length != 3
                            && !(attribute.equalsIgnoreCase(TRANSL_EXCEPT) && TERM_AMINO_ACID.equals(aminoAcidCode))) {
                        throw new ValidationException(line, INVALID_LOCATION_SPAN.formatted(attribute, 3, fullStart));
                    }
                }
            }
        } catch (TranslationException e) {
            throw new ValidationException(line, e.getMessage());
        }
    }
}
