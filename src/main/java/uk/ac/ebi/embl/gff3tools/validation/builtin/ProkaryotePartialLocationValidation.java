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
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ExitMethod;
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
 * INSDC annotation minimal spec v1.0, c.i.1, for prokaryotes (bacteria and archaea).
 *
 * <p>A feature marked {@code partial} carries on past the sequence we have, so its partial end must
 * be near where the sequence stops: the end of the sequence, or a gap (a run of unknown "N" bases).
 * Near means at most {@value #MAX_BASES_TO_GAP_OR_END} bases in between.
 *
 * <p>{@code partial=start} is the feature's lower coordinate and {@code partial=end} its higher one,
 * on either strand.
 *
 * <p>Runs after {@code TranslationFix}, which can add or remove partial markers. Does nothing unless
 * a {@link TaxonProvider} identifies the organism and the sequence is available.
 */
@Gff3Validation(
        name = "PROKARYOTE_PARTIAL_LOCATION",
        description = "Prokaryote features may only be partial near a gap or the end of the sequence")
public class ProkaryotePartialLocationValidation implements Validation {

    public static final String PROKARYOTE_PARTIAL_LOCATION_RULE = "PROKARYOTE_PARTIAL_LOCATION";

    /** Genes are read in 3-base codons, so up to 2 bases of a cut-off codon may remain. */
    private static final int MAX_BASES_TO_GAP_OR_END = 2;

    /** The top-level taxonomy groups that make up the prokaryotes. */
    private static final List<String> PROKARYOTE_DOMAINS = List.of("Bacteria", "Archaea");

    private static final String PARTIAL_LOCATION_MESSAGE =
            "Features of a prokaryote sequence can only be partial near a gap or the end of the sequence:%s";

    private static final String VIOLATION_MESSAGE =
            "\nFeature %s on accession \"%s\" is partial at base %d, which is more than %d bases from a gap or the end"
                    + " of the sequence";

    @InjectContext
    private ValidationContext context;

    /** The same for the whole file, so read once and reset by {@link #clear()}. */
    private Integer minGapSize;

    @ValidationMethod(
            rule = PROKARYOTE_PARTIAL_LOCATION_RULE,
            description = "A partial feature of a prokaryote sequence must end near a gap or the end of the sequence",
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
                if (partials.contains("start") && !startIsNearGapOrEnd(lookup, accession, feature.getStart())) {
                    violations.add(violation(feature, feature.getStart()));
                }
                if (partials.contains("end")
                        && !endIsNearGapOrEnd(lookup, accession, feature.getEnd(), sequenceLength)) {
                    violations.add(violation(feature, feature.getEnd()));
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

    @ExitMethod
    public void clear() {
        minGapSize = null;
    }

    private boolean startIsNearGapOrEnd(SequenceLookup lookup, String accession, long start) throws Exception {
        long basesBefore = start - 1;
        return basesBefore <= MAX_BASES_TO_GAP_OR_END
                || hasGap(lookup, accession, start - MAX_BASES_TO_GAP_OR_END - 1, start - 1);
    }

    private boolean endIsNearGapOrEnd(SequenceLookup lookup, String accession, long end, long sequenceLength)
            throws Exception {
        long basesAfter = sequenceLength - end;
        return basesAfter <= MAX_BASES_TO_GAP_OR_END
                || hasGap(lookup, accession, end + 1, end + MAX_BASES_TO_GAP_OR_END + 1);
    }

    /** Whether a gap touches bases fromBase to toBase. Runs of N shorter than the minimum gap size are not gaps. */
    private boolean hasGap(SequenceLookup lookup, String accession, long fromBase, long toBase) throws Exception {
        return lookup.getGapRegions(accession, fromBase, toBase).stream()
                .anyMatch(nRun -> nRun.lengthBases() >= minGapSize());
    }

    private int minGapSize() {
        if (minGapSize == null) {
            minGapSize = context.contains(AnalysisContext.class)
                    ? context.get(AnalysisContext.class).getMinGapSize()
                    : AnalysisContextProvider.DEFAULT_MIN_GAP_SIZE;
        }
        return minGapSize;
    }

    /** An organism that can't be identified is not treated as a prokaryote. */
    private boolean isKnownProkaryote(String accession) {
        return ValidationUtils.resolveTaxon(accession, context)
                .filter(taxon -> PROKARYOTE_DOMAINS.stream().anyMatch(taxon::isChildOf))
                .isPresent();
    }

    private String violation(GFF3Feature feature, long base) {
        return VIOLATION_MESSAGE.formatted(feature.getName(), feature.accession(), base, MAX_BASES_TO_GAP_OR_END);
    }
}
