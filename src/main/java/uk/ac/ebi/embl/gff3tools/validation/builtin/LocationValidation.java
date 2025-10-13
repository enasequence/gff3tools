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

import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.*;

@Gff3Validation
public class LocationValidation implements Validation {

    public static final String VALIDATION_RULE = "GFF3_LOCATION_VALIDATION";

    private static final String INVALID_START_END_MESSAGE = "Invalid start/end for accession \"%s\"";
    private static final String INVALID_PROPEPTIDE_CDS_LOCATION_MESSAGE = "Propeptide [%d %d] not inside any CDS";
    private static final String INVALID_PROPEPTIDE_PEPTIDE_LOCATION_MESSAGE =
            "Propeptide [%d %d] overlaps with peptide features";

    @ValidationMethod(type = ValidationType.FEATURE)
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        long start = feature.getStart();
        long end = feature.getEnd();

        if (start <= 0 || end <= 0 || end < start) {
            throw new ValidationException(
                    VALIDATION_RULE, line, INVALID_START_END_MESSAGE.formatted(feature.accession()));
        }
    }

    @ValidationMethod(type = ValidationType.ANNOTATION)
    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {

        // Annotation Level Validation
        List<GFF3Feature> propFeatures = annotation.getFeaturesByName(GFF3Anthology.PROPETIDE_FEATURE_NAME);
        List<GFF3Feature> cdsFeatures = annotation.getFeaturesByName(GFF3Anthology.CDS_FEATURE_NAME);

        List<GFF3Feature> peptideFeatures = new ArrayList<>();
        peptideFeatures.addAll(annotation.getFeaturesByName(GFF3Anthology.SIG_PEPTIDE_FEATURE_NAME));
        peptideFeatures.addAll(annotation.getFeaturesByName(GFF3Anthology.MAP_PEPTIDE_FEATURE_NAME));

        for (GFF3Feature propFeature : propFeatures) {

            long start = propFeature.getStart();
            long end = propFeature.getEnd();

            // Must be inside at least one CDS
            boolean insideCds = cdsFeatures.stream().anyMatch(cds -> start >= cds.getStart() && end <= cds.getEnd());
            if (!insideCds) {
                throw new ValidationException(
                        VALIDATION_RULE, line, INVALID_PROPEPTIDE_CDS_LOCATION_MESSAGE.formatted(start, end));
            }

            // Must not overlap any peptide features
            boolean overlaps = peptideFeatures.stream().anyMatch(p -> (start < p.getEnd() && end > p.getStart()));
            if (overlaps) {
                throw new ValidationException(
                        VALIDATION_RULE, line, INVALID_PROPEPTIDE_PEPTIDE_LOCATION_MESSAGE.formatted(start, end));
            }
        }
    }
}
