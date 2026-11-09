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

import java.util.ArrayList;
import java.util.List;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Validation;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.TaxonProvider;

/**
 * INSDC annotation minimal spec v1.0, c.i.1: on a prokaryote sequence a feature may only be partial
 * where the sequence stops, at a gap or at the end of the sequence. Up to two bases may lie between
 * the two, for a codon the boundary cuts short.
 *
 * <p>{@code partial=start} marks the lower coordinate and {@code partial=end} the higher one on
 * either strand, so the check needs no strand logic. A gap is a run of N bases at least as long as
 * the minimum gap size, the same runs {@code GapGenerationFix} annotates as gaps.
 *
 * <p>Runs at LOW priority on the annotation, after {@code TranslationFix} has added or removed
 * partial markers, so it judges the partiality that reaches the output. Inert unless a
 * {@link TaxonProvider} resolves the organism to a prokaryote and a sequence source is registered.
 */
@Gff3Validation(
        name = "PROKARYOTE_PARTIAL_LOCATION",
        description = "Prokaryote features may only be partial next to a gap or the end of the sequence")
public class ProkaryotePartialLocationValidation implements Validation {

    public static final String PROKARYOTE_PARTIAL_LOCATION_RULE = "PROKARYOTE_PARTIAL_LOCATION";

    /** Bases allowed between a partial end and the gap or sequence end, for an incomplete codon. */
    private static final int INCOMPLETE_CODON_BASES = 2;

    /** The domains that open a prokaryote lineage, e.g. "Bacteria; Pseudomonadota; ...". */
    private static final List<String> PROKARYOTE_LINEAGE_DOMAINS = List.of("Bacteria", "Archaea");

    private static final String PARTIAL_LOCATION_MESSAGE =
            "Features of a prokaryote sequence can only be partial next to a gap or the end of the sequence:%s";

    private static final String VIOLATION_MESSAGE =
            "\nFeature %s on accession \"%s\" is partial at base %d, which is more than %d bases from a gap or the end"
                    + " of the sequence";

    @InjectContext
    private ValidationContext context;

    @ValidationMethod(
            rule = PROKARYOTE_PARTIAL_LOCATION_RULE,
            description = "A partial feature of a prokaryote sequence must abut a gap or the end of the sequence",
            type = ValidationType.ANNOTATION,
            priority = ValidationPriority.LOW)
    public void validatePartialLocation(GFF3Annotation annotation, int line) throws ValidationException {
        String accession = annotation.getAccession();
        if (!context.contains(SequenceLookup.class) || !isKnownProkaryote(accession)) {
            return;
        }
        SequenceLookup lookup = context.get(SequenceLookup.class);
        List<String> violations = new ArrayList<>();
        try {
            long sequenceLength = lookup.getSequenceLength(accession);
            for (GFF3Feature feature : annotation.getFeatures()) {
                List<String> partials =
                        feature.getAttributeList(GFF3Attributes.PARTIAL).orElse(List.of());
                if (partials.contains("start") && !startAbutsGapOrEnd(lookup, accession, feature.getStart())) {
                    violations.add(describe(feature, feature.getStart()));
                }
                if (partials.contains("end")
                        && !endAbutsGapOrEnd(lookup, accession, feature.getEnd(), sequenceLength)) {
                    violations.add(describe(feature, feature.getEnd()));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the length or gap regions of sequence " + accession, e);
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(
                    PROKARYOTE_PARTIAL_LOCATION_RULE,
                    line,
                    PARTIAL_LOCATION_MESSAGE.formatted(String.join("", violations)));
        }
    }

    private boolean startAbutsGapOrEnd(SequenceLookup lookup, String accession, long start) throws Exception {
        return start - 1 <= INCOMPLETE_CODON_BASES
                || hasGap(lookup, accession, start - 1 - INCOMPLETE_CODON_BASES, start - 1);
    }

    private boolean endAbutsGapOrEnd(SequenceLookup lookup, String accession, long end, long sequenceLength)
            throws Exception {
        return sequenceLength - end <= INCOMPLETE_CODON_BASES
                || hasGap(lookup, accession, end + 1, end + 1 + INCOMPLETE_CODON_BASES);
    }

    /** Whether an N-run of at least the minimum gap size overlaps [fromBase, toBase]. */
    private boolean hasGap(SequenceLookup lookup, String accession, long fromBase, long toBase) throws Exception {
        int minGapSize = minGapSize();
        return lookup.getGapRegions(accession, fromBase, toBase).stream()
                .anyMatch(gap -> gap.lengthBases() >= minGapSize);
    }

    private String describe(GFF3Feature feature, long base) {
        return VIOLATION_MESSAGE.formatted(feature.getName(), feature.accession(), base, INCOMPLETE_CODON_BASES);
    }

    /** Minimum gap size, from the context when available, otherwise the shared default. */
    private int minGapSize() {
        return context.contains(AnalysisContext.class)
                ? context.get(AnalysisContext.class).getMinGapSize()
                : AnalysisContextProvider.DEFAULT_MIN_GAP_SIZE;
    }

    /**
     * Whether the organism is known to be a prokaryote. An organism that does not resolve, including
     * when no {@link TaxonProvider} is registered or the lookup fails, is not ruled one.
     */
    private boolean isKnownProkaryote(String accession) {
        return context.contains(TaxonProvider.class)
                && context.get(TaxonProvider.class)
                        .resolve(accession)
                        .filter(taxon -> PROKARYOTE_LINEAGE_DOMAINS.stream().anyMatch(taxon::isChildOf))
                        .isPresent();
    }
}
