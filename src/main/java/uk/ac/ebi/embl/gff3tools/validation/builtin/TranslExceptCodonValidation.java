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
 * <p>A transl_except names one codon whose translation differs from the genetic code, so a codon
 * named twice is either repeated or contradicted. The one or two bases of a stop codon left
 * incomplete in the genome, a T or TA that the poly(A) tail completes to TAA, must end the coding
 * sequence, since only its 3' end meets the tail. And a codon must start at codon_start, or a multiple
 * of three bases after it, counted along the spliced coding sequence from its 5' end.
 *
 * <p>The rows of a join each carry a copy of the CDS's values, so a value repeated across rows is
 * counted once. {@code TRANSL_EXCEPT_LOCATION} reports values that cannot be read, that lie outside
 * the CDS, or that span other than 3 bases without being a stop codon; those are not reported again.
 *
 * <p>The reading frame is not checked on CDSs whose coding sequence is not read from the genome in
 * one frame: pseudo CDSs, which may carry frameshifts, and CDSs with an exception such as RNA editing,
 * which the translation skips as well.
 */
@Gff3Validation(
        name = "TRANSL_EXCEPT_CODON",
        description = "A CDS's transl_except values each name one full codon, once, in its reading frame")
public class TranslExceptCodonValidation implements Validation {

    private static final String RULE = "TRANSL_EXCEPT_CODON";

    private static final char STOP_CODON = '*';

    private static final int DEFAULT_CODON_START = 1;

    private static final String CODON_MESSAGE =
            "Each transl_except must name one full codon of its CDS, once, in the CDS's reading frame:%s";

    private static final String CDS_HEADER = "\nCDS %s:";

    private static final String PROBLEM = "\n  - %s";

    private static final String REPEATED_MESSAGE = "\"%s\" is given more than once; keep one";

    private static final String SAME_CODON_MESSAGE = "\"%s\" and \"%s\" both name the codon at %d..%d; keep one";

    private static final String OVERLAP_MESSAGE =
            "\"%s\" and \"%s\" overlap at %d..%d, so they cannot both be codons of this CDS";

    private static final String TOO_LONG_MESSAGE =
            "\"%s\" spans %d bases, but a stop codon spans 3 bases, or 1-2 at the 3' end of the CDS";

    private static final String NOT_AT_END_MESSAGE = "\"%s\" is a %d-base stop codon but does not end the CDS, "
            + "whose 3' end is at %d; only the last 1-2 bases of a CDS can form a stop codon completed by "
            + "polyadenylation";

    private static final String OUT_OF_FRAME_MESSAGE = "\"%s\" is out of frame: it starts %d base%s into a codon "
            + "(base %d of the spliced coding sequence, codon_start=%d)";

    private static final String BEFORE_FIRST_CODON_MESSAGE = "\"%s\" is out of frame: it starts at base %d of the "
            + "spliced coding sequence, before the first full codon at base %d (codon_start=%d)";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = RULE,
            description = "A CDS's transl_except values each name one full codon, once, in its reading frame",
            type = ANNOTATION,
            severity = RuleSeverity.ERROR)
    public void validateTranslExceptCodons(GFF3Annotation annotation, int line) throws ValidationException {
        List<String> violations = new ArrayList<>();

        for (List<GFF3Feature> segments :
                ValidationUtils.groupFeaturesById(annotation, this::isCds).values()) {
            Map<String, TranslExceptAttribute> values = readDistinct(segments);
            if (values.isEmpty()) {
                continue;
            }

            CdsLayout layout = new CdsLayout(segments);
            List<String> problems = new ArrayList<>();
            checkOncePerCodon(segments, values, problems);
            checkStopCodonSpans(values, layout, problems);
            checkReadingFrame(segments, values, layout, problems);

            if (!problems.isEmpty()) {
                StringBuilder violation = new StringBuilder(CDS_HEADER.formatted(describe(segments)));
                problems.forEach(problem -> violation.append(PROBLEM.formatted(problem)));
                violations.add(violation.toString());
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(line, CODON_MESSAGE.formatted(String.join("", violations)));
        }
    }

    /**
     * The same text twice on one row, and distinct values sharing any base: written differently but
     * naming the same codon, or overlapping it.
     */
    private void checkOncePerCodon(
            List<GFF3Feature> segments, Map<String, TranslExceptAttribute> values, List<String> problems) {
        Set<String> repeated = new LinkedHashSet<>();
        for (GFF3Feature segment : segments) {
            Set<String> seen = new HashSet<>();
            for (String value : segment.getAttributeList(TRANSL_EXCEPT).orElse(List.of())) {
                if (values.containsKey(value) && !seen.add(value)) {
                    repeated.add(value);
                }
            }
        }
        repeated.forEach(value -> problems.add(REPEATED_MESSAGE.formatted(value)));

        List<Map.Entry<String, TranslExceptAttribute>> entries = List.copyOf(values.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            TranslExceptAttribute a = entries.get(i).getValue();
            for (int j = i + 1; j < entries.size(); j++) {
                TranslExceptAttribute b = entries.get(j).getValue();
                int sharedStart = Math.max(a.getStartPosition(), b.getStartPosition());
                int sharedEnd = Math.min(a.getEndPosition(), b.getEndPosition());
                if (sharedStart > sharedEnd) {
                    continue;
                }
                boolean sameCodon = a.getStartPosition().equals(b.getStartPosition())
                        && a.getEndPosition().equals(b.getEndPosition());
                problems.add((sameCodon ? SAME_CODON_MESSAGE : OVERLAP_MESSAGE)
                        .formatted(entries.get(i).getKey(), entries.get(j).getKey(), sharedStart, sharedEnd));
            }
        }
    }

    /** A stop codon may be shorter than 3 bases only where it ends the coding sequence, and never longer. */
    private void checkStopCodonSpans(
            Map<String, TranslExceptAttribute> values, CdsLayout layout, List<String> problems) {
        values.forEach((text, value) -> {
            if (value.getAminoAcidLetter() != STOP_CODON) {
                return;
            }

            int span = value.getEndPosition() - value.getStartPosition() + 1;
            if (span > 3) {
                problems.add(TOO_LONG_MESSAGE.formatted(text, span));
                return;
            }
            if (span == 3) {
                return;
            }

            Optional<CdsLayout.Span> located = layout.locate(value.getStartPosition(), value.getEndPosition());
            if (located.isPresent() && located.get().last() != layout.length()) {
                problems.add(NOT_AT_END_MESSAGE.formatted(text, span, layout.threePrimeEnd()));
            }
        });
    }

    private void checkReadingFrame(
            List<GFF3Feature> segments,
            Map<String, TranslExceptAttribute> values,
            CdsLayout layout,
            List<String> problems) {
        Optional<Integer> codonStart = findCodonStart(segments);
        if (codonStart.isEmpty() || isNotReadInOneFrame(segments)) {
            return;
        }

        values.forEach((text, value) -> {
            Optional<CdsLayout.Span> located = layout.locate(value.getStartPosition(), value.getEndPosition());
            if (located.isEmpty()) {
                return;
            }
            long first = located.get().first();
            if (first < codonStart.get()) {
                problems.add(BEFORE_FIRST_CODON_MESSAGE.formatted(text, first, codonStart.get(), codonStart.get()));
                return;
            }
            long intoCodon = Math.floorMod(first - codonStart.get(), 3);
            if (intoCodon != 0) {
                problems.add(OUT_OF_FRAME_MESSAGE.formatted(
                        text, intoCodon, intoCodon == 1 ? "" : "s", first, codonStart.get()));
            }
        });
    }

    /**
     * The distinct values carried by the CDS's rows, keyed by their text, leaving out those that cannot
     * be read.
     */
    private Map<String, TranslExceptAttribute> readDistinct(List<GFF3Feature> segments) {
        Map<String, TranslExceptAttribute> values = new LinkedHashMap<>();
        for (GFF3Feature segment : segments) {
            for (String value : segment.getAttributeList(TRANSL_EXCEPT).orElse(List.of())) {
                if (value == null || values.containsKey(value)) {
                    continue;
                }
                try {
                    values.put(value, new TranslExceptAttribute(value));
                } catch (TranslationException e) {
                    // Malformed values are reported by TRANSL_EXCEPT_LOCATION.
                }
            }
        }
        return values;
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

    /** The CDS's ID and the span its segments cover, with the line of its first segment where it was read from a file. */
    private String describe(List<GFF3Feature> segments) {
        GFF3Feature representative = ValidationUtils.representativeOfFeatureGroup(segments);
        long end = segments.stream().mapToLong(GFF3Feature::getEnd).max().orElse(representative.getEnd());
        String location = "\"%s\" at %s:%d..%d"
                .formatted(
                        representative.getId().orElse("<no ID>"),
                        representative.accession(),
                        representative.getStart(),
                        end);
        return representative.getLine() >= 0 ? location + " (line %d)".formatted(representative.getLine()) : location;
    }

    private boolean isCds(GFF3Feature feature) {
        return context.get(OntologyClient.class).isSoTerm(feature.getName(), OntologyTerm.CDS.ID);
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
