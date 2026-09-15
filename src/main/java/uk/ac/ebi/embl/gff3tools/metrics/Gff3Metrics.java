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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Immutable snapshot of the metrics gathered during one run, usable both programmatically (via
 * {@link MetricsCollector#snapshot()}) and as the serialized JSON report.
 *
 * <p>Ordering guarantees: annotations appear in first-seen accession order, features sorted by
 * name. Records are immutable, so a snapshot stays valid after the run that produced it.
 *
 * <p>Serialization uses Jackson: record component names become the JSON keys, so the schema is
 * {@code totalFeatures}, {@code annotations[].accession}, {@code annotations[].totalFeatures},
 * {@code annotations[].features[].name}, {@code annotations[].features[].count}.
 */
public record Gff3Metrics(long totalFeatures, List<AnnotationMetrics> annotations) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Gff3Metrics {
        annotations = List.copyOf(annotations);
    }

    /** Feature counts of one annotation (one entry per accession). */
    public record AnnotationMetrics(String accession, long totalFeatures, List<FeatureCount> features) {

        public AnnotationMetrics {
            features = List.copyOf(features);
        }
    }

    /** Count of a single feature type within one annotation. */
    public record FeatureCount(String name, long count) {}

    /** Renders the metrics as a pretty-printed JSON report. */
    public String toJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize metrics", e);
        }
    }

    /** Writes the JSON report to {@code path} using UTF-8. */
    public void writeJson(Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, this);
        }
    }
}
