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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

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
 * <p>Base counts per feature type are the plain sum of feature span lengths
 * ({@link GFF3Feature#getLength()}). Overlapping features of the same type — for example mRNA
 * isoforms sharing exons — are therefore counted more than once; the metric is "bases spanned by
 * features of this type", not "unique bases covered". Keeping it a running sum avoids retaining
 * intervals, so the collector stays O(1) per feature.
 */
public class MetricsCollector {

    private final Map<String, TreeMap<String, TypeStats>> annotations = new LinkedHashMap<>();
    private long totalFeatures;

    /**
     * Records the feature counts of one annotation, merging into an existing entry when the
     * accession was already seen. Annotations without features and without a sequence region are
     * skipped: they carry nothing to count and no accession to file them under.
     */
    public void record(GFF3Annotation annotation) {
        if (annotation == null) {
            return;
        }
        String accession = accessionOf(annotation);
        if (accession == null) {
            return;
        }
        TreeMap<String, TypeStats> featureStats = annotations.computeIfAbsent(accession, a -> new TreeMap<>());
        for (GFF3Feature feature : annotation.getFeatures()) {
            TypeStats stats = featureStats.computeIfAbsent(feature.getName(), name -> new TypeStats());
            stats.count++;
            stats.bases += feature.getLength();
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
                totalFeatures,
                annotations.entrySet().stream()
                        .map(entry -> new Gff3Metrics.AnnotationMetrics(
                                entry.getKey(),
                                entry.getValue().values().stream()
                                        .mapToLong(stats -> stats.count)
                                        .sum(),
                                entry.getValue().entrySet().stream()
                                        .map(feature -> new Gff3Metrics.FeatureCount(
                                                feature.getKey(), feature.getValue().count, feature.getValue().bases))
                                        .toList()))
                        .toList());
    }

    /** Running count and base total for one feature type within one accession. */
    private static final class TypeStats {
        long count;
        long bases;
    }
}
