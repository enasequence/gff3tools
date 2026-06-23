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
import java.util.Set;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(
        name = "SEQUENCE_MAPPING",
        description =
                "Validates that every feature accession maps to a sequence when a sequence provider is registered")
public class SequenceMappingValidation implements Validation {

    private static final String RULE_SEQUENCE_MAPPING = "SEQUENCE_MAPPING";
    private static final String NO_MAPPING_MESSAGE =
            "No sequence could be resolved for accession \"%s\". A sequence provider is registered but does not "
                    + "supply a sequence for this feature.";

    @InjectContext
    private ValidationContext context;

    private final Set<String> validatedAccessions = new HashSet<>();

    @ValidationMethod(
            rule = RULE_SEQUENCE_MAPPING,
            description = "Every feature accession must map to a sequence when a sequence provider is registered",
            type = ValidationType.FEATURE,
            priority = ValidationPriority.HIGH)
    public void validateSequenceMapping(GFF3Feature feature, int line) throws ValidationException {
        SequenceLookup lookup = registeredSequenceLookup();
        if (lookup == null) {
            return;
        }

        String accession = feature.accession();
        if (!validatedAccessions.add(accession)) {
            return;
        }

        resolveOrExplode(lookup, accession, line);
    }

    private SequenceLookup registeredSequenceLookup() {
        return context.contains(SequenceLookup.class) ? context.get(SequenceLookup.class) : null;
    }

    private void resolveOrExplode(SequenceLookup lookup, String accession, int line) throws ValidationException {
        try {
            lookup.getSequenceLength(accession, SequenceRangeOption.WHOLE_SEQUENCE);
        } catch (Exception e) {
            validatedAccessions.remove(accession);
            throw new ValidationException(RULE_SEQUENCE_MAPPING, line, NO_MAPPING_MESSAGE.formatted(accession));
        }
    }
}
