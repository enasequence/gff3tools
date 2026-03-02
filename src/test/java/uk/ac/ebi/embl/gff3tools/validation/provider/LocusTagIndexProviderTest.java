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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.validation.ProviderScope;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class LocusTagIndexProviderTest {

    private ValidationContext context;

    @BeforeEach
    void setUp() {
        context = new ValidationContext();
        context.register(OntologyClientProvider.class, new OntologyClientProvider());
        context.register(LocusTagIndexProvider.class, new LocusTagIndexProvider());
    }

    @Test
    @DisplayName("scope() returns ANNOTATION")
    void scope_returnsAnnotation() {
        LocusTagIndexProvider provider = new LocusTagIndexProvider();
        assertEquals(ProviderScope.ANNOTATION, provider.scope());
    }

    @Test
    @DisplayName("get() returns a non-null LocusTagIndex from the current annotation")
    void get_returnsNonNull() {
        GFF3Annotation annotation = new GFF3Annotation();
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));
        annotation.setFeatures(List.of(gene));
        context.setCurrentAnnotation(annotation);

        LocusTagIndex index = context.get(LocusTagIndexProvider.class);

        assertNotNull(index);
        assertEquals("LT_001", index.getGeneToLocusTag().get("geneA"));
    }

    @Test
    @DisplayName("get() returns the same cached instance on repeated calls")
    void get_returnsCachedInstance() {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setFeatures(List.of());
        context.setCurrentAnnotation(annotation);

        LocusTagIndex first = context.get(LocusTagIndexProvider.class);
        LocusTagIndex second = context.get(LocusTagIndexProvider.class);

        assertSame(first, second, "Repeated get() calls must return the same cached instance");
    }

    @Test
    @DisplayName("invalidate() clears cache -- next get() recomputes")
    void invalidate_clearsCacheAndRecomputes() {
        GFF3Annotation annotation1 = new GFF3Annotation();
        GFF3Feature gene1 = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));
        annotation1.setFeatures(List.of(gene1));
        context.setCurrentAnnotation(annotation1);

        LocusTagIndex before = context.get(LocusTagIndexProvider.class);

        // Switch annotation -- this triggers invalidate(ANNOTATION)
        GFF3Annotation annotation2 = new GFF3Annotation();
        GFF3Feature gene2 = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_002"), GFF3Attributes.GENE, List.of("geneB")));
        annotation2.setFeatures(List.of(gene2));
        context.setCurrentAnnotation(annotation2);

        LocusTagIndex after = context.get(LocusTagIndexProvider.class);

        assertNotSame(before, after, "Cache must be cleared after setCurrentAnnotation()");
        assertEquals("LT_002", after.getGeneToLocusTag().get("geneB"));
        assertNull(after.getGeneToLocusTag().get("geneA"), "Old annotation data should not be present");
    }

    @Test
    @DisplayName("Provider depends on OntologyClientProvider resolved via context")
    void get_resolvesOntologyClientDependency() {
        GFF3Annotation annotation = new GFF3Annotation();
        GFF3Feature gene = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.LOCUS_TAG, List.of("LT_001"), GFF3Attributes.GENE, List.of("geneA")));
        annotation.setFeatures(List.of(gene));
        context.setCurrentAnnotation(annotation);

        // Without OntologyClientProvider registered, this would throw
        ValidationContext contextWithout = new ValidationContext();
        contextWithout.register(LocusTagIndexProvider.class, new LocusTagIndexProvider());
        contextWithout.setCurrentAnnotation(annotation);

        assertThrows(IllegalArgumentException.class, () -> contextWithout.get(LocusTagIndexProvider.class));

        // With it registered, it succeeds
        LocusTagIndex index = context.get(LocusTagIndexProvider.class);
        assertNotNull(index);
    }
}
