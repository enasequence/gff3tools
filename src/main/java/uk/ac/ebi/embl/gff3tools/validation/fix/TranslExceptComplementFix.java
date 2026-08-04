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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.TRANSL_EXCEPT;
import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

/**
 * Removes a redundant {@code complement(...)} wrapper from the {@code pos:} location of
 * {@code transl_except}. This is the GFF3 counterpart of sequencetools'
 * {@code Transl_exceptLocationFix} ("Invalid Location:Complement ignored in transl_except").
 *
 * <p>In GFF3 the reading direction lives in column 7, and {@code Translator} derives it solely
 * from there — the wrapper only duplicates that, and the numeric range is identical either way.
 * The wrapper is therefore stripped <strong>only where the duplication is provable</strong>:
 * every fragment that contains the codon must be on the minus strand. A plus strand, an unknown
 * strand ({@code .}/{@code ?}), or a codon that resolves to no fragment is left untouched, so a
 * genuine contradiction is never silently resolved — {@code TRANSL_EXCEPT_STRAND_CONFLICT}
 * reports those instead.
 *
 * <p><strong>{@code anticodon} is deliberately not handled.</strong> sequencetools'
 * {@code AnticodonQualifierFix} re-extracts the {@code seq:} payload via {@code SegmentFactory}
 * using the anticodon location's own complement flag, so stripping it there would make the value
 * internally false. {@code transl_except} carries no such direction-dependent payload: its
 * {@code aa:} is stated outright by the submitter, never computed from the sequence.
 */
@Slf4j
@Gff3Fix(
        name = "TRANSL_EXCEPT_COMPLEMENT",
        description = "Remove the redundant complement(...) wrapper from transl_except locations")
public class TranslExceptComplementFix implements Fix {

    private static final String MINUS_STRAND = "-";

    /**
     * Anchored so that only a simple range is ever rewritten. Compound bodies
     * ({@code join}/{@code order}), fuzzy bounds ({@code <100}) and remote accessions
     * ({@code X12345.1:100..102}) all fail to match and are left for validation to judge.
     *
     * <p>Group 1 is everything up to the wrapper, group 2 the range, group 3 the remainder, so
     * recomposing {@code 1 + 2 + 3} preserves the original spacing, case and {@code aa:} token
     * exactly. In particular the amino acid is never canonicalised — that is orientation-unrelated
     * scope creep which sequencetools happens to also perform.
     */
    private static final Pattern POS_COMPLEMENT_PATTERN = Pattern.compile(
            "^(\\s*\\(\\s*pos\\s*:\\s*)complement\\(\\s*(\\d+(?:\\s*\\.\\.\\s*\\d+)?)\\s*\\)(\\s*,\\s*aa\\s*:\\s*[^\\s,)]+\\s*\\)\\s*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RANGE_PATTERN = Pattern.compile("^(\\d+)(?:\\s*\\.\\.\\s*(\\d+))?$");

    @FixMethod(
            rule = "TRANSL_EXCEPT_COMPLEMENT",
            description = "Strip complement(...) from transl_except locations on minus-strand features",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void fixAnnotation(GFF3Annotation annotation, int line) {

        Map<String, List<GFF3Feature>> grouped = annotation.getFeatures().stream()
                .filter(feature -> feature.hasAttribute(TRANSL_EXCEPT))
                .collect(Collectors.groupingBy(feature -> feature.getId().orElse(feature.hashCodeString())));

        for (List<GFF3Feature> fragments : grouped.values()) {
            // A fix must never propagate an exception: executeFixes passes a null severity to
            // handleRuleException, which turns any escape into a hard error that --rules cannot
            // suppress. Skip the offending group instead.
            try {
                fixGroup(fragments, line);
            } catch (RuntimeException e) {
                log.warn("TRANSL_EXCEPT_COMPLEMENT: skipping feature group at line {}: {}", line, e.getMessage());
            }
        }
    }

    /**
     * Decides once per distinct value, using the whole ID-group, so that every row of a join ends
     * up with identical text. {@code GFF3AnnotationFactory} replicates the attribute onto each
     * location row, and {@code GFF3Mapper} keeps only the first row's attributes when merging a
     * join back to flat file — so a per-row decision would be silently order-dependent.
     */
    private void fixGroup(List<GFF3Feature> fragments, int line) {

        Map<String, String> rewrites = new LinkedHashMap<>();
        Set<String> evaluated = new HashSet<>();

        for (GFF3Feature fragment : fragments) {
            for (String value : fragment.getAttributeList(TRANSL_EXCEPT).orElse(List.of())) {
                if (value == null || !evaluated.add(value)) {
                    continue;
                }
                String stripped = stripIfRedundant(value, fragments);
                if (stripped != null) {
                    rewrites.put(value, stripped);
                }
            }
        }

        if (rewrites.isEmpty()) {
            return;
        }

        for (GFF3Feature fragment : fragments) {
            applyRewrites(fragment, rewrites, line);
        }
    }

    /**
     * @return the normalised value, or {@code null} when the wrapper must be preserved.
     */
    private String stripIfRedundant(String value, List<GFF3Feature> fragments) {

        Matcher matcher = POS_COMPLEMENT_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        Matcher range = RANGE_PATTERN.matcher(matcher.group(2));
        if (!range.matches()) {
            return null;
        }

        long start;
        long end;
        try {
            start = Long.parseLong(range.group(1));
            end = range.group(2) != null ? Long.parseLong(range.group(2)) : start;
        } catch (NumberFormatException e) {
            // Out-of-range digits: leave the value for TRANSL_EXCEPT_LOCATION to judge.
            return null;
        }

        if (!isRedundantWithStrand(fragments, start, end)) {
            return null;
        }

        return matcher.group(1) + matcher.group(2) + matcher.group(3);
    }

    /**
     * The wrapper is redundant only when every fragment actually containing the codon is on the
     * minus strand. Testing containment rather than "the whole group is minus" keeps a legitimate
     * mixed-strand (trans-spliced) join fixable when the codon sits in its minus-strand segment,
     * while still refusing when the codon sits in a plus-strand segment.
     */
    private boolean isRedundantWithStrand(List<GFF3Feature> fragments, long start, long end) {

        List<GFF3Feature> containing = fragments.stream()
                .filter(fragment -> start >= fragment.getStart() && end <= fragment.getEnd())
                .toList();

        return !containing.isEmpty()
                && containing.stream().allMatch(fragment -> MINUS_STRAND.equals(fragment.getStrand()));
    }

    private void applyRewrites(GFF3Feature feature, Map<String, String> rewrites, int line) {

        List<String> values = feature.getAttributeList(TRANSL_EXCEPT).orElse(null);
        if (values == null || values.isEmpty()) {
            return;
        }

        List<String> updated = new ArrayList<>(values.size());
        boolean changed = false;

        for (String value : values) {
            String rewritten = rewrites.get(value);
            if (rewritten == null) {
                updated.add(value);
                continue;
            }
            changed = true;
            updated.add(rewritten);
            log.info(
                    "Fix: removed redundant complement() from {} on '{}' at line {}: '{}' -> '{}'",
                    TRANSL_EXCEPT,
                    feature.accession(),
                    line,
                    value,
                    rewritten);
        }

        if (changed) {
            feature.setAttributeList(TRANSL_EXCEPT, updated);
        }
    }
}
