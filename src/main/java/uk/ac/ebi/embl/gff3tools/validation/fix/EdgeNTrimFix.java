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

import static uk.ac.ebi.embl.gff3tools.validation.meta.ValidationType.ANNOTATION;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;

/**
 * Remaps GFF3 coordinates to account for leading and trailing N bases that are conceptually
 * removed ("fake deleted") from the start and end of the sequence.
 *
 * <p>Given a sequence of length {@code L} with {@code leadingNs} N bases at the start and
 * {@code trailingNs} N bases at the end, the retained sequence spans original 1-based positions
 * {@code [leadingNs + 1 .. L - trailingNs]}. Every original position {@code p} in that range maps
 * to new position {@code p - leadingNs}, so the retained sequence is re-indexed to
 * {@code 1 .. (L - leadingNs - trailingNs)}.
 *
 * <p>For each feature {@code [start, end]} (1-based inclusive):
 * <ul>
 *   <li><b>Entirely within the trimmed Ns</b> ({@code end < firstKept} or {@code start > lastKept}):
 *       the feature sits on N bases only and is removed from the annotation.</li>
 *   <li><b>Overlapping a trimmed edge</b>: the in-N portion is clamped — a feature reaching into the
 *       leading Ns starts at the new sequence start (1); one reaching into the trailing Ns ends at
 *       the new sequence end.</li>
 *   <li><b>Fully inside the retained region</b>: simply shifted left by {@code leadingNs}.</li>
 * </ul>
 *
 * <p>The {@code ##sequence-region} directive ({@link GFF3SequenceRegion}) is rewritten to
 * {@code 1 .. newLength} so the declared region matches the re-indexed coordinates.
 *
 * <p>Runs at {@link ValidationPriority#CRITICAL} at the {@code ANNOTATION} level: it must execute
 * before any other fix or validation reasons about coordinates, and only an annotation-level fix
 * can both drop N-only features ({@link GFF3Annotation#removeFeature}) and rewrite the
 * sequence-region directive ({@link GFF3Annotation#setSequenceRegion}).
 *
 * <p>Note: the sequence itself is not modified here ("fake delete"); only GFF3 coordinates are
 * remapped. Any actual trimming of the underlying sequence bases is handled elsewhere.
 */
@Slf4j
@Gff3Fix(
        name = "EDGE_N_TRIM",
        description = "Remap feature and sequence-region coordinates for trimmed leading/trailing N bases")
public class EdgeNTrimFix implements Fix {

    static final String RULE = "EDGE_N_TRIM";

    @InjectContext
    private ValidationContext context;

    @FixMethod(
            rule = RULE,
            description = "Remap feature and sequence-region coordinates for trimmed leading/trailing N bases",
            type = ANNOTATION,
            priority = ValidationPriority.CRITICAL)
    public void fixAnnotation(GFF3Annotation annotation, int line) throws ValidationException {
        SequenceLookup sequenceLookup =
                context.contains(SequenceLookup.class) ? context.get(SequenceLookup.class) : null;
        if (sequenceLookup == null) {
            log.warn("Sequence lookup could not be found. Proceeding with original coordinates.");
            return;
        }

        if (annotation.getFeatures().isEmpty() && annotation.getSequenceRegion() == null) {
            return;
        }

        String accession = annotation.getAccession();
        SequenceStats stats;
        try {
            stats = sequenceLookup.getSequenceStats(accession);
        } catch (Exception e) {
            throw new IllegalStateException("Could not find sequence stats for accession " + accession, e);
        }

        long leadingNs = stats.leadingNsCount();
        long trailingNs = stats.trailingNsCount();
        long totalBases = stats.totalBases();

        if (leadingNs == 0 && trailingNs == 0) {
            // No edge Ns — coordinates are already correct.
            return;
        }

        long newLength = totalBases - leadingNs - trailingNs;
        if (newLength <= 0) {
            // TODO: whole-sequence-of-Ns (or all-edge) case — every feature would be dropped and the
            // TODO: should log error or throw idk. The n percentage validation looks only at edge Ns
            // sequence-region becomes empty. Left unhandled here; needs a product decision on whether
            // such an annotation should be discarded entirely.
            log.warn(
                    "Sequence '{}' is entirely edge N bases (length={}, leadingNs={}, trailingNs={}) — "
                            + "skipping edge-N trim",
                    accession,
                    totalBases,
                    leadingNs,
                    trailingNs);
            return;
        }

        long firstKept = leadingNs + 1; // original coordinate of the new base 1
        long lastKept = totalBases - trailingNs; // original coordinate of the new last base

        List<GFF3Feature> toRemove = new ArrayList<>();

        // Go through all regions and adjust
        for (GFF3Feature feature : new ArrayList<>(annotation.getFeatures())) {
            long start = feature.getStart();
            long end = feature.getEnd();

            if (end < firstKept || start > lastKept) {
                // Feature lies entirely on trimmed N bases.
                // TODO: a removed parent may orphan surviving children; parent/child graph cleanup
                // (feature.getChildren()/getParent()) is not handled here.
                toRemove.add(feature);
                continue;
            }

            // Clamp any in-N overhang to the retained region, then shift into the new coordinate space.
            long newStart = Math.max(start, firstKept) - leadingNs;
            long newEnd = Math.min(end, lastKept) - leadingNs;
            feature.setStart(newStart);
            feature.setEnd(newEnd);
        }

        // Clear out features declared over edge Ns
        for (GFF3Feature feature : toRemove) {
            annotation.removeFeature(feature);
        }

        // Adjust sequence region
        GFF3SequenceRegion region = annotation.getSequenceRegion();
        if (region != null) {
            annotation.setSequenceRegion(
                    new GFF3SequenceRegion(region.accessionId(), region.accessionVersion(), 1, newLength));
        }

        log.debug(
                "Edge-N trim on '{}': leadingNs={}, trailingNs={}, newLength={}, removed {} N-only feature(s)",
                accession,
                leadingNs,
                trailingNs,
                newLength,
                toRemove.size());
    }
}
