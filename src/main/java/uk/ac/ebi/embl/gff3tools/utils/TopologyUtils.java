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
package uk.ac.ebi.embl.gff3tools.utils;

import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ControlledVocabularyUtils;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.FlatfileSequenceTopologyProvider;

/** Where a sequence's topology comes from, shared by validation and conversion. */
public class TopologyUtils {

    /** A stretch of sequence, {@code start..end}, 1-based and inclusive. */
    public record Span(long start, long end) {}

    /**
     * The stretches of the real sequence a GFF3 segment covers, in reading order. On a circular
     * sequence INSDC GFF3 numbers everything after the origin in virtual space, continuing past the
     * sequence length L instead of restarting at 1, so a segment is one of:
     *
     * <ul>
     *   <li>within the sequence ({@code end <= L}): unchanged;
     *   <li>crossing the origin ({@code start <= L < end}): {@code start..L} then {@code 1..(end - L)};
     *   <li>wholly past the origin ({@code start > L}): shifted back to {@code (start - L)..(end - L)}.
     * </ul>
     *
     * <p>A segment on a linear or undeclared sequence ({@code circularSequenceLength} is null) comes
     * back unchanged. So does one that would lap its own start (crossing with {@code end - L >=
     * start}, or wholly past with {@code end > 2L}): that is not a valid origin span and is left for
     * validation to report.
     */
    public static List<Span> unwrapOriginSpan(long start, long end, Long circularSequenceLength) {
        Long length = circularSequenceLength;
        if (length == null || end <= length) {
            return List.of(new Span(start, end));
        }
        if (start > length) {
            if (end <= 2 * length) {
                return List.of(new Span(start - length, end - length));
            }
        } else if (end - length < start) {
            return List.of(new Span(start, length), new Span(1, end - length));
        }
        return List.of(new Span(start, end));
    }

    /**
     * The topology declared for a sequence, from the first source that declares one. The sequence
     * record is authoritative (INSDC GFF3), so the record itself comes first: the flat file entry's
     * ID line, then the sequence's FASTA header. The master entry, which declares one topology for
     * the whole submission, is the last fallback. Validation and the GFF3 to flat file mapper both
     * resolve topology here, so the topology a rule checks against is the one the ID line is written
     * with. An absent or unrecognised value in one source falls through to the next; a missing
     * mandatory header topology is reported by {@code FastaHeaderFormatValidation}.
     *
     * @param accession the key the caller looks the sequence up by
     * @return the declared topology, or empty when no source declares a recognised one
     */
    public static Optional<ControlledVocabularyUtils.Topology> resolveTopology(
            String accession, ValidationContext context) {
        return flatfileTopology(accession, context)
                .or(() -> headerTopology(accession, context))
                .or(() -> masterTopology(accession, context));
    }

    /** Circular only when declared so; see {@link #resolveTopology} for where topology comes from. */
    public static boolean isCircularSequence(String accession, ValidationContext context) {
        return resolveTopology(accession, context)
                .map(ControlledVocabularyUtils.Topology.CIRCULAR::equals)
                .orElse(false);
    }

    private static Optional<ControlledVocabularyUtils.Topology> masterTopology(
            String accession, ValidationContext context) {
        if (!context.contains(MasterMetadataProvider.class)) {
            return Optional.empty();
        }
        return context.get(MasterMetadataProvider.class)
                .getMetadata(accession)
                .map(MasterMetadata::getTopology)
                .flatMap(TopologyUtils::toTopology);
    }

    private static Optional<ControlledVocabularyUtils.Topology> headerTopology(
            String accession, ValidationContext context) {
        if (!context.contains(FastaHeaderProvider.class)) {
            return Optional.empty();
        }
        return context.get(FastaHeaderProvider.class)
                .getHeader(accession)
                .map(FastaHeader::getTopology)
                // Canonicalise rather than matching the raw value: FastaHeaderNormalisationFix is
                // annotation-scoped and runs only after this annotation's features are validated.
                .flatMap(TopologyUtils::toTopology);
    }

    private static Optional<ControlledVocabularyUtils.Topology> flatfileTopology(
            String accession, ValidationContext context) {
        if (!context.contains(FlatfileSequenceTopologyProvider.class)) {
            return Optional.empty();
        }
        return context.get(FlatfileSequenceTopologyProvider.class).getTopology(accession);
    }

    private static Optional<ControlledVocabularyUtils.Topology> toTopology(String value) {
        return ControlledVocabularyUtils.canonicalise(ControlledVocabularyUtils.Topology.class, value)
                .flatMap(ControlledVocabularyUtils.Topology::fromValue);
    }
}
