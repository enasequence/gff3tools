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
package uk.ac.ebi.embl.gff3tools.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;

/**
 * End-to-end integration tests verifying that the annotation lifecycle is
 * correctly wired: {@code context.setCurrentAnnotation()} is called before
 * validation at each annotation boundary, ANNOTATION-scoped providers are
 * invalidated between blocks, and the context always reflects the current
 * annotation being processed.
 */
class AnnotationLifecycleIntegrationTest {

    // ------------------------------------------------------------------
    // Helper: ANNOTATION-scoped provider that counts how many times it
    // has been invalidated (i.e. how many annotation boundaries occurred).
    // ------------------------------------------------------------------

    static class InvocationCountingProvider implements ContextProvider<Integer> {
        private Integer cached;
        private int computeCount = 0;
        private int invalidateCount = 0;

        @Override
        public Integer get(ValidationContext context) {
            if (cached == null) {
                computeCount++;
                cached = computeCount;
            }
            return cached;
        }

        @Override
        public void invalidate() {
            cached = null;
            invalidateCount++;
        }

        @Override
        public ProviderScope scope() {
            return ProviderScope.ANNOTATION;
        }

        int getInvalidateCount() {
            return invalidateCount;
        }

        int getComputeCount() {
            return computeCount;
        }
    }

    // ------------------------------------------------------------------
    // Helper: GLOBAL-scoped provider that tracks invalidation calls.
    // ------------------------------------------------------------------

    static class GlobalCountingProvider implements ContextProvider<String> {
        private String cached;
        private int invalidateCount = 0;

        @Override
        public String get(ValidationContext context) {
            if (cached == null) {
                cached = "global-value";
            }
            return cached;
        }

        @Override
        public void invalidate() {
            cached = null;
            invalidateCount++;
        }

        @Override
        public ProviderScope scope() {
            return ProviderScope.GLOBAL;
        }

        int getInvalidateCount() {
            return invalidateCount;
        }
    }

    // ------------------------------------------------------------------
    // Test: Resolution directive (###) triggers annotation lifecycle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GFF3FileReader sets currentAnnotation and invalidates ANNOTATION providers at ### boundaries")
    void readAnnotation_resolutionDirective_setsCurrentAnnotationAndInvalidatesProviders() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 500\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feat1\n"
                + "###\n"
                + "seq1\tsource\tgene\t200\t300\t.\t+\t.\tID=feat2\n"
                + "###\n"
                + "seq1\tsource\tgene\t400\t500\t.\t+\t.\tID=feat3\n";

        InvocationCountingProvider annotationProvider = new InvocationCountingProvider();
        GlobalCountingProvider globalProvider = new GlobalCountingProvider();

        ValidationEngine engine = new ValidationEngineBuilder()
                .withProvider(annotationProvider)
                .withProvider(globalProvider)
                .build();

        Path tempFile = Files.createTempFile("lifecycle-test", ".gff3");
        try {
            Files.writeString(tempFile, gff3Content, Charset.defaultCharset());
            try (GFF3FileReader reader = new GFF3FileReader(engine, new StringReader(gff3Content), tempFile)) {
                reader.readHeader();

                // Read first annotation (feat1) -- returned at first ###
                GFF3Annotation ann1 = reader.readAnnotation();
                assertNotNull(ann1);
                assertEquals(1, ann1.getFeatures().size());
                assertEquals("feat1", ann1.getFeatures().get(0).getId().get());
                // After readAnnotation returns, context should point to ann1
                assertSame(ann1, engine.getContext().getCurrentAnnotation());

                // Resolve the ANNOTATION-scoped provider to cache a value
                Integer val1 = engine.getContext().get(InvocationCountingProvider.class);
                assertEquals(1, val1);

                // Read second annotation (feat2) -- returned at second ###
                GFF3Annotation ann2 = reader.readAnnotation();
                assertNotNull(ann2);
                assertEquals(1, ann2.getFeatures().size());
                assertEquals("feat2", ann2.getFeatures().get(0).getId().get());
                assertSame(ann2, engine.getContext().getCurrentAnnotation());

                // The ANNOTATION provider should have been invalidated when ann2 was set
                // so resolving it now should produce a new compute
                Integer val2 = engine.getContext().get(InvocationCountingProvider.class);
                assertEquals(2, val2);

                // Read third annotation (feat3) -- returned at end-of-file
                GFF3Annotation ann3 = reader.readAnnotation();
                assertNotNull(ann3);
                assertEquals(1, ann3.getFeatures().size());
                assertEquals("feat3", ann3.getFeatures().get(0).getId().get());
                assertSame(ann3, engine.getContext().getCurrentAnnotation());

                Integer val3 = engine.getContext().get(InvocationCountingProvider.class);
                assertEquals(3, val3);

                // No more annotations
                assertNull(reader.readAnnotation());

                // Three annotation boundaries means at least 3 invalidation calls
                assertTrue(
                        annotationProvider.getInvalidateCount() >= 3,
                        "ANNOTATION provider should be invalidated at each annotation boundary, got: "
                                + annotationProvider.getInvalidateCount());

                // GLOBAL provider should never be invalidated by annotation transitions
                assertEquals(
                        0,
                        globalProvider.getInvalidateCount(),
                        "GLOBAL provider must not be invalidated at annotation boundaries");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ------------------------------------------------------------------
    // Test: Accession change triggers annotation lifecycle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GFF3FileReader sets currentAnnotation when accession changes between features")
    void readAnnotation_accessionChange_setsCurrentAnnotation() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 200\n"
                + "##sequence-region seq2 1 200\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feat1\n"
                + "seq2\tsource\tgene\t1\t100\t.\t+\t.\tID=feat2\n";

        InvocationCountingProvider annotationProvider = new InvocationCountingProvider();

        ValidationEngine engine =
                new ValidationEngineBuilder().withProvider(annotationProvider).build();

        Path tempFile = Files.createTempFile("lifecycle-accession-test", ".gff3");
        try {
            Files.writeString(tempFile, gff3Content, Charset.defaultCharset());
            try (GFF3FileReader reader = new GFF3FileReader(engine, new StringReader(gff3Content), tempFile)) {
                reader.readHeader();

                // First read returns seq1 annotation (triggered by accession change to seq2)
                GFF3Annotation ann1 = reader.readAnnotation();
                assertNotNull(ann1);
                assertEquals("seq1", ann1.getAccession());
                assertSame(ann1, engine.getContext().getCurrentAnnotation());

                // Cache a provider value
                engine.getContext().get(InvocationCountingProvider.class);

                // Second read returns seq2 annotation (triggered by end-of-file)
                GFF3Annotation ann2 = reader.readAnnotation();
                assertNotNull(ann2);
                assertEquals("seq2", ann2.getAccession());
                assertSame(ann2, engine.getContext().getCurrentAnnotation());

                // Provider should have been invalidated between annotations
                assertTrue(
                        annotationProvider.getInvalidateCount() >= 2,
                        "ANNOTATION provider should be invalidated at accession boundary");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ------------------------------------------------------------------
    // Test: Context tracks each annotation returned by the read() method
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GFF3FileReader.read() ensures context tracks annotations across the full read loop")
    void read_multiAnnotation_contextTracksEachAnnotation() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 500\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feat1\n"
                + "###\n"
                + "seq1\tsource\tgene\t200\t300\t.\t+\t.\tID=feat2\n";

        InvocationCountingProvider annotationProvider = new InvocationCountingProvider();
        ValidationEngine engine =
                new ValidationEngineBuilder().withProvider(annotationProvider).build();

        Path tempFile = Files.createTempFile("lifecycle-read-test", ".gff3");
        try {
            Files.writeString(tempFile, gff3Content, Charset.defaultCharset());
            try (GFF3FileReader reader = new GFF3FileReader(engine, new StringReader(gff3Content), tempFile)) {
                reader.readHeader();

                List<GFF3Annotation> annotations = new ArrayList<>();
                reader.read(annotations::add);

                // read() merges same-accession annotations, so we get 1 merged annotation
                // but the context should have been set for each underlying readAnnotation call
                assertEquals(1, annotations.size());

                // The ANNOTATION provider should have been invalidated at least twice
                // (once per readAnnotation call that returned a non-null annotation)
                assertTrue(
                        annotationProvider.getInvalidateCount() >= 2,
                        "ANNOTATION provider invalidation should happen for each annotation block");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ------------------------------------------------------------------
    // Test: Annotations without features also set context
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Annotations with only sequence-region (no features) also set context")
    void readAnnotation_noFeatures_setsCurrentAnnotation() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 500\n"
                + "##sequence-region seq2 1 200\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feat1\n";

        InvocationCountingProvider annotationProvider = new InvocationCountingProvider();
        ValidationEngine engine =
                new ValidationEngineBuilder().withProvider(annotationProvider).build();

        Path tempFile = Files.createTempFile("lifecycle-nofeat-test", ".gff3");
        try {
            Files.writeString(tempFile, gff3Content, Charset.defaultCharset());
            try (GFF3FileReader reader = new GFF3FileReader(engine, new StringReader(gff3Content), tempFile)) {
                reader.readHeader();

                // First call returns the annotation with features (seq1)
                GFF3Annotation ann1 = reader.readAnnotation();
                assertNotNull(ann1);
                assertEquals("seq1", ann1.getAccession());
                assertSame(ann1, engine.getContext().getCurrentAnnotation());

                // Second call returns the feature-less annotation (seq2)
                GFF3Annotation ann2 = reader.readAnnotation();
                assertNotNull(ann2);
                assertEquals("seq2", ann2.getAccession());
                assertSame(ann2, engine.getContext().getCurrentAnnotation());

                // No more annotations
                assertNull(reader.readAnnotation());
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ------------------------------------------------------------------
    // Test: Provider invalidation resets cached values between annotations
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ANNOTATION-scoped provider produces fresh values after each annotation boundary")
    void annotationScopedProvider_produceFreshValues_betweenAnnotations() throws Exception {
        String gff3Content = "##gff-version 3.2.1\n"
                + "##sequence-region seq1 1 500\n"
                + "seq1\tsource\tgene\t1\t100\t.\t+\t.\tID=feat1\n"
                + "###\n"
                + "seq1\tsource\tgene\t200\t300\t.\t+\t.\tID=feat2\n";

        InvocationCountingProvider annotationProvider = new InvocationCountingProvider();
        ValidationEngine engine =
                new ValidationEngineBuilder().withProvider(annotationProvider).build();

        Path tempFile = Files.createTempFile("lifecycle-fresh-test", ".gff3");
        try {
            Files.writeString(tempFile, gff3Content, Charset.defaultCharset());
            try (GFF3FileReader reader = new GFF3FileReader(engine, new StringReader(gff3Content), tempFile)) {
                reader.readHeader();

                // Read first annotation
                reader.readAnnotation();
                Integer firstValue = engine.getContext().get(InvocationCountingProvider.class);
                // Verify cached -- second call returns same instance
                Integer firstValueAgain = engine.getContext().get(InvocationCountingProvider.class);
                assertSame(firstValue, firstValueAgain, "Cached value should be the same instance");

                // Read second annotation -- triggers invalidation
                reader.readAnnotation();
                Integer secondValue = engine.getContext().get(InvocationCountingProvider.class);

                // The values should be different because the provider recomputed
                assertNotEquals(
                        firstValue,
                        secondValue,
                        "ANNOTATION-scoped provider should produce a fresh value after annotation boundary");

                assertEquals(2, annotationProvider.getComputeCount(), "Provider should have computed exactly twice");
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
