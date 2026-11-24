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

import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

/**
 * Reports a {@code ##FASTA} section that cannot be read, such as one with a character that isn't
 * an amino acid or a header that appears twice. The other translation rules treat such a section as
 * empty, so without this rule it is only noticed when the output is written.
 *
 * <p>Checked once per GFF3 reader, on the first annotation: the engine has no hook that runs once
 * after the reader is registered. Building the index here costs nothing extra, as the first
 * {@code ##FASTA} lookup would build it anyway.
 */
@Gff3Validation(name = FastaSectionReadableValidation.RULE)
public class FastaSectionReadableValidation {

    public static final String RULE = "FASTA_SECTION_UNREADABLE";

    @InjectContext
    private ValidationContext context;

    /** The reader already checked, so a file is reported once, and a new reader is checked again. */
    private GFF3TranslationReader checkedReader;

    @ValidationMethod(
            rule = RULE,
            description = "Check that the ##FASTA section of a GFF3 file can be read",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void validateAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        if (!context.contains(GFF3TranslationReader.class)) {
            return;
        }
        GFF3TranslationReader reader = context.get(GFF3TranslationReader.class);
        if (reader == checkedReader) {
            return;
        }
        checkedReader = reader;
        try {
            reader.getTranslationOffsets();
        } catch (ReadException e) {
            throw new ValidationException(RULE, line, e.getMessage());
        }
    }
}
