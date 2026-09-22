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
package uk.ac.ebi.embl.gff3tools.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;

/**
 * Accumulates per-run metrics as plain counters, never retaining features, so it is safe to feed
 * from streaming pipelines. The {@link uk.ac.ebi.embl.gff3tools.validation.ValidationEngine} calls
 * {@link #record(GFF3Annotation)} once per validated annotation, which covers every flow that
 * reads or produces GFF3 annotations (validation, GFF3 to EMBL, EMBL/FASTA/TSV to GFF3).
 *
 * <p>The GFF3 reader emits one annotation per {@code ###} chunk or accession change; entries are
 * merged back here so the report carries one entry per accession, in first-seen order. Call
 * {@link #snapshot()} to obtain the immutable {@link Gff3Metrics} model, which can be consumed
 * programmatically or serialized to JSON.
 *
 * <p>Base counts per feature type: {@code bases} is the union of the feature spans with overlaps
 * merged — "how much of the sequence features of this type cover". It is strand-agnostic and
 * scoped to one type within one accession, so overlapping features of the same type (for example
 * mRNA isoforms sharing exons) count once, not once per feature.
 *
 * <p>Memory: the union requires retaining each feature's interval until {@link #snapshot()}
 * merges it — O(intervals of the run), a few MB per million features. Feature counts themselves
 * are still never retained.
 */
public class MetricsCollector {

    private final Map<String, AnnotationStats> annotations = new LinkedHashMap<>();

    // Version declared by the file header (##gff-version); null until seen.
    private String gff3Spec;
    private long totalFeatures;

    /**
     * Records the feature counts of one annotation, merging into an existing entry when the
     * accession was already seen. Annotations without features and without a sequence region are
     * skipped: they carry nothing to count and no accession to file them under.
     */
    /**
     * Records the GFF3 spec declared by the input's {@code ##gff-version} directive (the version
     * string as written, for example "3" or "3.1.26"). The first directive wins: a GFF3 file
     * declares exactly one version header. Runs that produce GFF3 without reading a version
     * directive (EMBL/FASTA/TSV input) carry no spec, and the report omits the field.
     */
    public void recordGff3Spec(String gff3Spec) {
        if (this.gff3Spec == null) {
            this.gff3Spec = gff3Spec;
        }
    }

    public void record(GFF3Annotation annotation) {
        if (annotation == null) {
            return;
        }
        String accession = accessionOf(annotation);
        if (accession == null) {
            return;
        }
        AnnotationStats entry = annotations.computeIfAbsent(accession, a -> new AnnotationStats());
        GFF3SequenceRegion region = annotation.getSequenceRegion();
        if (region != null) {
            entry.sequenceBases = Math.max(region.end() - region.start() + 1, 0);
        }
        TreeMap<String, TypeStats> featureStats = entry.featureStats;
        for (GFF3Feature feature : annotation.getFeatures()) {
            TypeStats stats = featureStats.computeIfAbsent(feature.getName(), name -> new TypeStats());
            stats.count++;
            if (feature.getLength() > 0) {
                stats.intervals.add(new long[] {feature.getStart(), feature.getEnd()});
            }
        }
        totalFeatures += annotation.getFeatures().size();
    }

    private String accessionOf(GFF3Annotation annotation) {
        if (annotation.getSequenceRegion() != null) {
            return annotation.getSequenceRegion().accession();
        }
        // Mirrors GFF3Annotation.getAccession(), but tolerates an empty annotation instead of
        // throwing: a feature-less, region-less annotation has nothing to count anyway.
        return annotation.getFeatures().isEmpty()
                ? null
                : annotation.getFeatures().get(0).accession();
    }

    /**
     * Builds the immutable metrics model from the counters accumulated so far. Feature counts are
     * sorted by name and annotations keep first-seen accession order.
     */
    public Gff3Metrics snapshot() {
        return new Gff3Metrics(
                gff3Spec,
                Gff3ToolsVersion.VERSION,
                totalFeatures,
                annotations.entrySet().stream()
                        .map(entry -> new Gff3Metrics.AnnotationMetrics(
                                entry.getKey(),
                                entry.getValue().sequenceBases,
                                entry.getValue().featureStats.values().stream()
                                        .mapToLong(stats -> stats.count)
                                        .sum(),
                                entry.getValue().featureStats.entrySet().stream()
                                        .map(feature -> new Gff3Metrics.FeatureCount(
                                                feature.getKey(),
                                                feature.getValue().count,
                                                unionOf(feature.getValue().intervals)))
                                        .toList()))
                        .toList());
    }

    /** Merges overlapping intervals and returns the total covered bases. */
    private static long unionOf(List<long[]> intervals) {
        if (intervals.isEmpty()) {
            return 0;
        }
        intervals.sort(Comparator.comparingLong(interval -> interval[0]));
        long union = 0;
        long curStart = 0;
        long curEnd = -1;
        boolean open = false;
        for (long[] interval : intervals) {
            if (!open) {
                curStart = interval[0];
                curEnd = interval[1];
                open = true;
            } else if (interval[0] <= curEnd) {
                curEnd = Math.max(curEnd, interval[1]);
            } else {
                union += curEnd - curStart + 1;
                curStart = interval[0];
                curEnd = interval[1];
            }
        }
        return union + (curEnd - curStart + 1);
    }

    /** Per-accession state: declared region span plus per-type feature stats. */
    private static final class AnnotationStats {
        long sequenceBases;
        final TreeMap<String, TypeStats> featureStats = new TreeMap<>();
    }

    /** Running count and intervals for one feature type within one accession. */
    private static final class TypeStats {
        long count;
        // Retained until snapshot(); {start, end} pairs of length > 0 only.
        final List<long[]> intervals = new ArrayList<>();
    }
}
