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
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Gff3Fix(
        name = "ANTICODON_ATTRIBUTE",
        description = "Correct the anticodon amino acid casing and fill in its seq: from the sequence")
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

    private record Position(boolean hasComplementWrapper, long start, long end) {}

    private record SequenceReader(SequenceLookup lookup, String accession, int line) {

        String read(Position position) throws Exception {
            return lookup.getSequenceSlice(accession, position.start(), position.end());
        }
    }

    @FixMethod(
            rule = "ANTICODON_ATTRIBUTE_FIX_AMINO_ACID_CASE",
            description = "Corrects the upper/lower case of amino acid names",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void fixAminoAcidCase(GFF3Annotation annotation, int line) {

        for (GFF3Feature feature : annotation.getFeatures()) {

            List<String> values = feature.getAttributeList(ANTI_CODON).orElse(null);
            if (values == null) {
                continue;
            }

            List<String> updatedValues = new ArrayList<>(values.size());
            for (String value : values) {
                String updated = withCorrectedAminoAcidCase(value, feature.accession(), line);
                updatedValues.add(updated != null ? updated : value);
            }

            if (!updatedValues.equals(values)) {
                feature.setAttributeList(ANTI_CODON, updatedValues);
            }
        }
    }

    /**
     * Replaces the amino acid name with its preferred spelling, e.g. {@code aa:SeC} becomes
     * {@code aa:Sec}. Every other character in the value is left exactly as it was.
     *
     * <p>No grouping by feature ID is needed here, unlike {@link #addSequence}: the new value depends
     * only on the old text, so two rows of the same feature carrying the same value always come out
     * the same.
     *
     * @return the corrected value, or {@code null} if there was nothing to correct
     */
    private String withCorrectedAminoAcidCase(String value, String accession, int line) {

        if (value == null) {
            return null;
        }

        Matcher matcher = VALUE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        String aminoAcid = matcher.group("aa");
        String preferredSpelling = CANONICAL_AMINO_ACIDS.get(aminoAcid.toUpperCase());

        if (preferredSpelling == null || preferredSpelling.equals(aminoAcid)) {
            return null;
        }

        log.info(
                "Fix: corrected anticodon amino acid case on '{}' at line {}: '{}' -> '{}'",
                accession,
                line,
                aminoAcid,
                preferredSpelling);

        // Swap out just the amino acid, keeping the rest of the string byte for byte.
        return value.substring(0, matcher.start("aa")) + preferredSpelling + value.substring(matcher.end("aa"));
    }

    @FixMethod(
            rule = "ANTICODON_ATTRIBUTE_ADD_SEQUENCE",
            description = "Adds the anticodon sequence section from the bases at positions, correcting it if present",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void addSequence(GFF3Annotation annotation, int line) throws Exception {
        if (context == null || !context.contains(SequenceLookup.class)) {
            return;
        }
        // Registered but empty is the normal case when no sequence was supplied, so skip quietly
        // rather than failing the run.
        SequenceLookup sequenceLookup = context.get(SequenceLookup.class);
        if (sequenceLookup == null) {
            return;
        }

        Map<String, List<GFF3Feature>> rowsByFeature = rowsByFeature(annotation);
        if (rowsByFeature.isEmpty()) {
            return;
        }

        // An annotation covers exactly one sequence, so the accession is the same for every feature in
        // it and both the lookup and the check happen once here rather than per value.
        String accession = annotation.getAccession();
        if (!sequenceLookup.hasSequence(accession)) {
            log.debug("Skipping anticodon on '{}' at line {}: no sequence source has it", accession, line);
            return;
        }

        SequenceReader reader = new SequenceReader(sequenceLookup, accession, line);
        for (List<GFF3Feature> rows : rowsByFeature.values()) {
            addSequenceToFeature(rows, reader);
        }
    }

    /**
     * Groups the rows carrying an {@code anticodon} by the feature they belong to. Rows sharing an
     * {@code ID} are the fragments of one feature and each carries its own copy of the attribute.
     *
     * <p>Unlike {@link #fixAminoAcidCase} this has to group, because the new value depends on the rows
     * and not just on the old text. Every row of a feature must end up with the same text: when
     * converting back to flat file only the first row's attributes are kept, and a row without an
     * {@code ID} is grouped by a hash of its attributes, so rows left saying different things would be
     * split into separate features later on.
     */
    private Map<String, List<GFF3Feature>> rowsByFeature(GFF3Annotation annotation) {

        Map<String, List<GFF3Feature>> rowsByFeature = new LinkedHashMap<>();

        for (GFF3Feature feature : annotation.getFeatures()) {
            if (!feature.hasAttribute(ANTI_CODON)) {
                continue;
            }
            String key = feature.getId().orElse(feature.hashCodeString());
            rowsByFeature.computeIfAbsent(key, unused -> new ArrayList<>()).add(feature);
        }

        return rowsByFeature;
    }

    private void addSequenceToFeature(List<GFF3Feature> rows, SequenceReader reader) throws Exception {

        Set<String> values = new LinkedHashSet<>();
        for (GFF3Feature row : rows) {
            for (String value : row.getAttributeList(ANTI_CODON).orElse(List.of())) {
                if (value != null) {
                    values.add(value);
                }
            }
        }

        Map<String, String> updatedValues = new LinkedHashMap<>();
        for (String value : values) {
            String updated = withSequence(value, rows, reader);
            if (updated != null && !updated.equals(value)) {
                updatedValues.put(value, updated);
            }
        }

        if (updatedValues.isEmpty()) {
            return;
        }

        for (GFF3Feature row : rows) {
            List<String> rewritten = new ArrayList<>();
            for (String value : row.getAttributeList(ANTI_CODON).orElse(List.of())) {
                rewritten.add(updatedValues.getOrDefault(value, value));
            }
            row.setAttributeList(ANTI_CODON, rewritten);
        }
    }

    /**
     * Reads the bases at the position and puts them in the {@code seq:} part.
     *
     * @return the updated value, or {@code null} if there was nothing to change
     */
    private String withSequence(String value, List<GFF3Feature> rows, SequenceReader reader) throws Exception {

        Matcher matcher = VALUE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        Position position = parsePosition(matcher.group("pos"));
        if (position == null) {
            return null;
        }

        // A position covering anything other than three bases is not a codon, so leave it for
        // validation to reject rather than reading a seq: of the wrong length.
        long span = position.end() - position.start() + 1;
        if (span != ANTICODON_LENGTH) {
            log.debug(
                    "Skipping anticodon seq: on '{}' at line {}: pos: spans {} bases, not {}",
                    reader.accession(),
                    reader.line(),
                    span,
                    ANTICODON_LENGTH);
            return null;
        }

        String bases = reader.read(position);
        if (bases == null || bases.length() != ANTICODON_LENGTH) {
            return null;
        }

        boolean readBackwards = isReverseStrand(position, rows);
        String oriented = readBackwards ? reverseComplement(bases) : bases;
        if (oriented == null) {
            log.debug(
                    "Skipping anticodon seq: on '{}' at line {}: cannot complement bases",
                    reader.accession(),
                    reader.line());
            return null;
        }

        // Lowercase because the flat-file side compares seq: case-sensitively.
        String newSequence = oriented.toLowerCase(Locale.ROOT);
        String oldSequence = matcher.group("seq");

        if (newSequence.equals(oldSequence)) {
            return null;
        }

        logSequenceChange(reader, position, oldSequence, newSequence, readBackwards);
        return withSequenceReplaced(value, matcher, newSequence);
    }

    private void logSequenceChange(
            SequenceReader reader, Position position, String oldSequence, String newSequence, boolean readBackwards) {

        if (oldSequence == null) {
            log.info(
                    "Fix: {} {}..{} anticodon seq: added as '{}'{}",
                    reader.accession(),
                    position.start(),
                    position.end(),
                    newSequence,
                    readBackwards ? " (read backwards)" : "");
        } else if (!oldSequence.equalsIgnoreCase(newSequence)) {
            log.info(
                    "Fix: {} {}..{} anticodon changed from '{}' to '{}'{}",
                    reader.accession(),
                    position.start(),
                    position.end(),
                    oldSequence,
                    newSequence,
                    readBackwards ? " (read backwards)" : "");
        }
    }

    /** Replaces an existing {@code seq:}, or adds one just before the closing bracket. */
    private String withSequenceReplaced(String value, Matcher matcher, String newSequence) {

        if (matcher.group("seq") != null) {
            return value.substring(0, matcher.start("seq")) + newSequence + value.substring(matcher.end("seq"));
        }

        int closingBracket = matcher.start("tail");
        return value.substring(0, closingBracket) + ",seq:" + newSequence + value.substring(closingBracket);
    }

    private Position parsePosition(String positionText) {

        String text = positionText.trim();
        Matcher complementMatcher = COMPLEMENT_PATTERN.matcher(text);
        boolean complement = complementMatcher.matches();
        if (complement) {
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
     * The strand decides. A minus-strand tRNA reads backwards whether or not the position carries a
     * {@code complement(...)} wrapper, and in ENA the wrapper is usually absent: of 1020 minus-strand
     * tRNAs surveyed, 93% wrote a plain forward position and still annotated the reverse complement.
     *
     * <p>The wrapper is therefore only a fallback, for when no row covers the position and the strand
     * has nothing to say about that coordinate. Where the two contradict each other the strand wins,
     * because it is the field that is always populated — that combination was not found at all in
     * 3944 surveyed anticodons, so it only arises in malformed input.
     *
     * <p>Only the rows whose own start/end span the position get a say, because the rows of one feature
     * may legitimately carry different strands.
     */
    private boolean isReverseStrand(Position position, List<GFF3Feature> rows) {

        boolean anyRowCoversPosition = false;
        for (GFF3Feature row : rows) {
            if (position.start() >= row.getStart() && position.end() <= row.getEnd()) {
                anyRowCoversPosition = true;
                if (!row.isComplement()) {
                    return false;
                }
            }
        }
        if (anyRowCoversPosition) {
            return true;
        }

        return position.hasComplementWrapper();
    }

    /** @return the reverse complement, or {@code null} if any base is not one the table knows. */
    private String reverseComplement(String bases) {

        // Uppercase first: the complement table has no lowercase entries, so lowercase bases would
        // silently come back as zeroes. Anything outside ASCII becomes '?', which the table also maps
        // to zero, so the loop below catches both.
        byte[] complemented =
                Translator.reverseComplement(bases.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        for (byte base : complemented) {
            if (base == 0) {
                return null;
            }
        }
        return new String(complemented, StandardCharsets.US_ASCII);
    }
}
