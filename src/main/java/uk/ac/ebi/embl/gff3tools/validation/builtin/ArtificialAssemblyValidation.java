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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

@Gff3Validation(name = "ARTIFICIAL_ASSEMBLY")
public class ArtificialAssemblyValidation implements Validation {

    public static final String ARTIFICIAL_ASSEMBLY_ANALYSIS_TYPE_RULE = "ARTIFICIAL_ASSEMBLY_ANALYSIS_TYPE";

    private static final String ARTIFICIAL_ASSEMBLY_MESSAGE =
            "Feature \"%s\" must not carry the artificial_assembly attribute for analysis type %s; it is only permitted for SEQUENCE_ASSEMBLY.";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = ARTIFICIAL_ASSEMBLY_ANALYSIS_TYPE_RULE,
            description = "Check that artificial_assembly is only used in a sequence assembly",
            type = ValidationType.FEATURE)
    public void validateArtificialAssemblyAnalysisType(GFF3Feature feature, int line) throws ValidationException {
        AnalysisType analysisType = getAnalysisType();
        if (analysisType == AnalysisType.UNKNOWN
                || analysisType == AnalysisType.SEQUENCE_ASSEMBLY
                || !feature.hasAttribute(GFF3Attributes.ARTIFICIAL_ASSEMBLY)) {
            return;
        }
        throw new ValidationException(
                ARTIFICIAL_ASSEMBLY_ANALYSIS_TYPE_RULE,
                line,
                ARTIFICIAL_ASSEMBLY_MESSAGE.formatted(feature.getName(), analysisType));
    }

    /**
     * {@link AnalysisContext} defaults to {@link AnalysisType#UNKNOWN}. An unknown analysis type is
     * not ruled out as an assembly, so the rule stays inert unless a caller registers a real one.
     */
    private AnalysisType getAnalysisType() {
        return context.contains(AnalysisContext.class)
                ? context.get(AnalysisContext.class).getAnalysisType()
                : AnalysisType.UNKNOWN;
    }
}
