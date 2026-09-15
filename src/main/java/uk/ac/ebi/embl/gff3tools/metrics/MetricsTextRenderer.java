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

import java.util.stream.Collectors;

/**
 * Human-readable rendering of a {@link Gff3Metrics} snapshot: one line per annotation, then a
 * total. Feature-name columns vary per file, so no table layout is attempted.
 *
 * <pre>
 * seq1: 812 features (CDS 310, 93000 bases; exon 382, 150000 bases; gene 120, 210000 bases)
 * seq2: 731 features (CDS 305, 88000 bases; exon 308, 148000 bases; gene 118, 205000 bases)
 * total: 1543 features
 * </pre>
 */
public final class MetricsTextRenderer {

    private MetricsTextRenderer() {}

    public static String render(Gff3Metrics metrics) {
        StringBuilder text = new StringBuilder();
        for (Gff3Metrics.AnnotationMetrics annotation : metrics.annotations()) {
            text.append("%s: %d features".formatted(annotation.accession(), annotation.totalFeatures()));
            if (!annotation.features().isEmpty()) {
                text.append(" (")
                        .append(annotation.features().stream()
                                .map(feature ->
                                        "%s %d, %d bases".formatted(feature.name(), feature.count(), feature.bases()))
                                .collect(Collectors.joining("; ")))
                        .append(")");
            }
            text.append("\n");
        }
        text.append("total: %d features\n".formatted(metrics.totalFeatures()));
        return text.toString();
    }
}
