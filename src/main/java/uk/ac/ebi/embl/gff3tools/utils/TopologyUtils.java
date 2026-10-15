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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
     * A GFF3 segment in virtual coordinates as ranges within {@code 1..L}, in reading order: crossing
     * the origin splits into {@code start..L} and {@code 1..(end - L)}, wholly past it shifts by
     * {@code -L}. Unchanged when not circular ({@code null}) or when it would lap its own start.
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
     * The reverse of {@link #unwrapOriginSpan}: plus-strand pieces from the first step down onwards
     * move into virtual space ({@code + L}). Unchanged on a second step down (the feature laps itself).
     */
    public static List<Span> toVirtualSpace(List<Span> plusStrandPath, long circularSequenceLength) {
        List<Span> virtual = new ArrayList<>();
        boolean crossedOrigin = false;
        for (int i = 0; i < plusStrandPath.size(); i++) {
            Span span = plusStrandPath.get(i);
            if (i > 0 && span.start() < plusStrandPath.get(i - 1).start()) {
                if (crossedOrigin) {
                    return plusStrandPath;
                }
                crossedOrigin = true;
            }
            virtual.add(
                    crossedOrigin
                            ? new Span(span.start() + circularSequenceLength, span.end() + circularSequenceLength)
                            : span);
        }
        return virtual;
    }

    private static final Pattern POSITION = Pattern.compile(
            "^(?<complement>complement\\(\\s*)?(?<start>\\d+)(?:\\s*\\.\\.\\s*(?<end>\\d+))?(?<close>\\s*\\))?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * A GFF3 position ({@code start..end} or {@code start}, optionally in {@code complement(...)}) in
     * flat file numbering ({@code 1..L}): virtual coordinates are unwrapped as by {@link
     * #unwrapOriginSpan}, and a position crossing the origin becomes a {@code join}. Anything else is
     * returned unchanged.
     */
    public static String toFlatFilePosition(String position, long circularSequenceLength) {
        Matcher matcher = POSITION.matcher(position.trim());
        if (!matcher.matches() || (matcher.group("complement") == null) != (matcher.group("close") == null)) {
            return position;
        }
        long start = Long.parseLong(matcher.group("start"));
        long end = matcher.group("end") != null ? Long.parseLong(matcher.group("end")) : start;
        List<Span> spans = unwrapOriginSpan(start, end, circularSequenceLength);
        if (spans.equals(List.of(new Span(start, end)))) {
            return position;
        }
        String body = spans.size() == 1
                ? range(spans.get(0))
                : "join(" + spans.stream().map(TopologyUtils::range).collect(Collectors.joining(",")) + ")";
        return matcher.group("complement") != null ? "complement(" + body + ")" : body;
    }

    private static final Pattern COMPLEMENT =
            Pattern.compile("^complement\\(\\s*(.+?)\\s*\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern JOIN = Pattern.compile("^join\\(\\s*(.+?)\\s*\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern RANGE = Pattern.compile("^(\\d+)(?:\\s*\\.\\.\\s*(\\d+))?$");

    /**
     * The reverse of {@link #toFlatFilePosition}: a flat file position ({@code a..b} or {@code a}, or a
     * {@code join} of those, optionally in {@code complement(...)}) moved into virtual space with the
     * piece of its feature that contains it. {@code pieces} are the feature's pieces in {@code 1..L},
     * {@code virtualPieces} where each lands. Ranges meeting at the origin become one. Unchanged when
     * a range lies outside every piece or the position has another form.
     */
    public static String toVirtualPosition(String position, List<Span> pieces, List<Span> virtualPieces) {
        String text = position.trim();
        Matcher complement = COMPLEMENT.matcher(text);
        boolean complemented = complement.matches();
        if (complemented) {
            text = complement.group(1);
        }
        Matcher join = JOIN.matcher(text);
        String[] parts = join.matches() ? join.group(1).split(",") : new String[] {text};

        List<Span> shifted = new ArrayList<>();
        boolean changed = false;
        for (String part : parts) {
            Matcher range = RANGE.matcher(part.trim());
            if (!range.matches()) {
                return position;
            }
            long start = Long.parseLong(range.group(1));
            long end = range.group(2) != null ? Long.parseLong(range.group(2)) : start;
            Long shift = shiftOfPieceContaining(start, end, pieces, virtualPieces);
            if (shift == null) {
                return position;
            }
            changed |= shift != 0;
            Span span = new Span(start + shift, end + shift);
            Span previous = shifted.isEmpty() ? null : shifted.get(shifted.size() - 1);
            if (previous != null && previous.end() + 1 == span.start()) {
                shifted.set(shifted.size() - 1, new Span(previous.start(), span.end()));
                changed = true;
            } else {
                shifted.add(span);
            }
        }
        if (!changed) {
            return position;
        }
        String body = shifted.size() == 1
                ? range(shifted.get(0))
                : "join(" + shifted.stream().map(TopologyUtils::range).collect(Collectors.joining(",")) + ")";
        return complemented ? "complement(" + body + ")" : body;
    }

    private static Long shiftOfPieceContaining(long start, long end, List<Span> pieces, List<Span> virtualPieces) {
        for (int i = 0; i < pieces.size(); i++) {
            Span piece = pieces.get(i);
            if (piece.start() <= start && end <= piece.end()) {
                return virtualPieces.get(i).start() - piece.start();
            }
        }
        return null;
    }

    private static final Pattern POSITION_PART =
            Pattern.compile("(pos\\s*:\\s*)(.+?)(\\s*,\\s*aa\\s*:)", Pattern.CASE_INSENSITIVE);

    /**
     * An {@code anticodon} or {@code transl_except} value ({@code (pos:<position>,aa:...)}) with its
     * position converted; unchanged when it has no position.
     */
    public static String mapPosition(String attributeValue, UnaryOperator<String> convert) {
        Matcher matcher = POSITION_PART.matcher(attributeValue);
        if (!matcher.find()) {
            return attributeValue;
        }
        return attributeValue.substring(0, matcher.start(2))
                + convert.apply(matcher.group(2))
                + attributeValue.substring(matcher.end(2));
    }

    private static String range(Span span) {
        return span.start() == span.end() ? String.valueOf(span.start()) : span.start() + ".." + span.end();
    }

    /** Whether two consecutive virtual spans run straight through the origin. */
    public static boolean continuesAcrossOrigin(Span previous, Span next, long circularSequenceLength) {
        return previous.end() == circularSequenceLength && next.start() == circularSequenceLength + 1;
    }

    /**
     * The first recognised topology from the flat file ID line, the FASTA header, then the master
     * entry (the sequence record is authoritative). Shared by validation and conversion.
     */
    public static Optional<ControlledVocabularyUtils.Topology> resolveTopology(
            String accession, ValidationContext context) {
        return flatfileTopology(accession, context)
                .or(() -> headerTopology(accession, context))
                .or(() -> masterTopology(accession, context));
    }

    /** Circular only when declared so ({@link #resolveTopology}). */
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
