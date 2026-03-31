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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

class TranslationComparisonValidationTest {

    private TranslationComparisonValidation validation;
    private ValidationContext context;
    private TranslationState state;

    @BeforeEach
    void setUp() {
        validation = new TranslationComparisonValidation();
        context = new ValidationContext();
        TranslationStateProvider provider = new TranslationStateProvider();
        context.register(TranslationState.class, provider);
        state = context.get(TranslationState.class);
        ValidationRegistry.injectContext(validation, context);
    }

    @Test
    void skipsNonCdsFeatures() throws Exception {
        GFF3Annotation annotation = createAnnotation(createFeature("gene", "seq1"));
        validation.validateTranslation(annotation, 1);
    }

    @Test
    void noOpWhenTranslationStateAbsent() throws Exception {
        TranslationComparisonValidation noStateValidation = new TranslationComparisonValidation();
        ValidationContext emptyContext = new ValidationContext();
        ValidationRegistry.injectContext(noStateValidation, emptyContext);

        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1"));
        noStateValidation.validateTranslation(annotation, 1);
    }

    @Test
    void noOpWhenNoOldTranslationRecorded() throws Exception {
        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1"));
        validation.validateTranslation(annotation, 1);
    }

    @Test
    void noOpWhenOldTranslationIsNull() throws Exception {
        String key = TranslationState.buildKey("seq1", "CDS_id");
        state.record(key, null, "MK");

        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1"));
        validation.validateTranslation(annotation, 1);
    }

    @Test
    void noWarningWhenTranslationsMatch() throws Exception {
        String key = TranslationState.buildKey("seq1", "CDS_id");
        state.record(key, "MK", "MK");

        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1"));
        validation.validateTranslation(annotation, 1);
    }

    @Test
    void warnsWhenTranslationsDiffer() {
        String key = TranslationState.buildKey("seq1", "CDS_id");
        state.record(key, "WRONG", "MK");

        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1"));
        ValidationException ex =
                assertThrows(ValidationException.class, () -> validation.validateTranslation(annotation, 1));
        assertTrue(ex.getMessage().contains("TRANSLATION_COMPARISON"));
        assertTrue(ex.getMessage().contains("CDS_id"));
        assertTrue(ex.getMessage().contains("seq1"));
    }

    @Test
    void usesLowestStartSegmentAsRepresentative() throws Exception {
        // Add segments in reverse order (higher start first) to verify sorting
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 100, 200);
        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 50);
        GFF3Annotation annotation = createAnnotation(seg2, seg1);

        // Key built from the lowest-start segment (seg1: seqId=seq1)
        String key = TranslationState.buildKey("seq1", "cds1");
        state.record(key, "OLD", "NEW");

        ValidationException ex =
                assertThrows(ValidationException.class, () -> validation.validateTranslation(annotation, 1));
        assertTrue(ex.getMessage().contains("TRANSLATION_COMPARISON"));
    }

    @Test
    void multipleCdsGroupsOneMismatchThrows() {
        // Two CDS groups: cds_a matches, cds_b mismatches
        GFF3Feature cdsA = createFeature(OntologyTerm.CDS.name(), "cds_a", "seq1", 1, 9);
        GFF3Feature cdsB = createFeature(OntologyTerm.CDS.name(), "cds_b", "seq1", 20, 30);
        GFF3Annotation annotation = createAnnotation(cdsA, cdsB);

        String keyA = TranslationState.buildKey("seq1", "cds_a");
        state.record(keyA, "MK", "MK"); // match

        String keyB = TranslationState.buildKey("seq1", "cds_b");
        state.record(keyB, "WRONG", "MK"); // mismatch

        ValidationException ex =
                assertThrows(ValidationException.class, () -> validation.validateTranslation(annotation, 1));
        assertTrue(ex.getMessage().contains("cds_b"));
    }

    @Test
    void multipleMismatchesAllReported() {
        // Both CDS groups mismatch — both should appear in the error message
        GFF3Feature cdsA = createFeature(OntologyTerm.CDS.name(), "cds_a", "seq1", 1, 9);
        GFF3Feature cdsB = createFeature(OntologyTerm.CDS.name(), "cds_b", "seq1", 20, 30);
        GFF3Annotation annotation = createAnnotation(cdsA, cdsB);

        String keyA = TranslationState.buildKey("seq1", "cds_a");
        state.record(keyA, "OLD_A", "NEW_A");

        String keyB = TranslationState.buildKey("seq1", "cds_b");
        state.record(keyB, "OLD_B", "NEW_B");

        ValidationException ex =
                assertThrows(ValidationException.class, () -> validation.validateTranslation(annotation, 1));
        assertTrue(ex.getMessage().contains("cds_a"), "Should report mismatch for cds_a");
        assertTrue(ex.getMessage().contains("cds_b"), "Should report mismatch for cds_b");
    }

    private GFF3Annotation createAnnotation(GFF3Feature... features) {
        GFF3Annotation annotation = new GFF3Annotation();
        for (GFF3Feature feature : features) {
            annotation.addFeature(feature);
        }
        return annotation;
    }

    private GFF3Feature createFeature(String name, String seqId) {
        return createFeature(name, name + "_id", seqId, 1, 9);
    }

    private GFF3Feature createFeature(String name, String featureId, String seqId, long start, long end) {
        return new GFF3Feature(
                Optional.of(featureId),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                name,
                start,
                end,
                ".",
                "+",
                "0");
    }
}
