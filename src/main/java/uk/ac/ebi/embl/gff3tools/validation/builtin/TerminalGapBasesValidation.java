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
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(
        name = "NO_TERMINAL_GAPS",
        description = "Validates that a linear sequence does not start or end with N (gap) bases")
public class TerminalGapBasesValidation implements Validation {

    public static final String NO_TERMINAL_GAPS_RULE = "NO_TERMINAL_GAPS";
    private static final String MESSAGE_TERMINAL_GAPS =
            "Linear sequence \"%s\" starts and/or ends with \"n\" characters (%d leading, %d trailing). Leading and trailing"
                    + " Ns must be removed from the sequence for a linear sequence.";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = NO_TERMINAL_GAPS_RULE,
            description = "A sequence must not begin or end with N bases",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.LOW)
    public void validateTerminalGapBases(GFF3Annotation annotation, int line) throws ValidationException {
        if (!context.contains(SequenceLookup.class)) {
            return;
        }
        SequenceLookup lookup = context.get(SequenceLookup.class);
        if (lookup == null) {
            return;
        }

        String seqId = annotation.getAccession();
        // A circular sequence has no first or last base, so terminal gaps are not meaningful for it.
        if (isCircularSequence(seqId)) {
            return;
        }

        SequenceStats stats = resolveStats(lookup, seqId);
        if (stats == null) {
            throw new IllegalStateException("No sequence stats available for sequence " + seqId);
        }

        long leadingNs = stats.leadingNsCount();
        long trailingNs = stats.trailingNsCount();
        if (leadingNs < 0 || trailingNs < 0) {
            throw new IllegalStateException("Negative terminal gap-base counts for sequence " + seqId + ": leading="
                    + leadingNs + ", trailing=" + trailingNs);
        }

        if (leadingNs > 0 || trailingNs > 0) {
            throw new ValidationException(
                    NO_TERMINAL_GAPS_RULE, line, MESSAGE_TERMINAL_GAPS.formatted(seqId, leadingNs, trailingNs));
        }
    }

    /**
     * Topology is only known when a FASTA header source is registered for the run. An absent or
     * unrecognised topology is treated as non-circular: circular is always explicitly declared, and
     * a missing mandatory topology is reported by {@link FastaHeaderFormatValidation}.
     */
    private boolean isCircularSequence(String accession) {
        if (!context.contains(FastaHeaderProvider.class)) {
            return false;
        }
        return context.get(FastaHeaderProvider.class)
                .getHeader(accession)
                .map(FastaHeader::getTopology)
                // Canonicalise rather than matching the raw value: FastaHeaderNormalisationFix is
                // annotation-scoped and runs only after this annotation's features are validated.
                .flatMap(topology ->
                        ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, topology))
                .flatMap(ControlledVocabularyUtils.Topology::fromValue)
                .map(ControlledVocabularyUtils.Topology.CIRCULAR::equals)
                .orElse(false);
    }

    private SequenceStats resolveStats(SequenceLookup lookup, String seqId) {
        try {
            return lookup.getSequenceStats(seqId);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve sequence stats for sequence " + seqId, e);
        }
    }
}
