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

import java.util.List;
import org.junit.jupiter.api.Test;

public class MetricsTextRendererTest {

    @Test
    public void emptyMetricsRenderTotalOnly() {
        assertEquals("total: 0 features\n", MetricsTextRenderer.render(new Gff3Metrics(0, List.of())));
    }

    @Test
    public void annotationsRenderOneLineEachWithCounts() {
        Gff3Metrics metrics = new Gff3Metrics(
                3,
                List.of(new Gff3Metrics.AnnotationMetrics(
                        "seq1",
                        3,
                        List.of(
                                new Gff3Metrics.FeatureCount("CDS", 1, 10, 10),
                                new Gff3Metrics.FeatureCount("gene", 2, 20, 20)))));

        assertEquals(
                """
                seq1: 3 features (CDS 1, 10 bases, unique 10; gene 2, 20 bases, unique 20)
                total: 3 features
                """,
                MetricsTextRenderer.render(metrics));
    }

    @Test
    public void annotationWithoutFeaturesOmitsCounts() {
        Gff3Metrics metrics = new Gff3Metrics(0, List.of(new Gff3Metrics.AnnotationMetrics("seq1", 0, List.of())));

        assertEquals(
                """
                seq1: 0 features
                total: 0 features
                """,
                MetricsTextRenderer.render(metrics));
    }

    @Test
    public void explicitFormatWinsOverDestination() {
        assertEquals(MetricsFormat.TEXT, MetricsFormat.resolve(MetricsFormat.TEXT, false));
        assertEquals(MetricsFormat.JSON, MetricsFormat.resolve(MetricsFormat.JSON, true));
    }

    @Test
    public void absentFormatFollowsDestination() {
        assertEquals(MetricsFormat.JSON, MetricsFormat.resolve(null, false));
        assertEquals(MetricsFormat.TEXT, MetricsFormat.resolve(null, true));
    }
}
