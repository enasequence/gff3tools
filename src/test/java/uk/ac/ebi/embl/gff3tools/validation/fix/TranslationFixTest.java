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

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceProvider;

class TranslationFixTest {

    private TranslationFix fix;
    private SequenceReader mockReader;
    private ValidationContext context;

    @BeforeEach
    void setUp() {
        fix = new TranslationFix();
        mockReader = mock(SequenceReader.class);
        when(mockReader.submissionType()).thenReturn(SubmissionType.FASTA);
        when(mockReader.getOrderedIds(any())).thenReturn(java.util.List.of("seq1"));
        context = new ValidationContext();

        CompositeSequenceProvider compositeProvider = new CompositeSequenceProvider();
        FileSequenceProvider fileProvider = new FileSequenceProvider(mockReader);
        compositeProvider.addSource(fileProvider);
        context.register(SequenceReader.class, compositeProvider);

        ValidationRegistry.injectContext(fix, context);
    }

    @Test
    void skipsNonCdsFeatures() throws Exception {
        GFF3Annotation annotation = createAnnotation(createFeature("gene", "seq1", 1, 100, "+"));
        fix.fixAnnotation(annotation, 1);
        verifyNoInteractions(mockReader);
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
        ctx.register(SequenceReader.class, emptyComposite);
        ValidationRegistry.injectContext(fixWithEmptyComposite, ctx);

        GFF3Annotation annotation = createAnnotation(createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+"));
        fixWithEmptyComposite.fixAnnotation(annotation, 1);
    }

    @Test
    void translatesCdsFeatureForwardStrand() throws Exception {
        // ATG = M (start), AAA = K, TAA = * (stop) → conceptual = "MK"
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(1L), eq(9L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn("ATGAAATAA");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertTrue(feature.hasAttribute("translation"));
        assertEquals("MK", feature.getAttribute("translation").orElse(""));
    }

    @Test
    void translatesCdsFeatureComplementStrand() throws Exception {
        // Complement of TTA TTT CAT → ATG AAA TAA → M K *
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(1L), eq(9L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn("TTATTTCAT");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "-");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertTrue(feature.hasAttribute("translation"));
        assertEquals("MK", feature.getAttribute("translation").orElse(""));
    }

    @Test
    void skipsPseudoFeatures() throws Exception {
        when(mockReader.getSequenceSlice(any(), any(), anyLong(), anyLong(), any()))
                .thenReturn("ATGAAATAA");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        feature.addAttribute("pseudo", "true");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertFalse(feature.hasAttribute("translation"));
    }

    @Test
    void translatesMultiSegmentCdsJoin() throws Exception {
        // Two segments: positions 1-6 and 10-15
        // Concatenated: ATGAAA + CCCTAA = ATGAAACCCTAA
        // ATG=M, AAA=K, CCC=P, TAA=* → conceptual = "MKP"
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(1L), eq(6L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn("ATGAAA");
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(10L), eq(15L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn("CCCTAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);
        fix.fixAnnotation(annotation, 1);

        // Both segments should have the same translation
        assertEquals("MKP", seg1.getAttribute("translation").orElse(""));
        assertEquals("MKP", seg2.getAttribute("translation").orElse(""));
    }

    @Test
    void translatesMultiSegmentCdsComplementJoin() throws Exception {
        // Two segments on - strand: positions 1-6 and 10-15
        // Concatenated in genomic order: bases[1..6] + bases[10..15]
        // Then reverse complemented by Translator
        // If reverse complement of "TTATTTCATGGG" = "CCCATGAAATAA"
        // ATG=M, AAA=K → wait, let me think...
        // Actually: concat = "TTATTT" + "CATGGG" = "TTATTTCATGGG" (12 bases)
        // Rev comp = "CCCATGAAATAA"
        // CCC=P, ATG=M(?), AAA=K, TAA=* → but P is not start codon
        // Let's use: seg1 bases "TTTCAT" (pos 1-6), seg2 bases "TTATTT" (pos 10-15)
        // Concat = "TTTCAT" + "TTATTT" = "TTTCATTTATTT" (12 bases)
        // Rev comp = "AAATAAATGAAA" → AAA=K, TAA=stop... hmm
        // Simpler: just test that slices are concatenated and translated
        // Use: seg1="TTATTT" (1-6), seg2="CAT" (10-12) → concat "TTATTTCAT" (9 bases)
        // Rev comp = "ATGAAATAA" → ATG=M, AAA=K, TAA=* → "MK"
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(1L), eq(6L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn("TTATTT");
        when(mockReader.getSequenceSlice(
                        eq(IdType.SUBMISSION_ID), eq("seq1"), eq(10L), eq(12L), eq(SequenceRangeOption.WHOLE_SEQUENCE)))
                .thenReturn("CAT");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 12, "-");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);
        fix.fixAnnotation(annotation, 1);

        assertEquals("MK", seg1.getAttribute("translation").orElse(""));
        assertEquals("MK", seg2.getAttribute("translation").orElse(""));
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
