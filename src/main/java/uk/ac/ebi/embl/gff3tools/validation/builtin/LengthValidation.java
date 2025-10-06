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

import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationWarning;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.FeatureValidation;

public class LengthValidation implements FeatureValidation {

    public static final String VALIDATION_RULE = "GFF3_LENGTH_VALIDATION";
    public static long INTRON_FETURE_LENGTH = 10;
    public static long EXON_FETURE_LENGTH = 15;

    private static final String INVALID_PROPEPTIDE_LENGTH_MESSAGE =
            "Propeptide feature length must be multiple of 3 for accession \"%s\"";
    private static final String INVALID_INTRON_LENGTH_MESSAGE = "Intron feature length is invalid for accession \"%s\"";
    private static final String INVALID_EXON_LENGTH_MESSAGE = "Exon feature length is invalid for accession \"%s\"";

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException, ValidationWarning {
        String featureName = feature.getName();
        long length = feature.getLength();

        if (GFF3Anthology.PROPETIDE_FEATURE_NAME.equalsIgnoreCase(featureName) && feature.getLength() % 3 != 0) {
            throw new ValidationException(
                    VALIDATION_RULE, line, INVALID_PROPEPTIDE_LENGTH_MESSAGE.formatted(feature.accession()));
        }

        if ((GFF3Anthology.INTRON_EQUIVALENTS.contains(featureName)) && length < INTRON_FETURE_LENGTH) {
            throw new ValidationException(
                    VALIDATION_RULE, line, INVALID_INTRON_LENGTH_MESSAGE.formatted(feature.accession()));
        } else if ((GFF3Anthology.EXON_EQUIVALENTS.contains(featureName)) && length < EXON_FETURE_LENGTH) {
            throw new ValidationWarning(
                    VALIDATION_RULE, line, INVALID_EXON_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }
}
