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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ExitMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(
        name = "FASTA_HEADER_MAPPING",
        description =
                "Validates that every annotation accession maps to a FASTA header when a FASTA header provider is registered")
public class FastaHeaderMappingValidation implements Validation {

    private static final String RULE_FASTA_HEADER_MAPPING = "FASTA_HEADER_MAPPING";
    private static final String NO_HEADER_MESSAGE =
            "No FASTA header could be resolved for accession \"%s\". A FASTA header provider is registered but does "
                    + "not supply a header for this annotation.";

    @InjectContext
    private ValidationContext context;

    private final Set<String> validatedAccessions = new HashSet<>();

    @ValidationMethod(
            rule = RULE_FASTA_HEADER_MAPPING,
            description =
                    "Every annotation accession must map to a FASTA header when a FASTA header provider is registered",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void validateFastaHeaderMapping(GFF3Annotation annotation, int line) throws ValidationException {
        FastaHeaderProvider headerProvider = registeredHeaderProvider();
        if (headerProvider == null) {
            return;
        }

        String accession = annotation.getAccession();
        if (!validatedAccessions.add(accession)) {
            return;
        }

        Optional<FastaHeader> header = headerProvider.getHeader(accession);
        if (header.isEmpty()) {
            validatedAccessions.remove(accession);
            throw new ValidationException(RULE_FASTA_HEADER_MAPPING, line, NO_HEADER_MESSAGE.formatted(accession));
        }
    }

    private FastaHeaderProvider registeredHeaderProvider() {
        return context.contains(FastaHeaderProvider.class) ? context.get(FastaHeaderProvider.class) : null;
    }

    @ExitMethod
    public void clear() {
        validatedAccessions.clear();
    }
}
