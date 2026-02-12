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
import java.util.stream.Collectors;
import uk.ac.ebi.embl.api.entry.location.CompoundLocation;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.qualifier.AminoAcid;
import uk.ac.ebi.embl.api.entry.qualifier.AnticodonQualifier;
import uk.ac.ebi.embl.api.entry.qualifier.TranslExceptQualifier;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "ANTI_CODON")
public class AntiCodonValidation extends Validation {

    private static final String INVALID_FORMAT = "Invalid %s format \"%s\"";
    private static final String INVALID_AMINO_ACID = "%s contains an invalid amino acid at location %s";
    private static final String INVALID_AMINO_ACID_VALUE =
            "Invalid amino acid \"%s\" at location %s..%s. Expected \"%s\"";
    private static final String INVALID_LOCATION_RANGE = "%s location %s..%s is outside feature range %s..%s";
    private static final String INVALID_LOCATION_VALUE =
            "Invalid %s location: start must be > 0 and less than end at %s..%s";
    private static final String INVALID_LOCATION_SPAN = "%s location span must be \"%s\" at location %s";

    @ValidationMethod(rule = "ANTI_CODON_ATTRIBUTE", type = ValidationType.ANNOTATION)
    public void validateAntiCodon(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        Map<String, List<GFF3Feature>> grouped = gff3Annotation.getFeatures().stream()
                .filter(f -> f.hasAttribute(ANTI_CODON))
                .collect(Collectors.groupingBy(feature -> feature.getId().orElse(feature.hashCodeString())));

        validateCodonAttribute(grouped, ANTI_CODON, line);
    }

    @ValidationMethod(rule = "TRANSL_EXCEPT_ATTRIBUTE", type = ValidationType.ANNOTATION)
    public void validateTranslExcept(GFF3Annotation gff3Annotation, int line) throws ValidationException {
        Map<String, List<GFF3Feature>> grouped = gff3Annotation.getFeatures().stream()
                .filter(f -> f.hasAttribute(TRANSL_EXCEPT))
                .collect(Collectors.groupingBy(feature -> feature.getId().orElse(feature.hashCodeString())));

        validateCodonAttribute(grouped, TRANSL_EXCEPT, line);
    }

    private void validateCodonAttribute(Map<String, List<GFF3Feature>> groupedFeatures, String attribute, int line)
            throws ValidationException {
        AminoAcid aminoAcid;
        CompoundLocation<Location> location;

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

                    if (attribute.equalsIgnoreCase(ANTI_CODON)) {
                        AnticodonQualifier q = new AnticodonQualifier(value);
                        aminoAcid = q.getAminoAcid();
                        location = q.getLocations();
                    } else if (attribute.equalsIgnoreCase(TRANSL_EXCEPT)) {
                        TranslExceptQualifier q = new TranslExceptQualifier(value);
                        aminoAcid = q.getAminoAcid();
                        location = q.getLocations();
                    } else {
                        return;
                    }

                    if (aminoAcid == null) {
                        throw new ValidationException(line, INVALID_AMINO_ACID.formatted(attribute, fullStart));
                    }

                    Long startObj = location.getMinPosition();
                    Long endObj = location.getMaxPosition();

                    // Defensive guard: qualifier parsing may produce a null location
                    if (startObj == null || endObj == null) {
                        throw new ValidationException(line, INVALID_FORMAT.formatted(attribute, value));
                    }

                    long start = startObj;
                    long end = endObj;
                    long length = location.getLength();

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
                            && !(attribute.equalsIgnoreCase(TRANSL_EXCEPT)
                                    && TERM_AMINO_ACID.equals(aminoAcid.getAbbreviation()))) {
                        throw new ValidationException(line, INVALID_LOCATION_SPAN.formatted(attribute, 3, fullStart));
                    }
                }
            }

        } catch (uk.ac.ebi.embl.api.validation.ValidationException e) {
            throw new ValidationException(line, INVALID_FORMAT.formatted(attribute, e.getMessage()));
        }
    }

    @ValidationMethod(rule = "AMINO_ACID_MISMATCH", type = ValidationType.FEATURE, severity = RuleSeverity.WARN)
    public void validateAminoAcidMismatch(GFF3Feature feature, int line) throws ValidationException {
        String value = null;
        String attribute = null;

        if (feature.hasAttribute(ANTI_CODON)) {
            value = feature.getAttribute(ANTI_CODON).orElse(null);
            attribute = ANTI_CODON;
        } else if (feature.hasAttribute(TRANSL_EXCEPT)) {
            value = feature.getAttribute(TRANSL_EXCEPT).orElse(null);
            attribute = TRANSL_EXCEPT;
        }

        if (value == null) return;

        try {
            AminoAcid aminoAcid;
            String aminoAcidString;

            if (attribute.equals(ANTI_CODON)) {
                AnticodonQualifier q = new AnticodonQualifier(value);
                aminoAcid = q.getAminoAcid();
                aminoAcidString = q.getAminoAcidString();
            } else {
                TranslExceptQualifier q = new TranslExceptQualifier(value);
                aminoAcid = q.getAminoAcid();
                aminoAcidString = q.getAminoAcidString();
            }

            if (aminoAcid != null
                    && aminoAcid.getAbbreviation() != null
                    && !aminoAcid.getAbbreviation().equals(aminoAcidString)) {
                throw new ValidationException(
                        line,
                        INVALID_AMINO_ACID_VALUE.formatted(
                                aminoAcidString, feature.getStart(), feature.getEnd(), aminoAcid.getAbbreviation()));
            }
        } catch (uk.ac.ebi.embl.api.validation.ValidationException e) {
            throw new ValidationException(line, INVALID_FORMAT.formatted(attribute, e.getMessage()));
        }
    }
}
