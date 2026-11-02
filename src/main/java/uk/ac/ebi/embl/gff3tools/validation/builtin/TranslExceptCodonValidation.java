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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.TRANSL_EXCEPT;
import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.translation.TranslationException;
import uk.ac.ebi.embl.gff3tools.translation.except.TranslExceptAttribute;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.*;

/**
 * INSDC Minimal Annotation Specification b.vi.5: a coding region's translation exceptions must be
 * given only once on a single codon, and must correspond to one full codon (3 bp, or 1-2 bp for a
 * stop codon completed by polyadenylation) in the expected reading frame.
 *
 * <p>The clause is checked by three rules, so that each can be relaxed on its own:
 *
 * <ul>
 *   <li>{@code TRANSL_EXCEPT_ONCE_PER_CODON}: no codon is named by more than one transl_except;
 *   <li>{@code TRANSL_EXCEPT_SHORT_STOP_POSITION}: a stop codon of 1-2 bases ends the CDS;
 *   <li>{@code TRANSL_EXCEPT_READING_FRAME}: each value starts a codon of the CDS's reading frame.
 * </ul>
 *
 * <p>The rules run at normal priority, before the {@link uk.ac.ebi.embl.gff3tools.validation.fix.TranslationFix}
 * (low priority), so that a CDS failing them is never written with a translation.
 */
@Gff3Validation(
        name = "TRANSL_EXCEPT_CODON",
        description = "A CDS's transl_except values each name one full codon, once, in its reading frame")
public class TranslExceptCodonValidation implements Validation {

    private static final String ONCE_PER_CODON_RULE = "TRANSL_EXCEPT_ONCE_PER_CODON";
    private static final String SHORT_STOP_POSITION_RULE = "TRANSL_EXCEPT_SHORT_STOP_POSITION";
    private static final String READING_FRAME_RULE = "TRANSL_EXCEPT_READING_FRAME";

    private static final String ONCE_PER_CODON_MESSAGE = "A codon must carry at most one transl_except:%s";
    private static final String REPEATED_VIOLATION = "\nCDS \"%s\" at %s: \"%s\" is given more than once";
    private static final String SAME_CODON_VIOLATION = "\nCDS \"%s\" at %s: \"%s\" and \"%s\" name the same codon";
    private static final String OVERLAP_VIOLATION = "\nCDS \"%s\" at %s: \"%s\" and \"%s\" overlap at %d..%d";

    private static final String SHORT_STOP_POSITION_MESSAGE =
            "A transl_except stop codon of 1-2 bases, completed by polyadenylation, must end the CDS:%s";
    private static final String SHORT_STOP_VIOLATION =
            "\nCDS \"%s\" at %s: \"%s\" does not end the CDS, whose 3' end is at %d";

    private static final String READING_FRAME_MESSAGE =
            "A transl_except must name a codon in the CDS's reading frame:%s";
    private static final String OUT_OF_FRAME_VIOLATION =
            "\nCDS \"%s\" at %s: \"%s\" starts %d base%s into a codon (codon_start=%d)";
    private static final String BEFORE_FIRST_CODON_VIOLATION =
            "\nCDS \"%s\" at %s: \"%s\" starts before the first full codon (codon_start=%d)";

    private static final char STOP_CODON = '*';

    private static final int DEFAULT_CODON_START = 1;

    @InjectContext
    private ValidationContext context;

    /** The same value twice on one row, or two values sharing a base: the same codon, or overlapping ones. */
    @ValidationMethod(
            rule = ONCE_PER_CODON_RULE,
            description = "No codon of a CDS is named by more than one transl_except",
            type = ANNOTATION)
    public void validateOncePerCodon(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        for (CdsExceptions cds : readCdsExceptions(annotation)) {
            for (String value : cds.repeated()) {
                violations.add(REPEATED_VIOLATION.formatted(cds.id(), cds.location(), value));
            }

            List<String> values = List.copyOf(cds.exceptions().keySet());
            for (int i = 0; i < values.size(); i++) {
                for (int j = i + 1; j < values.size(); j++) {
                    findSharedBases(cds, values.get(i), values.get(j)).ifPresent(violations::add);
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, ONCE_PER_CODON_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * A genomic T or TA at the 3' end of a coding sequence becomes the stop codon TAA once the poly(A)
     * tail is added. Only the 3' end meets the tail, so a shorter stop codon must be the last bases of
     * the CDS.
     */
    @ValidationMethod(
            rule = SHORT_STOP_POSITION_RULE,
            description = "A transl_except stop codon of 1-2 bases ends the CDS",
            type = ANNOTATION)
    public void validateShortStopPosition(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        for (CdsExceptions cds : readCdsExceptions(annotation)) {
            cds.exceptions().forEach((text, value) -> {
                if (!isShortStopCodon(value)) {
                    return;
                }
                cds.layout()
                        .locate(value.getStartPosition(), value.getEndPosition())
                        .filter(span -> span.last() != cds.layout().length())
                        .ifPresent(span -> violations.add(SHORT_STOP_VIOLATION.formatted(
                                cds.id(), cds.location(), text, cds.layout().threePrimeEnd())));
            });
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, SHORT_STOP_POSITION_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * A codon starts at codon_start, or a multiple of three bases after it, counted along the spliced
     * coding sequence from its 5' end.
     *
     * <p>Pseudo CDSs, which may carry frameshifts, and CDSs with an exception such as RNA editing are
     * not read from the genome in one frame, and are left out as the translation leaves them out. A
     * ribosomal slippage is written as overlapping segments, so the spliced sequence keeps one frame
     * across it, and it is checked like any other CDS.
     */
    @ValidationMethod(
            rule = READING_FRAME_RULE,
            description = "Each transl_except of a CDS names a codon in the CDS's reading frame",
            type = ANNOTATION)
    public void validateReadingFrame(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        for (CdsExceptions cds : readCdsExceptions(annotation)) {
            Optional<Integer> codonStart = findCodonStart(cds.segments());
            if (codonStart.isEmpty() || isNotReadInOneFrame(cds.segments())) {
                continue;
            }
            cds.exceptions().forEach((text, value) -> findFrameViolation(cds, text, value, codonStart.get())
                    .ifPresent(violations::add));
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, READING_FRAME_MESSAGE.formatted(String.join("", violations)));
        }
    }

    private Optional<String> findSharedBases(CdsExceptions cds, String firstText, String secondText) {
        TranslExceptAttribute first = cds.exceptions().get(firstText);
        TranslExceptAttribute second = cds.exceptions().get(secondText);
        int sharedStart = Math.max(first.getStartPosition(), second.getStartPosition());
        int sharedEnd = Math.min(first.getEndPosition(), second.getEndPosition());
        if (sharedStart > sharedEnd) {
            return Optional.empty();
        }

        boolean sameCodon = first.getStartPosition().equals(second.getStartPosition())
                && first.getEndPosition().equals(second.getEndPosition());
        return Optional.of(
                sameCodon
                        ? SAME_CODON_VIOLATION.formatted(cds.id(), cds.location(), firstText, secondText)
                        : OVERLAP_VIOLATION.formatted(
                                cds.id(), cds.location(), firstText, secondText, sharedStart, sharedEnd));
    }

    private boolean isShortStopCodon(TranslExceptAttribute value) {
        return value.getAminoAcidLetter() == STOP_CODON && lengthOf(value) < 3;
    }

    /** 3 bases, or 1-2 for a stop codon completed by polyadenylation. */
    private boolean hasCodonLength(TranslExceptAttribute value) {
        int length = lengthOf(value);
        return length == 3 || (length > 0 && length < 3 && value.getAminoAcidLetter() == STOP_CODON);
    }

    private int lengthOf(TranslExceptAttribute value) {
        return value.getEndPosition() - value.getStartPosition() + 1;
    }

    private Optional<String> findFrameViolation(
            CdsExceptions cds, String text, TranslExceptAttribute value, int codonStart) {
        Optional<CdsLayout.Span> span = cds.layout().locate(value.getStartPosition(), value.getEndPosition());
        if (span.isEmpty()) {
            return Optional.empty();
        }

        long first = span.get().first();
        if (first < codonStart) {
            return Optional.of(BEFORE_FIRST_CODON_VIOLATION.formatted(cds.id(), cds.location(), text, codonStart));
        }
        long intoCodon = (first - codonStart) % 3;
        if (intoCodon == 0) {
            return Optional.empty();
        }
        return Optional.of(OUT_OF_FRAME_VIOLATION.formatted(
                cds.id(), cds.location(), text, intoCodon, intoCodon == 1 ? "" : "s", codonStart));
    }

    /** The CDSs of the annotation that carry at least one transl_except read as a codon. */
    private List<CdsExceptions> readCdsExceptions(GFF3Annotation annotation) {
        List<CdsExceptions> result = new ArrayList<>();
        for (List<GFF3Feature> segments :
                ValidationUtils.groupFeaturesById(annotation, this::isCds).values()) {
            Map<String, TranslExceptAttribute> exceptions = new LinkedHashMap<>();
            Set<String> repeated = new LinkedHashSet<>();
            for (GFF3Feature segment : segments) {
                Set<String> onThisRow = new HashSet<>();
                for (String value : segment.getAttributeList(TRANSL_EXCEPT).orElse(List.of())) {
                    if (!exceptions.containsKey(value)) {
                        readCodon(value).ifPresent(codon -> exceptions.put(value, codon));
                    }
                    if (exceptions.containsKey(value) && !onThisRow.add(value)) {
                        repeated.add(value);
                    }
                }
            }
            if (!exceptions.isEmpty()) {
                result.add(new CdsExceptions(segments, exceptions, repeated, new CdsLayout(segments)));
            }
        }
        return result;
    }

    /**
     * The value read as a codon. Empty where it cannot be read or has a length no codon can have, which
     * {@code TRANSL_EXCEPT_LOCATION} reports.
     */
    private Optional<TranslExceptAttribute> readCodon(String value) {
        try {
            return Optional.of(new TranslExceptAttribute(value)).filter(this::hasCodonLength);
        } catch (TranslationException e) {
            return Optional.empty();
        }
    }

    /**
     * The codon_start of the first row carrying one, or the default where none does, as the translation
     * reads it. Empty where it is not 1, 2 or 3, which the translation reports.
     */
    private Optional<Integer> findCodonStart(List<GFF3Feature> segments) {
        String value = segments.stream()
                .map(segment -> segment.getAttribute(GFF3Attributes.CODON_START).orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (value == null) {
            return Optional.of(DEFAULT_CODON_START);
        }
        try {
            int codonStart = Integer.parseInt(value.trim());
            return codonStart >= 1 && codonStart <= 3 ? Optional.of(codonStart) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private boolean isNotReadInOneFrame(List<GFF3Feature> segments) {
        return segments.stream()
                .anyMatch(segment -> segment.hasAttribute(GFF3Attributes.PSEUDO)
                        || segment.hasAttribute(GFF3Attributes.PSEUDOGENE)
                        || segment.hasAttribute(GFF3Attributes.EXCEPTION));
    }

    private boolean isCds(GFF3Feature feature) {
        return context.get(OntologyClient.class).isSoTerm(feature.getName(), OntologyTerm.CDS.ID);
    }

    /**
     * One CDS with its distinct readable transl_except values, keyed by their text, and the values
     * given more than once on a single row.
     */
    private record CdsExceptions(
            List<GFF3Feature> segments,
            Map<String, TranslExceptAttribute> exceptions,
            Set<String> repeated,
            CdsLayout layout) {

        String id() {
            return ValidationUtils.representativeOfFeatureGroup(segments)
                    .getId()
                    .orElse("<no ID>");
        }

        String location() {
            return ValidationUtils.getGroupLocationString(segments);
        }
    }

    /**
     * The segments of one CDS in the order they are translated, so that a position on the sequence can
     * be found again in the spliced coding sequence.
     *
     * <p>The order is the one {@link uk.ac.ebi.embl.gff3tools.validation.fix.TranslationFix} builds: by
     * start position unless the CDS is trans-spliced, in which case the rows keep the order they were
     * written in. Each segment is read from its 5' end, so a CDS whose segments are all on the minus
     * strand is read from its last segment backwards, while a join on mixed strands reads each segment
     * in its own direction.
     *
     * <p>Positions are compared with segment coordinates as plain numbers: on a circular sequence both
     * are in the feature's own numbering, virtual coordinates past the end included.
     */
    private static final class CdsLayout {

        private final List<GFF3Feature> readingOrder;

        CdsLayout(List<GFF3Feature> segments) {
            List<GFF3Feature> ordered = new ArrayList<>(segments);
            boolean transSpliced = ordered.stream().anyMatch(s -> s.hasAttribute(GFF3Attributes.TRANS_SPLICING));
            if (!transSpliced) {
                ordered.sort(Comparator.comparingLong(GFF3Feature::getStart));
            }

            boolean mixedStrands =
                    ordered.stream().map(GFF3Feature::isComplement).distinct().count() > 1;
            if (!mixedStrands && !ordered.isEmpty() && ordered.get(0).isComplement()) {
                Collections.reverse(ordered);
            }
            this.readingOrder = List.copyOf(ordered);
        }

        /** The length of the spliced coding sequence. */
        long length() {
            return readingOrder.stream().mapToLong(GFF3Feature::getLength).sum();
        }

        /**
         * Where the bases {@code start..end} sit in the spliced coding sequence, counted from 1 at its
         * 5' end. Empty where no single segment holds them all, which {@code TRANSL_EXCEPT_LOCATION}
         * reports. Where segments overlap, as with ribosomal slippage, the first segment in reading
         * order that holds them is taken.
         */
        Optional<Span> locate(long start, long end) {
            long preceding = 0;
            for (GFF3Feature segment : readingOrder) {
                if (start >= segment.getStart() && end <= segment.getEnd()) {
                    return Optional.of(
                            segment.isComplement()
                                    ? new Span(
                                            preceding + segment.getEnd() - end + 1,
                                            preceding + segment.getEnd() - start + 1)
                                    : new Span(
                                            preceding + start - segment.getStart() + 1,
                                            preceding + end - segment.getStart() + 1));
                }
                preceding += segment.getLength();
            }
            return Optional.empty();
        }

        /** The sequence position of the CDS's 3'-most base. */
        long threePrimeEnd() {
            GFF3Feature last = readingOrder.get(readingOrder.size() - 1);
            return last.isComplement() ? last.getStart() : last.getEnd();
        }

        /** Positions in the spliced coding sequence: {@code first} is the 5'-most base, {@code last} the 3'-most. */
        record Span(long first, long last) {}
    }
}
