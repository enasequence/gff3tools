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
 * an amino acid or a header that appears twice. {@code GFF3FileReader} and the other translation
 * rules skip such a section, and a validated run writes translations from {@code TranslationState}
 * without reading it again, so this rule is the only place it is reported.
 *
 * <p>Checked once, on the first annotation: the engine has no hook that runs once
 * after the reader is registered. The index is usually built already, by the reader's first
 * {@code ##FASTA} lookup; a failed build is remembered, so it is not retried here.
 */
@Gff3Validation(name = FastaSectionReadableValidation.RULE)
public class FastaSectionReadableValidation {

    public static final String RULE = "FASTA_SECTION_UNREADABLE";

    @InjectContext
    private ValidationContext context;

    /** Set once the section is checked, so it is reported once. One engine reads one file. */
    private boolean checked;

    @ValidationMethod(
            rule = RULE,
            description = "Check that the ##FASTA section of a GFF3 file is formatted correctly",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void validateFastaSectionReadable(GFF3Annotation annotation, int line) throws ValidationException {
        if (checked || !context.contains(GFF3TranslationReader.class)) {
            return;
        }
        checked = true;
        try {
            context.get(GFF3TranslationReader.class).getTranslationOffsets();
        } catch (ReadException e) {
            throw new ValidationException(RULE, line, e.getMessage());
        }
    }
}
