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
package uk.ac.ebi.embl.converter.gff3toff.validation;

import java.util.HashSet;
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.converter.exception.ValidationException;
import uk.ac.ebi.embl.converter.gff3.GFF3Annotation;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.validation.AnnotationValidation;
import uk.ac.ebi.embl.converter.validation.FeatureValidation;

public class DuplicateSeqIdValidation implements FeatureValidation, AnnotationValidation {

    public static final String VALIDATION_RULE = "GFF3_DUPLICATE_SEQID";

    private HashSet<String> processedAnnotations = new HashSet<>();
    private String currentAccession = null;

    @Override
    public String getValidationRule() {
        return VALIDATION_RULE;
    }

    @Override
    public void validateFeature(GFF3Feature feature, int line) throws ValidationException {
        String accession = feature.accession();

        if (!accession.equals(currentAccession)) {
            if (processedAnnotations.contains(accession)) {
                throw new DuplicateSeqIdException(line, feature.accession());
            }
            processedAnnotations.add(currentAccession);
            currentAccession = accession;
        }
    }

    @Override
    public void validateAnnotation(GFF3Annotation feature, int line) throws ValidationException {
        currentAccession = null;
    }
}
