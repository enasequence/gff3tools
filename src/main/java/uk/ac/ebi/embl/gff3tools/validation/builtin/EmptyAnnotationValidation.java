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

import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

/**
 * Rejects annotations without features.
 *
 * <p>Off by default: a sequence with no features is a valid entry when the sequences come from a
 * submitted FASTA file. Turn it on ({@code rule.EMPTY_ANNOTATION=ERROR}) when the GFF3 is the only
 * thing submitted.
 */
@Gff3Validation(name = "EMPTY_ANNOTATION", description = "Rejects annotations without features")
public class EmptyAnnotationValidation implements Validation {

    public static final String VALIDATION_RULE = "EMPTY_ANNOTATION";

    private static final String EMPTY_ANNOTATION_MESSAGE = "Annotation for accession \"%s\" has no features.";

    @ValidationMethod(
            rule = VALIDATION_RULE,
            description = "Annotations must have at least one feature",
            type = ANNOTATION,
            severity = RuleSeverity.OFF,
            priority = ValidationPriority.HIGH)
    public void validate(GFF3Annotation annotation, int line) throws ValidationException {
        if (annotation.hasFeatures()) {
            return;
        }
        throw new ValidationException(
                VALIDATION_RULE, line, EMPTY_ANNOTATION_MESSAGE.formatted(annotation.getAccession()));
    }
}
