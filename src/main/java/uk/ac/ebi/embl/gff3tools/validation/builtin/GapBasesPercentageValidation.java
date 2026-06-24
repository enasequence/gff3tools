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

import java.util.Optional;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;

@Gff3Validation(
        name = "GAP_BASES_PERCENTAGE",
        description = "Validates that a sequence does not consist of an excessive proportion of N (gap) bases")
public class GapBasesPercentageValidation implements Validation {

    private static final String RULE_GAP_BASES_PERCENTAGE = "GAP_BASES_PERCENTAGE";

    private static final double MAX_GAP_FRACTION = 0.5;

    private static final String MESSAGE_TOO_MANY_NS =
            "Sequence \"%s\" contains %.1f%% N bases (excluding leading/trailing Ns), which exceeds the"
                    + " permitted maximum of 50%% for non-chromosome sequences.";
    private static final String MESSAGE_ALL_NS =
            "Sequence \"%s\" consists entirely of N bases (excluding leading/trailing Ns).";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE_GAP_BASES_PERCENTAGE,
            description = "A sequence (excluding edge Ns) must not be more than 50% Ns, unless it is a chromosome or no"
                    + " FASTA header information is available, in which case it must only not be 100% Ns",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.NORMAL)
    public void validateGapBasesPercentage(GFF3Annotation annotation, int line) throws ValidationException {
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

        long lengthWithoutEdges = stats.totalBasesWithoutNBases();
        long nCount = countInteriorNs(stats);
        if (lengthWithoutEdges < 0 || nCount < 0) {
            throw new IllegalStateException("Negative gap-base counts for sequence " + seqId + ": length="
                    + lengthWithoutEdges + ", Ns=" + nCount);
        }
        if (lengthWithoutEdges == 0 || nCount == 0) {
            return;
        }

        if (isChromosomeSequence(seqId)) {
            // For chromosomes, only check N count is less than the sequence count.
            if (nCount >= lengthWithoutEdges) {
                throw new ValidationException(RULE_GAP_BASES_PERCENTAGE, line, MESSAGE_ALL_NS.formatted(seqId));
            }
        } else {
            // N percentage check for non-chromosome sequences
            if (nCount > lengthWithoutEdges * MAX_GAP_FRACTION) {
                double percentage = 100.0 * nCount / lengthWithoutEdges;
                throw new ValidationException(
                        RULE_GAP_BASES_PERCENTAGE, line, MESSAGE_TOO_MANY_NS.formatted(seqId, percentage));
            }
        }
    }

    private SequenceStats resolveStats(SequenceLookup lookup, String seqId) {
        try {
            return lookup.getSequenceStats(seqId);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to resolve sequence stats for sequence " + seqId, e);
        }
    }

    private long countInteriorNs(SequenceStats stats) {
        return stats.baseCount().getOrDefault('N', 0L) - stats.leadingNsCount() - stats.trailingNsCount();
    }

    private boolean isChromosomeSequence(String seqId) {
        if (!context.contains(FastaHeaderProvider.class)) {
            return true;
        }
        FastaHeaderProvider headerProvider = context.get(FastaHeaderProvider.class);
        if (headerProvider == null) {
            return true;
        }
        Optional<FastaHeader> header = headerProvider.getHeader(seqId);
        if (header.isEmpty()) {
            return false;
        }
        FastaHeader fastaHeader = header.get();
        return fastaHeader.getChromosomeType() != null
                || fastaHeader.getChromosomeLocation() != null
                || fastaHeader.getChromosomeName() != null;
    }
}
