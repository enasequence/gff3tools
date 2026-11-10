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

import java.util.EnumSet;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

/**
 * The source-dependent rule of the INSDC Annotation Minimum Specification v1.0, section c.ii.
 *
 * <p>Use of {@code artificial_location} (either {@code low-quality sequence region} or {@code
 * heterogeneous population sequenced}) must be limited to genome-scale annotation, which is {@link
 * AnalysisType#SEQUENCE_ASSEMBLY} here.
 */
@Gff3Validation(name = "ARTIFICIAL_LOCATION")
public class ArtificialLocationValidation implements Validation {

    public static final String ARTIFICIAL_LOCATION_ANALYSIS_TYPE_RULE = "ARTIFICIAL_LOCATION_ANALYSIS_TYPE";

    private static final String ARTIFICIAL_LOCATION_MESSAGE =
            "Feature \"%s\" carries artificial_location, which is limited to genome-scale annotation "
                    + "(so analysis type sequence assembly), but the analysis type was submitted as %s.";

    private static final Set<AnalysisType> ARTIFICIAL_LOCATION_NOT_ALLOWED =
            EnumSet.of(AnalysisType.TRANSCRIPTOME_ASSEMBLY, AnalysisType.SEQUENCE_FLATFILE);

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = ARTIFICIAL_LOCATION_ANALYSIS_TYPE_RULE,
            description = "Attribute artificial_location is limited to genome-scale annotation",
            type = ValidationType.FEATURE)
    public void validateArtificialLocationAnalysisType(GFF3Feature feature, int line) throws ValidationException {
        if (!feature.hasAttribute(GFF3Attributes.ARTIFICIAL_LOCATION)) {
            return;
        }
        AnalysisType analysisType = ValidationUtils.getAnalysisType(context);
        if (ARTIFICIAL_LOCATION_NOT_ALLOWED.contains(analysisType)) {
            throw new ValidationException(
                    ARTIFICIAL_LOCATION_ANALYSIS_TYPE_RULE,
                    line,
                    ARTIFICIAL_LOCATION_MESSAGE.formatted(feature.getName(), analysisType));
        }
    }
}
