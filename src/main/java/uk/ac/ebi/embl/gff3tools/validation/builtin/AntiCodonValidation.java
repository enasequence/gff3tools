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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.TERM_AMINO_ACID;

import java.util.*;
import uk.ac.ebi.embl.api.entry.location.CompoundLocation;
import uk.ac.ebi.embl.api.entry.location.Location;
import uk.ac.ebi.embl.api.entry.qualifier.AminoAcid;
import uk.ac.ebi.embl.api.entry.qualifier.AnticodonQualifier;
import uk.ac.ebi.embl.api.entry.qualifier.TranslExceptQualifier;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "ANTI_CODON")
public class AntiCodonValidation extends Validation {

    private static final String INVALID_FORMAT = "Invalid %s format \"%s\"";
    private static final String INVALID_AMINO_ACID = "%s contains illegal amino acid.";
    private static final String INVALID_AMINO_ACID_VALUE =
            "Illegal amino acid \"%s\" should be changed to legal amino acid \"%s\"";
    private static final String INVALID_LOCATION_RANGE = "%s location is invalid. Length must be within feature range.";
    private static final String INVALID_LOCATION_VALUE = "Invalid %s location: start must be > 0 and less than end.";
    private static final String INVALID_LOCATION_SPAN = "%s location span must be \"%s\"";

    @ValidationMethod(rule = "ANTI_CODON_ATTRIBUTE", type = ValidationType.FEATURE)
    public void validateAntiCodon(GFF3Feature feature, int line) throws ValidationException {
        if (feature.hasAttribute(GFF3Attributes.ANTI_CODON)) {
            validateCodonAttribute(
                    feature,
                    line,
                    GFF3Attributes.ANTI_CODON,
                    feature.getAttributeByName(GFF3Attributes.ANTI_CODON).orElse(null));
        }
    }

    @ValidationMethod(rule = "TRANSL_EXCEPT_ATTRIBUTE", type = ValidationType.FEATURE)
    public void validateTranslExcept(GFF3Feature feature, int line) throws ValidationException {
        if (feature.hasAttribute(GFF3Attributes.TRANSL_EXCEPT)) {
            validateCodonAttribute(
                    feature,
                    line,
                    GFF3Attributes.TRANSL_EXCEPT,
                    feature.getAttributeByName(GFF3Attributes.TRANSL_EXCEPT).orElse(null));
        }
    }

    private void validateCodonAttribute(GFF3Feature feature, int line, String attribute, String value)
            throws ValidationException {
        AminoAcid aminoAcid;
        CompoundLocation<Location> location;

        try {
            if (attribute.equalsIgnoreCase(GFF3Attributes.ANTI_CODON)) {
                AnticodonQualifier q = new AnticodonQualifier(value);
                aminoAcid = q.getAminoAcid();
                location = q.getLocations();
            } else if (attribute.equalsIgnoreCase(GFF3Attributes.TRANSL_EXCEPT)) {
                TranslExceptQualifier q = new TranslExceptQualifier(value);
                aminoAcid = q.getAminoAcid();
                location = q.getLocations();
            } else {
                return;
            }

            long start = location.getMinPosition();
            long end = location.getMaxPosition();
            long length = location.getLength();

            // Feature boundaries
            long featureStart = feature.getStart();
            long featureEnd = feature.getEnd();

            // Amino acid
            if (aminoAcid == null) {
                throw new ValidationException(line, INVALID_AMINO_ACID.formatted(attribute));
            }

            // Location range inside feature
            if (start < featureStart || end > featureEnd) {
                throw new ValidationException(line, INVALID_LOCATION_RANGE.formatted(attribute));
            }

            // Logical checks
            if (start > end || start <= 0) {
                throw new ValidationException(line, INVALID_LOCATION_VALUE.formatted(attribute));
            }

            // Length must be 3 unless transl_except TERM
            if (length != 3
                    && !(attribute.equalsIgnoreCase(GFF3Attributes.TRANSL_EXCEPT)
                            && TERM_AMINO_ACID.equals(aminoAcid.getAbbreviation()))) {
                throw new ValidationException(line, INVALID_LOCATION_SPAN.formatted(attribute, 3));
            }

        } catch (uk.ac.ebi.embl.api.validation.ValidationException e) {
            throw new ValidationException(line, INVALID_FORMAT.formatted(attribute, e.getMessage()));
        }
    }

    @ValidationMethod(rule = "AMINO_ACID_MISMATCH", type = ValidationType.FEATURE, severity = RuleSeverity.WARN)
    public void validateAminoAcidMismatch(GFF3Feature feature, int line) throws ValidationException {
        String value = null;
        String attribute = null;

        if (feature.hasAttribute(GFF3Attributes.ANTI_CODON)) {
            value = feature.getAttributeByName(GFF3Attributes.ANTI_CODON).orElse(null);
            attribute = GFF3Attributes.ANTI_CODON;
        } else if (feature.hasAttribute(GFF3Attributes.TRANSL_EXCEPT)) {
            value = feature.getAttributeByName(GFF3Attributes.TRANSL_EXCEPT).orElse(null);
            attribute = GFF3Attributes.TRANSL_EXCEPT;
        }

        if (value == null) return;

        try {
            AminoAcid aminoAcid;
            String aminoAcidString;

            if (attribute.equals(GFF3Attributes.ANTI_CODON)) {
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
                        line, INVALID_AMINO_ACID_VALUE.formatted(aminoAcidString, aminoAcid.getAbbreviation()));
            }
        } catch (uk.ac.ebi.embl.api.validation.ValidationException e) {
            throw new ValidationException(line, INVALID_FORMAT.formatted(attribute, e.getMessage()));
        }
    }
}
