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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContextProvider;

/**
 * Adds a {@code gap} feature for every run of N bases that is at least {@code minGapLength} long
 * and is not already covered by the annotation's existing gap features.
 *
 * <p>Existing gap features are never modified or removed. A run is skipped only when the
 * <em>union</em> of the existing gap intervals covers every one of its bases, so a submitter who
 * legitimately partitions one N-run into adjacent gaps of different {@code gap_type} gets nothing
 * added. Anything less than full coverage produces a single generated gap spanning the
 * <em>entire</em> run — not the uncovered fragments — because an N-run is one physical gap event
 * and splitting it would assert two abutting assembly gaps while leaving sub-threshold remainders
 * unannotated. Where the run was only partially covered the generated feature therefore overlaps
 * the submitter's, and a warning naming the uncovered bases is logged. This matches sequencetools'
 * {@code SequenceToGapFeatureBasesFix}, which likewise emits a full-run gap when no existing
 * feature matches.
 *
 * <p>Because generated and submitted features can overlap, summing {@code estimated_length} across
 * an annotation's gap features over-counts bases in that case; gap-base totals must come from the
 * sequence or from merged intervals. The warning is what makes such annotations findable.
 *
 * <p>Runs at {@link ValidationPriority#HIGH} so the feature list is settled before the NORMAL-tier
 * annotation validations see it. The only runtime gate is whether a {@link SequenceLookup} is
 * available: without one this fix is a complete no-op.
 *
 * <p>Generated features are <em>appended</em>. That is not cosmetic. {@code GeneFeatureValidation}
 * and {@code LocusTagAssociationFix} leave their feature loops with {@code return} rather than
 * {@code continue} on the first feature lacking {@code gene} / {@code locus_tag}, so a gap placed
 * anywhere but the end would silently switch those rules off for the whole annotation. Appending
 * leaves their behaviour unchanged, because the loop already stops at the first gene-less feature
 * the submitter supplied. Do not reorder these into coordinate position without fixing that first.
 */
@Slf4j
@Gff3Fix(
        name = "GAP_GENERATION",
        description = "Add gap features for runs of N bases that no existing gap feature covers")
public class GapGenerationFix implements Fix {

    @InjectContext
    private ValidationContext context;

    /**
     * Document-wide counter for generated gap IDs. GFF3 requires IDs to be unique within a file and
     * a single fix instance serves the whole run, so this is instance state rather than a local:
     * the sequence continues across annotations. Mirrors the counter {@code FastaToGff3Converter}
     * used before gap generation moved here, so emitted IDs are unchanged.
     */
    private int gapCounter = 0;

    @FixMethod(
            rule = "GAP_GENERATION",
            description = "Add gap features for runs of N bases that no existing gap feature covers",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void fix(GFF3Annotation annotation, int line) {
        if (!context.contains(SequenceLookup.class)) {
            return;
        }
        SequenceLookup lookup = context.get(SequenceLookup.class);
        if (lookup == null) {
            return;
        }

        String accession = annotation.getAccession();

        // No knownSeqIds() pre-check: a keyless plain-sequence source reports no known seqIds while
        // still matching any accession (FileSequenceSource#hasSequence, which is what
        // CompositeSequenceProvider resolves against). Let the lookup itself be the membership test.
        List<GapRegion> runs;
        try {
            // Whole-sequence by definition on this API: leading and trailing N-runs are
            // ordinary runs, matching GapFeatureBasesValidation and sequencetools.
            runs = lookup.getGapRegions(accession);
        } catch (Exception e) {
            // A missing or unreadable sequence is to be raised as a validation issue beforehand by {@link
            // SequenceMappingValidation}
            log.warn("Unable to read gap regions for accession {}: {}", accession, e.getMessage());
            return;
        }
        if (runs == null || runs.isEmpty()) {
            return;
        }

        List<GapRegion> covered = existingGapIntervals(annotation);
        Set<String> usedIds = existingIds(annotation);
        int minGapLength = minGapLength();

        for (GapRegion run : runs) {
            if (run.lengthBases() < minGapLength) {
                continue;
            }

            List<GapRegion> uncovered = subtract(run, covered);
            if (uncovered.isEmpty()) {
                // Already fully covered by the submitter's own gap features.
                continue;
            }
            if (!isWholeRun(uncovered, run)) {
                log.warn(
                        "N-run {}-{} on {} is only partially covered by existing gap features"
                                + " (uncovered: {}); adding a gap spanning the full run",
                        run.startBase,
                        run.endBase,
                        accession,
                        format(uncovered));
            }

            annotation.addFeature(buildGapFeature(annotation, run, usedIds));
            log.info("Adding gap feature {}-{} on {}", run.startBase, run.endBase, accession);
        }
    }

    /** Minimum run length, from the context when available, otherwise the shared default of 10. */
    private int minGapLength() {
        AnalysisContext analysisContext = analysisContext();
        return analysisContext != null ? analysisContext.getMinGapSize() : AnalysisContextProvider.DEFAULT_MIN_GAP_SIZE;
    }

    private AnalysisContext analysisContext() {
        return context.contains(AnalysisContext.class) ? context.get(AnalysisContext.class) : null;
    }

    /**
     * Intervals of the annotation's existing gap features, ascending by start. Both {@code gap} and
     * {@code assembly_gap} resolve to SO:0000730, so both count as coverage.
     */
    private List<GapRegion> existingGapIntervals(GFF3Annotation annotation) {
        OntologyClient ontologyClient = context.get(OntologyClient.class);
        return annotation.getFeatures().stream()
                .filter(feature -> {
                    Optional<String> soId = ontologyClient.findTermByNameOrSynonym(feature.getName());
                    return soId.isPresent() && OntologyTerm.GAP.ID.equals(soId.get());
                })
                // A feature with end < start covers nothing, and GapRegion's constructor rejects
                // such a range outright - drop it here rather than letting it abort the run.
                .filter(feature -> feature.getEnd() >= feature.getStart())
                .map(feature -> new GapRegion(feature.getStart(), feature.getEnd()))
                .sorted(Comparator.comparingLong(interval -> interval.startBase))
                .collect(Collectors.toList());
    }

    private Set<String> existingIds(GFF3Annotation annotation) {
        Set<String> ids = new HashSet<>();
        for (GFF3Feature feature : annotation.getFeatures()) {
            feature.getId().ifPresent(ids::add);
        }
        return ids;
    }

    /**
     * The uncovered sub-ranges of {@code run}, ascending and disjoint. All coordinates are 1-based
     * inclusive.
     *
     * <p>The result is used as the coverage predicate (empty means fully covered) and to name the
     * uncovered bases in the partial-coverage warning. It deliberately does <em>not</em> define the
     * emitted feature, which always spans the whole run.
     *
     * @param covered existing gap intervals sorted ascending by startBase; they may overlap each
     *                other, nest, and extend beyond either end of {@code run}
     */
    static List<GapRegion> subtract(GapRegion run, List<GapRegion> covered) {
        List<GapRegion> out = new ArrayList<>();
        long cursor = run.startBase;
        for (GapRegion interval : covered) {
            // Defensive: GapRegion's constructor rejects end < start, but its fields are public and
            // mutable, so an inverted interval can still reach here. It covers nothing.
            if (interval.endBase < interval.startBase) continue;
            if (interval.endBase < run.startBase) continue; // entirely before the run
            if (interval.startBase > run.endBase) break; // entirely after; so is everything left
            long start = Math.max(interval.startBase, run.startBase); // clip to the run
            long end = Math.min(interval.endBase, run.endBase);
            if (start > cursor) {
                out.add(new GapRegion(cursor, start - 1));
            }
            // Never moves backwards, so overlapping and nested intervals collapse without an
            // explicit merge pass, and adjacent ones produce no zero-length segment.
            cursor = Math.max(cursor, end + 1);
            if (cursor > run.endBase) {
                return out;
            }
        }
        if (cursor <= run.endBase) {
            out.add(new GapRegion(cursor, run.endBase));
        }
        return out;
    }

    private static boolean isWholeRun(List<GapRegion> uncovered, GapRegion run) {
        return uncovered.size() == 1
                && uncovered.get(0).startBase == run.startBase
                && uncovered.get(0).endBase == run.endBase;
    }

    private static String format(List<GapRegion> intervals) {
        return intervals.stream()
                .map(interval -> interval.startBase + "-" + interval.endBase)
                .collect(Collectors.joining(", "));
    }

    private GFF3Feature buildGapFeature(GFF3Annotation annotation, GapRegion run, Set<String> usedIds) {
        String id = nextId(usedIds);
        // Mirror the annotation's own seqId and version so the generated feature's accession()
        // matches the sequence-region directive and the accession the gap regions were read under.
        GFF3SequenceRegion region = annotation.getSequenceRegion();
        String seqId = region != null
                ? region.accessionId()
                : annotation.getFeatures().get(0).getSeqId();
        Optional<Integer> seqIdVersion = region != null
                ? region.accessionVersion()
                : annotation.getFeatures().get(0).getSeqIdVersion();

        GFF3Feature feature = new GFF3Feature(
                Optional.of(id),
                Optional.empty(),
                seqId,
                seqIdVersion,
                ".",
                "gap",
                run.startBase,
                run.endBase,
                ".",
                "+",
                ".");
        feature.addAttribute(GFF3Attributes.ATTRIBUTE_ID, id);
        feature.addAttribute(GFF3Attributes.ESTIMATED_LENGTH, String.valueOf(run.lengthBases()));

        // gap_type is not inferable from a run of Ns. It is emitted only when the caller asserted
        // one, in which case the value has already been validated by AnalysisContext's constructor
        // - AssemblyGapValidation never sees features added here.
        AnalysisContext analysisContext = analysisContext();
        if (analysisContext != null) {
            if (analysisContext.getGapType() != null) {
                feature.addAttribute(GFF3Attributes.GAP_TYPE, analysisContext.getGapType());
            }
            if (analysisContext.getLinkageEvidence() != null) {
                feature.addAttribute(GFF3Attributes.LINKAGE_EVIDENCE, analysisContext.getLinkageEvidence());
            }
        }
        return feature;
    }

    /**
     * {@code gap}, then {@code gap_1}, {@code gap_2}, ... skipping any ID the annotation already
     * uses so a submitter's own {@code ID=gap} is never shadowed.
     */
    private String nextId(Set<String> usedIds) {
        String id;
        do {
            id = gapCounter == 0 ? "gap" : "gap_" + gapCounter;
            gapCounter++;
        } while (usedIds.contains(id));
        usedIds.add(id);
        return id;
    }
}
