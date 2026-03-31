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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.SequenceSource;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

class TranslationFixTest {

    private TranslationFix fix;
    private SequenceLookup mockLookup;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        fix = new TranslationFix();
        mockLookup = mock(SequenceLookup.class);
        context = new ValidationContext();

        CompositeSequenceProvider compositeProvider = new CompositeSequenceProvider();
        compositeProvider.addSource(new SequenceSource() {
            @Override
            public boolean hasSequence(String seqId) {
                return true;
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase) throws Exception {
                return mockLookup.getSequenceSlice(seqId, fromBase, toBase);
            }
        });
        context.register(SequenceLookup.class, compositeProvider);

        ValidationRegistry.injectContext(fix, context);
    }

    @Test
    void skipsNonCdsFeatures() throws Exception {
        GFF3Annotation annotation = createAnnotation(createFeature("gene", "seq1", 1, 100, "+"));
        fix.fixAnnotation(annotation, 1);
        verifyNoInteractions(mockLookup);
    }

    @Test
    void skipsWhenNoProviderRegistered() throws Exception {
        TranslationFix fixWithoutProvider = new TranslationFix();
        ValidationContext emptyContext = new ValidationContext();
        ValidationRegistry.injectContext(fixWithoutProvider, emptyContext);

        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+"));
        fixWithoutProvider.fixAnnotation(annotation, 1);
    }

    @Test
    void skipsWhenNoSourcesRegistered() throws Exception {
        TranslationFix fixWithEmptyComposite = new TranslationFix();
        ValidationContext ctx = new ValidationContext();
        CompositeSequenceProvider emptyComposite = new CompositeSequenceProvider();
        ctx.register(SequenceLookup.class, emptyComposite);
        ValidationRegistry.injectContext(fixWithEmptyComposite, ctx);

        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+"));
        fixWithEmptyComposite.fixAnnotation(annotation, 1);
    }

    @Test
    void translatesCdsFeatureForwardStrand() throws Exception {
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("ATGAAATAA");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertTrue(feature.hasAttribute("translation"));
        assertEquals("MK", feature.getAttribute("translation").orElse(""));
    }

    @Test
    void translatesCdsFeatureComplementStrand() throws Exception {
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("TTATTTCAT");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "-");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertTrue(feature.hasAttribute("translation"));
        assertEquals("MK", feature.getAttribute("translation").orElse(""));
    }

    @Test
    void translatorSkipsNonTranslatingPseudoFeatures() throws Exception {
        // Pseudo features are handled by the Translator constructor (sets nonTranslating=true),
        // not by explicit logic in TranslationFix. This test verifies the end-to-end behavior.
        when(mockLookup.getSequenceSlice(any(), anyLong(), anyLong())).thenReturn("ATGAAATAA");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        feature.addAttribute("pseudo", "true");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertFalse(feature.hasAttribute("translation"));
    }

    @Test
    void translatesMultiSegmentCdsJoin() throws Exception {
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("ATGAAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCTAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);
        fix.fixAnnotation(annotation, 1);

        assertEquals("MKP", seg1.getAttribute("translation").orElse(""));
        assertEquals("MKP", seg2.getAttribute("translation").orElse(""));
    }

    @Test
    void translatesMultiSegmentCdsComplementJoin() throws Exception {
        // seg1="TTATTT" (1-6), seg2="CAT" (10-12) → concat "TTATTTCAT" (9 bases)
        // Rev comp = "ATGAAATAA" → ATG=M, AAA=K, TAA=* → "MK"
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("TTATTT");
        when(mockLookup.getSequenceSlice("seq1", 10L, 12L)).thenReturn("CAT");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 12, "-");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);
        fix.fixAnnotation(annotation, 1);

        assertEquals("MK", seg1.getAttribute("translation").orElse(""));
        assertEquals("MK", seg2.getAttribute("translation").orElse(""));
    }

    @Test
    void skipsExceptionFeatures() throws Exception {
        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        feature.addAttribute("exception", "ribosomal slippage");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertFalse(feature.hasAttribute("translation"));
        verifyNoInteractions(mockLookup);
    }

    @Test
    void skipsEntireJoinGroupWhenLaterSegmentHasException() throws Exception {
        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        seg2.addAttribute("exception", "ribosomal slippage");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);

        fix.fixAnnotation(annotation, 1);

        assertFalse(seg1.hasAttribute("translation"));
        assertFalse(seg2.hasAttribute("translation"));
        verifyNoInteractions(mockLookup);
    }

    @Test
    void propagatesPseudoToAllJoinSegments() throws Exception {
        when(mockLookup.getSequenceSlice(any(), anyLong(), anyLong())).thenReturn("ATGAAATAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 9, "+");
        seg1.addAttribute("pseudo", "true");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 20, 28, "+");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);
        fix.fixAnnotation(annotation, 1);

        assertTrue(seg1.hasAttribute("pseudo"));
        assertTrue(seg2.hasAttribute("pseudo"));
    }

    @Test
    void recordsOldAndNewTranslationInState() throws Exception {
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("ATGAAATAA");

        TranslationStateProvider stateProvider = new TranslationStateProvider();
        context.register(TranslationState.class, stateProvider);
        TranslationState state = context.get(TranslationState.class);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        feature.setAttributeList("translation", List.of("OLDVALUE"));
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        String key = TranslationState.buildKey("seq1", "CDS_id");
        TranslationState.TranslationEntry entry = state.get(key);
        assertNotNull(entry);
        assertEquals("OLDVALUE", entry.oldTranslation());
        assertEquals("MK", entry.newTranslation());
    }

    @Test
    void worksWithoutTranslationStateProvider() throws Exception {
        when(mockLookup.getSequenceSlice(any(), anyLong(), anyLong())).thenReturn("ATGAAATAA");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertTrue(feature.hasAttribute("translation"));
        assertEquals("MK", feature.getAttribute("translation").orElse(""));
    }

    @Test
    void recordsNullOldTranslationWhenNoPriorTranslation() throws Exception {
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("ATGAAATAA");

        TranslationStateProvider stateProvider = new TranslationStateProvider();
        context.register(TranslationState.class, stateProvider);
        TranslationState state = context.get(TranslationState.class);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        String key = TranslationState.buildKey("seq1", "CDS_id");
        TranslationState.TranslationEntry entry = state.get(key);
        assertNotNull(entry);
        assertNull(entry.oldTranslation());
        assertEquals("MK", entry.newTranslation());
    }

    private GFF3Annotation createAnnotation(GFF3Feature... features) {
        GFF3Annotation annotation = new GFF3Annotation();
        for (GFF3Feature feature : features) {
            annotation.addFeature(feature);
        }
        return annotation;
    }

    private GFF3Feature createFeature(String name, String seqId, long start, long end, String strand) {
        return createFeature(name, name + "_id", seqId, start, end, strand);
    }

    private GFF3Feature createFeature(
            String name, String featureId, String seqId, long start, long end, String strand) {
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
                strand,
                "0");
    }
}
