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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.ANTI_CODON;
import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.translation.Translator;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

/**
 * Fills in the {@code seq:} part of an {@code anticodon} value and fixes the casing of its
 * {@code aa:} part, so {@code (pos:complement(4229..4231),aa:LyS)} becomes
 * {@code (pos:complement(4229..4231),aa:Lys,seq:ttt)}.
 *
 * <p>{@code seq:} is just the three bases the position points at, so it can be read straight off the
 * sequence. Supplying a sequence is optional though, so when there is none that half is skipped and
 * only the casing is fixed.
 *
 * <p>The two jobs are separate {@code @FixMethod}s so each can be switched off on its own. Neither
 * depends on the other having run: each replaces only its own part of the value and copies the rest
 * through unchanged, so running them in either order gives the same result.
 *
 * <p>{@link TranslExceptComplementFix} does the same kind of work on {@code transl_except}. The two
 * never touch the same attribute, so their order does not matter either — as long as both leave every
 * row of a group with identical text, for the reasons on {@link #rewriteGroup}.
 */
@Slf4j
@Gff3Fix(
        name = "ANTICODON",
        description = "Canonicalise the anticodon amino acid and derive its seq: from the sequence")
public class AnticodonAttributeFix implements Fix {

    @InjectContext
    private ValidationContext context;

    // Deliberately matches only a plain "123" or "123..456" for the position, bare or wrapped in a
    // single complement(...); anything more exotic (join(...), <100, OTHER_ACC:1..3) is left untouched
    // for validation to judge. The named groups let the value be rebuilt piece by piece, so the parts
    // this fix does not change keep their original spacing and casing byte for byte.
    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "^(?<head>\\s*\\(\\s*pos\\s*:\\s*)"
                    + "(?<pos>[^,]+?)"
                    + "(?<aaHead>\\s*,\\s*aa\\s*:\\s*)(?<aa>[^\\s,()]+?)"
                    + "(?:(?<seqHead>\\s*,\\s*seq\\s*:\\s*)(?<seq>[^\\s,()]+?))?"
                    + "(?<tail>\\s*\\)\\s*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMPLEMENT_PATTERN =
            Pattern.compile("^complement\\(\\s*(.+?)\\s*\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)(?:\\s*\\.\\.\\s*(\\d+))?$");

    private static final int ANTICODON_LENGTH = 3;

    // Preferred spelling for each abbreviation, keyed on the uppercased token. Deliberately holds
    // exactly the set AminoAcidExcept accepts, and keep the two in step: this fix only corrects
    // casing, so it must never make a value newly valid or newly invalid. A token that is not in
    // here is left alone.
    private static final Map<String, String> CANONICAL_AMINO_ACIDS = Map.ofEntries(
            Map.entry("ALA", "Ala"),
            Map.entry("ARG", "Arg"),
            Map.entry("ASN", "Asn"),
            Map.entry("ASP", "Asp"),
            Map.entry("CYS", "Cys"),
            Map.entry("GLN", "Gln"),
            Map.entry("GLU", "Glu"),
            Map.entry("GLY", "Gly"),
            Map.entry("HIS", "His"),
            Map.entry("ILE", "Ile"),
            Map.entry("LEU", "Leu"),
            Map.entry("LYS", "Lys"),
            Map.entry("MET", "Met"),
            Map.entry("PHE", "Phe"),
            Map.entry("PRO", "Pro"),
            Map.entry("SER", "Ser"),
            Map.entry("THR", "Thr"),
            Map.entry("TRP", "Trp"),
            Map.entry("TYR", "Tyr"),
            Map.entry("VAL", "Val"),
            Map.entry("SEC", "Sec"),
            Map.entry("PYL", "Pyl"),
            Map.entry("TERM", "TERM"),
            Map.entry("TER", "TER"),
            Map.entry("OTHER", "OTHER"));

    /** A parsed position. {@code complement} means the wrapper was there, not that the row is minus-strand. */
    private record Position(boolean complement, long start, long end) {}

    /** Returns the rewritten value, or {@code null} to leave it untouched. */
    private interface ValueRewrite {
        String apply(String value, List<GFF3Feature> fragments, int line);
    }

    @FixMethod(
            rule = "ANTICODON_AMINO_ACID",
            description = "Canonicalise the casing of the anticodon aa: abbreviation",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void fixAminoAcid(GFF3Annotation annotation, int line) {
        rewriteGroups(annotation, line, this::canonicaliseAminoAcid);
    }

    @FixMethod(
            rule = "ANTICODON_SEQUENCE",
            description = "Derive the anticodon seq: from the bases at pos:, adding or correcting it",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void fixSequence(GFF3Annotation annotation, int line) {

        // The lookup type is registered even when no sequence was supplied, so contains() alone is
        // not enough — check for a real value too.
        if (context == null || !context.contains(SequenceLookup.class)) {
            return;
        }
        SequenceLookup sequenceLookup = context.get(SequenceLookup.class);
        if (sequenceLookup == null) {
            return;
        }

        rewriteGroups(annotation, line, (value, fragments, ln) -> deriveSequence(value, fragments, ln, sequenceLookup));
    }

    private void rewriteGroups(GFF3Annotation annotation, int line, ValueRewrite rewrite) {

        Map<String, List<GFF3Feature>> featuresWithAttribute = annotation.getFeatures().stream()
                .filter(feature -> feature.hasAttribute(ANTI_CODON))
                .collect(Collectors.groupingBy(feature -> feature.getId().orElse(feature.hashCodeString())));

        featuresWithAttribute.values().forEach(featureGroup -> rewriteGroup(featureGroup, line, rewrite));
    }

    /**
     * One feature can span several GFF3 rows sharing an {@code ID} (the fragments of a join), each
     * carrying its own copy of the same attribute value. Decide once for the whole group so every row
     * ends up with identical text. Two things break otherwise: {@code GFF3Mapper} keeps only the first
     * row's attributes when converting back to flat file, so a per-row decision would depend on row
     * order; and a row without an {@code ID} is grouped by a hash of its attributes, so rows left
     * saying different things would be split into separate features further down the line.
     */
    private void rewriteGroup(List<GFF3Feature> fragments, int line, ValueRewrite rewrite) {

        List<String> distinctValues = fragments.stream()
                .flatMap(fragment -> fragment.getAttributeList(ANTI_CODON).orElse(List.of()).stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, String> rewrites = new LinkedHashMap<>();
        for (String value : distinctValues) {
            String updated = rewrite.apply(value, fragments, line);
            if (updated != null && !updated.equals(value)) {
                rewrites.put(value, updated);
            }
        }

        if (rewrites.isEmpty()) {
            return;
        }
        fragments.forEach(fragment -> applyRewrites(fragment, rewrites));
    }

    private String canonicaliseAminoAcid(String value, List<GFF3Feature> fragments, int line) {

        Matcher matcher = VALUE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        String aminoAcid = matcher.group("aa");
        String canonical = CANONICAL_AMINO_ACIDS.get(aminoAcid.toUpperCase());
        if (canonical == null || canonical.equals(aminoAcid)) {
            return null;
        }

        log.info(
                "Fix: canonicalised {} amino acid on '{}' at line {}: '{}' -> '{}'",
                ANTI_CODON,
                accession(fragments),
                line,
                aminoAcid,
                canonical);

        return splice(matcher, canonical, matcher.group("seq"));
    }

    private String deriveSequence(String value, List<GFF3Feature> fragments, int line, SequenceLookup sequenceLookup) {

        Matcher matcher = VALUE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        Position position = parsePosition(matcher.group("pos"));
        if (position == null) {
            return null;
        }

        // A position covering anything other than three bases is not a codon, so leave it for
        // validation to reject rather than deriving a seq: of the wrong length.
        long span = position.end() - position.start() + 1;
        if (span != ANTICODON_LENGTH) {
            log.debug(
                    "Skipping {} seq: derivation on '{}' at line {}: pos: spans {} bases, not {}",
                    ANTI_CODON,
                    accession(fragments),
                    line,
                    span,
                    ANTICODON_LENGTH);
            return null;
        }

        List<GFF3Feature> containing = containingFragments(fragments, position);
        String accession =
                containing.isEmpty() ? accession(fragments) : containing.get(0).accession();

        String slice;
        try {
            slice = sequenceLookup.getSequenceSlice(
                    accession, position.start(), position.end(), SequenceRangeOption.WHOLE_SEQUENCE);
        } catch (Exception e) {
            // An unknown sequence name, or a position past the end of the sequence, both land here.
            // Neither is this fix's to report, and letting anything out of a fix aborts the whole run.
            log.debug(
                    "Skipping {} seq: derivation on '{}' at line {}: {}", ANTI_CODON, accession, line, e.getMessage());
            return null;
        }

        if (slice == null || slice.length() != ANTICODON_LENGTH) {
            return null;
        }

        boolean reverse = isReverseStrand(position, containing);
        String oriented = reverse ? reverseComplement(slice) : slice;
        if (oriented == null) {
            log.debug(
                    "Skipping {} seq: derivation on '{}' at line {}: cannot complement bases '{}'",
                    ANTI_CODON,
                    accession,
                    line,
                    slice);
            return null;
        }

        // Lowercase last, never before the reverse complement: the complement table only has uppercase
        // entries, so lowercase input would come back as zero bytes. Lowercase is also what the value
        // has to end up as, because the flat-file side compares it case-sensitively.
        String derived = oriented.toLowerCase(Locale.ROOT);
        String existing = matcher.group("seq");

        if (existing == null) {
            log.info(
                    "Fix: added {} seq: on '{}' at line {}: '{}' -> seq:{}",
                    ANTI_CODON,
                    accession,
                    line,
                    value,
                    derived);
        } else if (existing.equalsIgnoreCase(derived)) {
            if (existing.equals(derived)) {
                return null;
            }
            log.debug(
                    "Fix: lowercased {} seq: on '{}' at line {}: '{}' -> '{}'",
                    ANTI_CODON,
                    accession,
                    line,
                    existing,
                    derived);
        } else {
            log.info(
                    "Fix: corrected {} seq: on '{}' at line {}: '{}' -> '{}' (bases at {}..{}{})",
                    ANTI_CODON,
                    accession,
                    line,
                    existing,
                    derived,
                    position.start(),
                    position.end(),
                    reverse ? ", reverse strand" : "");
        }

        return splice(matcher, matcher.group("aa"), derived);
    }

    /**
     * Rebuilds the value with a new amino acid and {@code seq:}, copying every other part — the
     * position text and all the original spacing — through unchanged.
     */
    private String splice(Matcher matcher, String aminoAcid, String sequence) {

        StringBuilder rebuilt = new StringBuilder()
                .append(matcher.group("head"))
                .append(matcher.group("pos"))
                .append(matcher.group("aaHead"))
                .append(aminoAcid);

        if (sequence != null) {
            // Reuse the original separator when there was already a seq:, so its spacing survives.
            String seqHead = matcher.group("seqHead");
            rebuilt.append(seqHead != null ? seqHead : ",seq:").append(sequence);
        }

        return rebuilt.append(matcher.group("tail")).toString();
    }

    private Position parsePosition(String positionText) {

        String text = positionText.trim();
        boolean complement = false;

        Matcher complementMatcher = COMPLEMENT_PATTERN.matcher(text);
        if (complementMatcher.matches()) {
            complement = true;
            text = complementMatcher.group(1);
        }

        Matcher rangeMatcher = RANGE_PATTERN.matcher(text);
        if (!rangeMatcher.matches()) {
            return null;
        }

        try {
            long start = Long.parseLong(rangeMatcher.group(1));
            String endGroup = rangeMatcher.group(2);
            long end = endGroup != null ? Long.parseLong(endGroup) : start;
            if (start <= 0 || start > end) {
                return null;
            }
            return new Position(complement, start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Only the rows whose own start/end span the position get a say on the strand. Checking those
     * rather than the whole group matters because the rows of one feature may legitimately carry
     * different strands.
     */
    private List<GFF3Feature> containingFragments(List<GFF3Feature> fragments, Position position) {
        return fragments.stream()
                .filter(fragment -> position.start() >= fragment.getStart() && position.end() <= fragment.getEnd())
                .toList();
    }

    /**
     * A {@code complement(...)} wrapper around the position is flat-file syntax meaning "read
     * backwards", and it decides on its own whenever it is there.
     *
     * <p>Without a wrapper the strand column decides instead, so a minus-strand feature still gets the
     * bases the way they read on its own strand. Values converted from flat file always carry the
     * wrapper; this fallback is for GFF3 written by other tools, which use plain forward positions.
     */
    private boolean isReverseStrand(Position position, List<GFF3Feature> containing) {

        if (position.complement()) {
            return true;
        }
        return !containing.isEmpty() && containing.stream().allMatch(GFF3Feature::isComplement);
    }

    /** @return the reverse complement, or {@code null} if any base is not one the table knows. */
    private String reverseComplement(String bases) {

        for (int i = 0; i < bases.length(); i++) {
            // The table is 128 entries indexed by the raw byte, with no bounds check of its own.
            if (bases.charAt(i) > 127) {
                return null;
            }
        }

        byte[] complemented = Translator.reverseComplement(bases.getBytes(StandardCharsets.US_ASCII));
        for (byte base : complemented) {
            if (base == 0) {
                return null;
            }
        }
        return new String(complemented, StandardCharsets.US_ASCII);
    }

    private void applyRewrites(GFF3Feature feature, Map<String, String> rewrites) {

        List<String> values = feature.getAttributeList(ANTI_CODON).orElse(List.of());
        if (values.stream().noneMatch(rewrites::containsKey)) {
            return;
        }

        List<String> updated = values.stream()
                .map(value -> rewrites.getOrDefault(value, value))
                .toList();

        feature.setAttributeList(ANTI_CODON, new ArrayList<>(updated));
    }

    private String accession(List<GFF3Feature> fragments) {
        return fragments.isEmpty() ? "unknown" : fragments.get(0).accession();
    }
}
