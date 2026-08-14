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

import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(name = "EDGE_NS", description = "Validates that a sequence does not start or end with N (gap) bases")
public class EdgeNsValidation implements Validation {

    private static final String RULE_EDGE_NS = "EDGE_NS";

    private static final String MESSAGE_EDGE_NS =
            "Sequence \"%s\" starts and/or ends with \"n\" characters (%d leading, %d trailing). Leading and trailing"
                    + " Ns must be removed from the sequence.";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE_EDGE_NS,
            description = "A sequence must not begin or end with N bases",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateEdgeNs(GFF3Annotation annotation, int line) throws ValidationException {
        if (!context.contains(SequenceLookup.class)) {
            return;
        }
        SequenceLookup lookup = context.get(SequenceLookup.class);
        if (lookup == null) {
            return;
        }

        String seqId = annotation.getAccession();
        SequenceStats stats = resolveStats(lookup, seqId);
        if (stats == null) {
            throw new IllegalStateException("No sequence stats available for sequence " + seqId);
        }

        long leadingNs = stats.leadingNsCount();
        long trailingNs = stats.trailingNsCount();
        if (leadingNs < 0 || trailingNs < 0) {
            throw new IllegalStateException("Negative edge-N counts for sequence " + seqId + ": leading=" + leadingNs
                    + ", trailing=" + trailingNs);
        }

        if (leadingNs > 0 || trailingNs > 0) {
            throw new ValidationException(RULE_EDGE_NS, line, MESSAGE_EDGE_NS.formatted(seqId, leadingNs, trailingNs));
        }
    }

    private SequenceStats resolveStats(SequenceLookup lookup, String seqId) {
        try {
            return lookup.getSequenceStats(seqId);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve sequence stats for sequence " + seqId, e);
        }
    }
}
