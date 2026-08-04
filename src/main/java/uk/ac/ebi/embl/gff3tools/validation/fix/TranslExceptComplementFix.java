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
 * Rewrites {@code transl_except} attribute values to drop a {@code complement(...)} wrapper around
 * the position, so {@code (pos:complement(4370..4372),aa:Sec)} becomes {@code (pos:4370..4372,aa:Sec)}.
 * That wrapper is flat-file syntax meaning "read backwards" and survives FF-&gt;GFF3 conversion, but
 * GFF3 already states direction in the strand column, so it is duplicated information — the numbers
 * are identical either way.
 *
 * <p>It is dropped only when the strand column agrees, i.e. holds the literal {@code "-"}.
 * Otherwise the two contradict each other, so the value is left
 * untouched and {@code TRANSL_EXCEPT_STRAND_CONFLICT} reports it rather than this fix silently
 * picking a winner.
 *
 * <p>The lookalike {@code anticodon} attribute is deliberately left alone: it carries an extra
 * {@code seq:} part that other EBI tooling recomputes from that same wrapper, so removing it there
 * would change what the value claims.
 */
@Slf4j
@Gff3Fix(
        name = "TRANSL_EXCEPT_COMPLEMENT",
        description = "Remove the redundant complement(...) wrapper from transl_except locations")
public class TranslExceptComplementFix implements Fix {

    // Deliberately matches only a plain "123" or "123..456" inside complement(...); anything more
    // exotic (join(...), <100, OTHER_ACC:100..102) is left untouched for validation to judge.
    // The named groups let the value be rebuilt as prefix + range + suffix, which removes the
    // wrapper while keeping the original spacing and casing byte for byte.
    private static final Pattern POS_COMPLEMENT_PATTERN = Pattern.compile(
            "^(?<prefix>\\s*\\(\\s*pos\\s*:\\s*)"
                    + "complement\\(\\s*(?<range>(?<start>\\d+)(?:\\s*\\.\\.\\s*(?<end>\\d+))?)\\s*\\)"
                    + "(?<suffix>\\s*,\\s*aa\\s*:\\s*[^\\s,)]+\\s*\\)\\s*)$",
            Pattern.CASE_INSENSITIVE);

    @FixMethod(
            rule = "TRANSL_EXCEPT_COMPLEMENT",
            description = "Strip complement(...) from transl_except locations on minus-strand features",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void fixAnnotation(GFF3Annotation annotation, int line) {

        Map<String, List<GFF3Feature>> featuresWithAttribute = annotation.getFeatures().stream()
                .filter(feature -> feature.hasAttribute(TRANSL_EXCEPT))
                .collect(Collectors.groupingBy(feature -> feature.getId().orElse(feature.hashCodeString())));

        featuresWithAttribute.values().forEach(featureGroup -> fixGroup(featureGroup, line));
    }

    /**
     * One feature can span several GFF3 rows sharing an {@code ID} (the fragments of a join), and
     * each row carries its own copy of the same attribute value. Decide once for the whole group so
     * every row ends up with identical text — {@code GFF3Mapper} keeps only the first row's
     * attributes when converting back to flat file, so a per-row decision would depend on row order.
     */
    private void fixGroup(List<GFF3Feature> fragments, int line) {

        List<String> distinctValues = fragments.stream()
                .flatMap(fragment -> fragment.getAttributeList(TRANSL_EXCEPT).orElse(List.of()).stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, String> rewrites = new LinkedHashMap<>();
        for (String value : distinctValues) {
            String stripped = stripIfRedundant(value, fragments);
            if (stripped == null) {
                continue;
            }
            rewrites.put(value, stripped);
            log.info(
                    "Fix: removed redundant complement() from {} on '{}' at line {}: '{}' -> '{}'",
                    TRANSL_EXCEPT,
                    fragments.get(0).accession(),
                    line,
                    value,
                    stripped);
        }

        fragments.forEach(fragment -> applyRewrites(fragment, rewrites));
    }

    /** @return the normalised value, or {@code null} to leave it untouched. */
    private String stripIfRedundant(String value, List<GFF3Feature> fragments) {

        Matcher matcher = POS_COMPLEMENT_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }

        long start;
        long end;
        try {
            start = Long.parseLong(matcher.group("start"));
            String endGroup = matcher.group("end");
            end = endGroup != null ? Long.parseLong(endGroup) : start;
        } catch (NumberFormatException e) {
            return null;
        }

        if (!isRedundantWithStrand(fragments, start, end)) {
            return null;
        }

        return matcher.group("prefix") + matcher.group("range") + matcher.group("suffix");
    }

    /**
     * Only the rows whose own start/end span the position get a say on the strand. Checking those
     * rather than the whole group matters because the rows of one feature may legitimately carry
     * different strands.
     */
    private boolean isRedundantWithStrand(List<GFF3Feature> fragments, long start, long end) {

        List<GFF3Feature> containing = fragments.stream()
                .filter(fragment -> start >= fragment.getStart() && end <= fragment.getEnd())
                .toList();

        return !containing.isEmpty() && containing.stream().allMatch(GFF3Feature::isComplement);
    }

    private void applyRewrites(GFF3Feature feature, Map<String, String> rewrites) {

        List<String> values = feature.getAttributeList(TRANSL_EXCEPT).orElse(List.of());
        if (values.stream().noneMatch(rewrites::containsKey)) {
            return;
        }

        List<String> updated = values.stream()
                .map(value -> rewrites.getOrDefault(value, value))
                .toList();

        feature.setAttributeList(TRANSL_EXCEPT, new ArrayList<>(updated));
    }
}
