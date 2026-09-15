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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

public class MetricsCollectorTest {

    @TempDir
    Path tempDir;

    private static GFF3Feature feature(String seqId, String name) {
        return featureAt(seqId, name, 1L, 10L);
    }

    private static GFF3Feature featureAt(String seqId, String name, long start, long end) {
        return new GFF3Feature(
                Optional.empty(), Optional.empty(), seqId, Optional.empty(), "", name, start, end, "", "", "");
    }

    private static GFF3Annotation annotation(String accession, String... featureNames) {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion(accession, Optional.empty(), 1, 1000));
        for (String name : featureNames) {
            annotation.addFeature(feature(accession, name));
        }
        return annotation;
    }

    @Test
    public void emptyCollectorProducesZeroTotals() {
        Gff3Metrics metrics = new MetricsCollector().snapshot();

        assertEquals(new Gff3Metrics(0, List.of()), metrics);
    }

    @Test
    public void singleAnnotationCountsFeaturesSortedByName() {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("ACC1", "gene", "gene", "CDS"));

        Gff3Metrics metrics = collector.snapshot();

        assertEquals(
                new Gff3Metrics(
                        3,
                        List.of(new Gff3Metrics.AnnotationMetrics(
                                "ACC1",
                                1000,
                                3,
                                List.of(
                                        new Gff3Metrics.FeatureCount("CDS", 1, 10),
                                        new Gff3Metrics.FeatureCount("gene", 2, 10))))),
                metrics);
    }

    @Test
    public void chunksOfSameAccessionAreMerged() {
        MetricsCollector collector = new MetricsCollector();
        // The GFF3 reader validates one annotation per '###' chunk; metrics must merge them
        // back into one entry per accession.
        collector.record(annotation("ACC1", "gene"));
        collector.record(annotation("ACC1", "CDS"));

        Gff3Metrics metrics = collector.snapshot();

        assertEquals(2, metrics.totalFeatures());
        assertEquals(1000, metrics.annotations().get(0).sequenceBases());
        assertEquals(1, metrics.annotations().size());
        assertEquals(
                List.of(new Gff3Metrics.FeatureCount("CDS", 1, 10), new Gff3Metrics.FeatureCount("gene", 1, 10)),
                metrics.annotations().get(0).features());
    }

    @Test
    public void accessionsKeepFirstSeenOrder() {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("ACC2", "gene"));
        collector.record(annotation("ACC1", "CDS"));

        List<Gff3Metrics.AnnotationMetrics> annotations = collector.snapshot().annotations();

        assertEquals("ACC2", annotations.get(0).accession());
        assertEquals("ACC1", annotations.get(1).accession());
    }

    @Test
    public void annotationWithoutFeaturesRecordsEmptyEntry() {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("ACC1"));

        assertEquals(
                new Gff3Metrics(0, List.of(new Gff3Metrics.AnnotationMetrics("ACC1", 1000, 0, List.of()))),
                collector.snapshot());
    }

    @Test
    public void nullAnnotationIsIgnored() {
        MetricsCollector collector = new MetricsCollector();

        assertDoesNotThrow(() -> collector.record(null));

        assertEquals(new Gff3Metrics(0, List.of()), collector.snapshot());
    }

    @Test
    public void accessionsAreJsonEscaped() {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("we\"ird\\accession", "gene"));

        assertTrue(
                collector.snapshot().toJson().contains("we\\" + "\"ird\\\\accession"),
                collector.snapshot().toJson());
    }

    @Test
    public void snapshotIsImmutableWhileCollectorMovesOn() {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("ACC1", "gene"));

        Gff3Metrics first = collector.snapshot();
        // More data after the snapshot changes future snapshots, but never the earlier one.
        collector.record(annotation("ACC1", "CDS"));

        assertEquals(1, first.totalFeatures());
        assertEquals(
                List.of(new Gff3Metrics.FeatureCount("gene", 1, 10)),
                first.annotations().get(0).features());
        assertEquals(2, collector.snapshot().totalFeatures());
    }

    @Test
    public void writeJsonWritesFile() throws IOException {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("ACC1", "gene"));
        Path metricsFile = tempDir.resolve("metrics.json");

        collector.snapshot().writeJson(metricsFile);

        assertEquals(collector.snapshot().toJson(), Files.readString(metricsFile));
    }

    @Test
    public void basesMergeOverlappingAndDisjointSpans() {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("ACC1", "gene", "gene"));
        GFF3Annotation chunk = new GFF3Annotation();
        chunk.setSequenceRegion(new GFF3SequenceRegion("ACC1", Optional.empty(), 1, 1000));
        chunk.addFeature(featureAt("ACC1", "CDS", 1, 10));
        chunk.addFeature(featureAt("ACC1", "CDS", 20, 35));
        collector.record(chunk);

        Gff3Metrics.AnnotationMetrics acc1 = collector.snapshot().annotations().get(0);

        assertEquals(new Gff3Metrics.FeatureCount("CDS", 2, 26), acc1.features().get(0));
        // Same-type overlaps count once in 'bases' (see MetricsCollector javadoc).
        assertEquals(
                new Gff3Metrics.FeatureCount("gene", 2, 10), acc1.features().get(1));
    }

    @Test
    public void basesUnionMergesAcrossContiguousChunks() {
        MetricsCollector collector = new MetricsCollector();
        GFF3Annotation chunk = new GFF3Annotation();
        chunk.setSequenceRegion(new GFF3SequenceRegion("ACC1", Optional.empty(), 1, 1000));
        chunk.addFeature(featureAt("ACC1", "CDS", 1, 10));
        chunk.addFeature(featureAt("ACC1", "CDS", 20, 35));
        chunk.addFeature(featureAt("ACC1", "CDS", 30, 40));
        collector.record(chunk);

        Gff3Metrics.FeatureCount cds =
                collector.snapshot().annotations().get(0).features().get(0);

        // bases = union of [1..10] + [20..40] (the 30..35 overlap merges).
        assertEquals(new Gff3Metrics.FeatureCount("CDS", 3, 31), cds);
    }

    @Test
    public void basesIsStrandAgnostic() {
        MetricsCollector collector = new MetricsCollector();
        GFF3Annotation chunk = new GFF3Annotation();
        chunk.setSequenceRegion(new GFF3SequenceRegion("ACC1", Optional.empty(), 1, 1000));
        chunk.addFeature(featureAt("ACC1", "gene", 1, 100));
        chunk.addFeature(new GFF3Feature(
                Optional.empty(), Optional.empty(), "ACC1", Optional.empty(), "", "gene", 1L, 100L, "", "-", ""));
        collector.record(chunk);

        Gff3Metrics.FeatureCount gene =
                collector.snapshot().annotations().get(0).features().get(0);

        assertEquals(new Gff3Metrics.FeatureCount("gene", 2, 100), gene);
    }

    @Test
    public void sequenceBasesComesFromTheRegionDirective() {
        MetricsCollector collector = new MetricsCollector();
        GFF3Annotation chunk = new GFF3Annotation();
        chunk.setSequenceRegion(new GFF3SequenceRegion("ACC1", Optional.empty(), 500, 1500));
        chunk.addFeature(featureAt("ACC1", "CDS", 500, 1500));
        collector.record(chunk);

        Gff3Metrics.AnnotationMetrics acc1 = collector.snapshot().annotations().get(0);

        // Declared span 500..1500 -> 1001 bases, independent of feature spans.
        assertEquals(1001, acc1.sequenceBases());
        assertEquals(1001, acc1.features().get(0).bases());
    }

    @Test
    public void annotationWithoutRegionHasZeroSequenceBases() {
        MetricsCollector collector = new MetricsCollector();
        GFF3Annotation chunk = new GFF3Annotation();
        chunk.addFeature(featureAt("ACC1", "CDS", 1, 10));
        collector.record(chunk);

        Gff3Metrics.AnnotationMetrics acc1 = collector.snapshot().annotations().get(0);

        assertEquals("ACC1", acc1.accession());
        assertEquals(0, acc1.sequenceBases());
        assertEquals(10, acc1.features().get(0).bases());
    }

    @Test
    public void jsonShapeIsStable() {
        MetricsCollector collector = new MetricsCollector();
        collector.record(annotation("ACC1", "gene", "gene", "CDS"));

        assertEquals(
                """
                {
                  "totalFeatures" : 3,
                  "annotations" : [ {
                    "accession" : "ACC1",
                    "sequenceBases" : 1000,
                    "totalFeatures" : 3,
                    "features" : [ {
                      "name" : "CDS",
                      "count" : 1,
                      "bases" : 10
                    }, {
                      "name" : "gene",
                      "count" : 2,
                      "bases" : 10
                    } ]
                  } ]
                }""",
                collector.snapshot().toJson());
    }

    @Test
    public void engineValidateRecordsAnnotations() {
        MetricsCollector collector = new MetricsCollector();
        ValidationEngine engine = new ValidationEngineBuilder()
                .disableAutodetectContextProviders()
                .disableAutodetectValidationsAndFixes()
                .build();
        engine.setMetrics(collector);

        assertDoesNotThrow(() -> engine.validate(annotation("ACC1", "gene", "CDS"), 1));

        assertEquals(2, collector.snapshot().totalFeatures());
    }

    @Test
    public void engineWithoutMetricsDoesNotFail() {
        ValidationEngine engine = new ValidationEngineBuilder()
                .disableAutodetectContextProviders()
                .disableAutodetectValidationsAndFixes()
                .build();

        assertDoesNotThrow(() -> engine.validate(annotation("ACC1", "gene"), 1));
    }
}
