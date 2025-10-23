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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation
public class LengthValidation extends Validation {

    private static final long INTRON_FEATURE_MIN_LENGTH = 10;
    private static final long EXON_FEATURE_MIN_LENGTH = 15;

    private static final String INVALID_PROPEPTIDE_LENGTH_MESSAGE =
            "Propeptide feature length must be a multiple of 3 for accession \"%s\"";
    private static final String INVALID_INTRON_LENGTH_MESSAGE = "Intron feature length is invalid for accession \"%s\"";
    private static final String INVALID_EXON_LENGTH_MESSAGE = "Exon feature length is invalid for accession \"%s\"";

    private final OntologyClient ontologyClient = ConversionUtils.getOntologyClient();

    @ValidationMethod(rule = "GFF3_PROPEPTIDE_LENGTH_VALIDATION", type = ValidationType.FEATURE)
    public void validatePropeptideLength(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();

        if (OntologyTerm.PROPEPTIDE.name().equalsIgnoreCase(featureName) && feature.getLength() % 3 != 0) {
            throw new ValidationException(line, INVALID_PROPEPTIDE_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }

    @ValidationMethod(rule = "GFF3_INTRON_LENGTH_VALIDATION", type = ValidationType.FEATURE)
    public void validateIntronLength(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();
        long length = feature.getLength();

        String soId = ontologyClient.findTermByNameOrSynonym(featureName).orElse(null);

        if (ontologyClient.isChildOf(soId, OntologyTerm.INTRON.ID) && length < INTRON_FEATURE_MIN_LENGTH) {
            throw new ValidationException(line, INVALID_INTRON_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }

    @ValidationMethod(rule = "GFF3_EXON_LENGTH_VALIDATION", type = ValidationType.FEATURE, severity = RuleSeverity.WARN)
    public void validateExonLength(GFF3Feature feature, int line) throws ValidationException {
        String featureName = feature.getName();
        long length = feature.getLength();

        String soId = ontologyClient.findTermByNameOrSynonym(featureName).orElse(null);

        if (ontologyClient.isChildOf(soId, OntologyTerm.EXON.ID) && length < EXON_FEATURE_MIN_LENGTH) {
            throw new ValidationException(line, INVALID_EXON_LENGTH_MESSAGE.formatted(feature.accession()));
        }
    }
}
