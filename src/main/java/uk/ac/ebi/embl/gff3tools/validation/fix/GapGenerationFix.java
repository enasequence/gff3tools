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
import uk.ac.ebi.embl.gff3tools.utils.ValidationUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ExitMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.FixMethod;
import uk.ac.ebi.embl.gff3tools.validation.meta.Gff3Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.InjectContext;
import uk.ac.ebi.embl.gff3tools.validation.meta.ValidationPriority;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContextProvider;

/**
 * Adds a {@code gap} feature for every run of N bases at least {@code minGapLength} long that the
 * annotation's existing gap features do not already cover.
 *
 * Coverage is judged against the union of existing gaps, so a run split into adjacent submitter gaps is left alone,
 * while a partly covered N run still gets one gap spanning the whole run.
 * This behaviour matches previous {@code sequencetools} behaviour, so flatfile <-> gff3 conversions should be smooth.
 */
@Slf4j
@Gff3Fix(
        name = "GAP_GENERATION",
        description = "Add gap features for runs of N bases that no existing gap feature covers")
public class GapGenerationFix implements Fix {

    @InjectContext
    private ValidationContext context;

    /**
     * Document-wide counter: GFF3 gap IDs must be unique within a file and one instance serves the whole
     * run, so it keeps counting across annotations. Mirrors the counter {@code FastaToGff3Converter}
     * used before generation moved here, so emitted IDs are unchanged.
     */
    private int gapCounter = 0;

    @FixMethod(
            rule = "GAP_GENERATION",
            description = "Add gap features for runs of N bases that no existing gap feature covers",
            type = ANNOTATION,
            priority = ValidationPriority.HIGH)
    public void fix(GFF3Annotation annotation, int line) {
        // Checked before getAccession(), which throws for an annotation with neither features nor a
        // sequence region.
        if (!hasSequenceLookup()) {
            return;
        }

        String accession = annotation.getAccession();
        List<GapRegion> runs = resolveGapRegions(accession);
        if (runs.isEmpty()) {
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
            // The generated gap overlaps the submitter's here, so estimated_length no longer sums to
            // the sequence's gap bases. The warning is what makes these annotations findable.
            if (!isWholeRun(uncovered, run)) {
                log.warn(
                        "N-run {}-{} on {} is only partially covered by existing gap features"
                                + " (uncovered: {}); adding a gap spanning the full run",
                        run.startBase,
                        run.endBase,
                        accession,
                        format(uncovered));
            }

            // Append, do not insert by coordinate: GeneFeatureValidation and LocusTagAssociationFix
            // leave their loops with return on the first feature lacking gene/locus_tag, so a gap
            // earlier in the list would silently disable them for the whole annotation.
            annotation.addFeature(buildGapFeature(annotation, run, usedIds));
            log.info("Adding gap feature {}-{} on {}", run.startBase, run.endBase, accession);
        }
    }

    private boolean hasSequenceLookup() {
        return context.contains(SequenceLookup.class) && context.get(SequenceLookup.class) != null;
    }

    /**
     * The N-runs of {@code accession}, never {@code null} and empty at worst. Shaped after
     * {@link ValidationUtils#resolveSequenceLength}, except that an unresolvable sequence returns
     * empty rather than throwing: {@code SequenceMappingValidation} already reports it at
     * {@code CRITICAL}, and a {@code RuntimeException} out of a fix aborts the run. There is no
     * {@code knownSeqIds()} pre-check because a keyless plain-sequence source reports none while
     * still matching any accession.
     */
    private List<GapRegion> resolveGapRegions(String accession) {
        if (!context.contains(SequenceLookup.class)) {
            return List.of();
        }
        SequenceLookup lookup = context.get(SequenceLookup.class);
        if (lookup == null) {
            return List.of();
        }
        try {
            // Whole sequence, so leading and trailing N-runs count like any other.
            List<GapRegion> runs = lookup.getGapRegions(accession);
            return runs != null ? runs : List.of();
        } catch (Exception e) {
            // Already reported as SEQUENCE_MAPPING; a trace is all that is useful here.
            log.debug("No gap regions resolvable for accession {}: {}", accession, e.getMessage());
            return List.of();
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
                // Covers nothing, and GapRegion's constructor rejects such a range outright.
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
     * The uncovered sub-ranges of {@code run}, ascending and disjoint, 1-based inclusive. Used as the
     * coverage predicate and to name the uncovered bases in the warning; it does not define the
     * emitted feature, which always spans the whole run. {@code covered} must be sorted ascending by
     * startBase, and its intervals may overlap, nest and extend past either end of {@code run}.
     */
    static List<GapRegion> subtract(GapRegion run, List<GapRegion> covered) {
        List<GapRegion> out = new ArrayList<>();
        long cursor = run.startBase;
        for (GapRegion interval : covered) {
            // Defensive: GapRegion's fields are public and mutable, so an inverted interval can
            // still reach here.
            if (interval.endBase < interval.startBase) continue;
            if (interval.endBase < run.startBase) continue; // entirely before the run
            if (interval.startBase > run.endBase) break; // entirely after; so is everything left
            long start = Math.max(interval.startBase, run.startBase); // clip to the run
            long end = Math.min(interval.endBase, run.endBase);
            if (start > cursor) {
                out.add(new GapRegion(cursor, start - 1));
            }
            // Never moves backwards, so overlapping and nested intervals collapse without a merge
            // pass and adjacent ones leave no zero-length segment.
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
        // Mirror the annotation's seqId and version so the feature's accession() matches the
        // sequence-region directive.
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

        // gap_type is not inferable from a run of Ns, so it is emitted only when the caller asserted
        // one. AnalysisContext's constructor validates those values, because AssemblyGapValidation
        // never sees features added by a fix.
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

    /**
     * Resets the per-file state. IDs only have to be unique within one GFF3 file, so a reused engine
     * must start each file at {@code ID=gap} again rather than carrying the previous file's count.
     */
    @ExitMethod
    public void clear() {
        gapCounter = 0;
    }
}
