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

import java.io.Reader;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.cli.ConversionFileFormat;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.ConversionDirectionProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.SequenceSource;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationStateProvider;

class TranslationFixTest {

    private TranslationFix fix;
    private SequenceLookup mockLookup;
    private ValidationContext context;
    private TranslationState translationState;

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
            public String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option)
                    throws Exception {
                return mockLookup.getSequenceSlice(seqId, fromBase, toBase);
            }

            @Override
            public long getSequenceLength(String seqId, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SequenceStats getSequenceStats(String seqId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<String> knownSeqIds() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }
        });
        context.register(SequenceLookup.class, compositeProvider);

        TranslationStateProvider stateProvider = new TranslationStateProvider();
        context.register(TranslationState.class, stateProvider);
        translationState = context.get(TranslationState.class);

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

        assertFalse(feature.hasAttribute("translation"));
    }

    @Test
    void translatesCdsFeatureComplementStrand() throws Exception {
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("TTATTTCAT");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "-");
        GFF3Annotation annotation = createAnnotation(feature);
        fix.fixAnnotation(annotation, 1);

        assertFalse(feature.hasAttribute("translation"));
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

        assertFalse(seg1.hasAttribute("translation"));
        assertFalse(seg2.hasAttribute("translation"));
        String key = TranslationState.buildKey(seg1.accession(), "cds1");
        assertEquals("MKP", translationState.get(key).newTranslation());
    }

    @Test
    void preservesThreePrimePartialAcrossJoinDuringTranslation() throws Exception {
        long seg1Start = 62703507L;
        long seg1End = 62703686L;
        long seg2Start = 62710877L;
        long seg2End = 62710985L;

        String seg1Sequence =
                "TTGAAATGCTTGTGGGCTGGGCCAGTCTCACAGCAAGATTACAGAGTCATCTCTCTGCCATCTGATGAGAAGATACCTCAGGCACTGGAAAAAAACCCAGACTGTATGGTTCTTTTGGGATCCACTGCAGAGAATACTCCCAAGACAGAGAAGCATTCCGACCCTCCCAGTCCATCAGGA";
        String seg2Sequence =
                "CCTGAGGATAGCACAAGGCACTGCAGGAAGCAGGAGCACTTCACGGTACAGGGCCCTGCTCTTGGTGCTGCTGGGCCTCTTTGCAGGGAAGGGATAAAAATCCAGGGAC";

        when(mockLookup.getSequenceSlice("seq1", seg1Start, seg1End)).thenReturn(seg1Sequence);
        when(mockLookup.getSequenceSlice("seq1", seg2Start, seg2End)).thenReturn(seg2Sequence);

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", seg1Start, seg1End, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", seg2Start, seg2End, "+");
        seg2.setThreePrimePartial();

        GFF3Annotation annotation = createAnnotation(seg1, seg2);
        fix.fixAnnotation(annotation, 1);

        String key = TranslationState.buildKey(seg1.accession(), "cds1");
        assertEquals(
                "MKCLWAGPVSQQDYRVISLPSDEKIPQALEKNPDCMVLLGSTAENTPKTEKHSDPPSPSGPEDSTRHCRKQEHFTVQGPALGAAGPLCREGIKIQG",
                translationState.get(key).newTranslation());
        assertFalse(seg1.isFivePrimePartial(), "5' partial should not be introduced for an already 3'-partial join");
        assertTrue(seg2.isThreePrimePartial(), "Existing 3' partial should be preserved on the last segment");
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

        assertFalse(seg1.hasAttribute("translation"));
        assertFalse(seg2.hasAttribute("translation"));
        String key = TranslationState.buildKey(seg1.accession(), "cds1");
        assertEquals("MK", translationState.get(key).newTranslation());
    }

    @Test
    void translatesMixedStrandJoinWithPerSegmentReverseComplement() throws Exception {
        // Partiality is auto-fixed during FF <-> GFF3 conversion
        enableFlatfileToGff3Conversion();
        // join(complement(1..6), 10..15)
        // seg1 minus: raw "TTCATT" → RC "AATGAA"
        // seg2 plus:  raw "CCCTAA" (unchanged)
        // concat → "AATGAACCCTAA" → forward translate: AAT=N, GAA=E, CCC=P, TAA=stop → "NEP"
        //
        // Without per-segment reverse complement (RC) the whole concat "TTCATTCCCTAA" would be RC'd by Translator
        // (driven by seg1 being complement), producing a garbled sequence with internal stops.
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("TTCATT");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCTAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        String key = TranslationState.buildKey("seq1", "cds1");
        assertEquals("NEP", translationState.get(key).newTranslation());
    }

    @Test
    void translatesMixedStrandThreeSegmentJoin() throws Exception {
        // Mirrors the rps12 trans-spliced join pattern:
        //   join(complement(1..3), 10..12, 20..22)
        // seg1 minus: raw "CAT" → RC "ATG"
        // seg2 plus:  raw "AAA"
        // seg3 plus:  raw "TAA"
        // concat (sorted by start) → "ATG" + "AAA" + "TAA" = "ATGAAATAA"
        // forward translate: ATG=M, AAA=K, TAA=stop → "MK"
        when(mockLookup.getSequenceSlice("seq1", 1L, 3L)).thenReturn("CAT");
        when(mockLookup.getSequenceSlice("seq1", 10L, 12L)).thenReturn("AAA");
        when(mockLookup.getSequenceSlice("seq1", 20L, 22L)).thenReturn("TAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 3, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 12, "+");
        GFF3Feature seg3 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 20, 22, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2, seg3), 1);

        String key = TranslationState.buildKey("seq1", "cds1");
        assertEquals("MK", translationState.get(key).newTranslation());
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
    void recordsOldAndNewTranslationInState() throws Exception {
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("ATGAAATAA");

        TranslationStateProvider stateProvider = new TranslationStateProvider();
        context.register(TranslationState.class, stateProvider);
        TranslationState state = context.get(TranslationState.class);

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        feature.setAttributeList("translation", List.of("OLDVALUE"));
        // fixFeature captures the old translation into TranslationState and removes the attribute
        fix.fixFeature(feature, 1);
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
        // Create a fix with a context that has no TranslationState registered
        TranslationFix fixNoState = new TranslationFix();
        ValidationContext ctxNoState = new ValidationContext();
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        provider.addSource(new SequenceSource() {
            @Override
            public boolean hasSequence(String seqId) {
                return true;
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option)
                    throws Exception {
                return mockLookup.getSequenceSlice(seqId, fromBase, toBase);
            }

            @Override
            public long getSequenceLength(String seqId, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SequenceStats getSequenceStats(String seqId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<String> knownSeqIds() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }
        });
        ctxNoState.register(SequenceLookup.class, provider);
        ValidationRegistry.injectContext(fixNoState, ctxNoState);

        when(mockLookup.getSequenceSlice(any(), anyLong(), anyLong())).thenReturn("ATGAAATAA");

        GFF3Feature feature = createFeature(OntologyTerm.CDS.name(), "seq1", 1, 9, "+");
        GFF3Annotation annotation = createAnnotation(feature);
        fixNoState.fixAnnotation(annotation, 1);

        // Translation is not set on the feature (no TranslationState to record it either)
        assertFalse(feature.hasAttribute("translation"));
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

    @Test
    void sortsNonTransSplicedSegmentsByGenomicPosition() throws Exception {
        // Segments added in reverse genomic order — without trans_splicing they must be sorted
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("ATGAAAAAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 18L)).thenReturn("CCCGGGTAA");

        GFF3Feature segHigh = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 18, "+");
        GFF3Feature segLow = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 9, "+");
        // Add high-coord segment first (reverse genomic order)
        GFF3Annotation annotation = createAnnotation(segHigh, segLow);
        fix.fixAnnotation(annotation, 1);

        // After sort: segLow(1-9) first → "ATGAAATAA"+"CCCGGGAAA" → translation "MKKPG"
        String key = TranslationState.buildKey("seq1", "cds1");
        assertEquals("MKKPG", translationState.get(key).newTranslation());
    }

    @Test
    void preservesOriginalOrderForTransSplicedSegments() throws Exception {
        // Trans-spliced join: ATG is at the high-coord segment which appears first in the join
        // Original order must be preserved — sorting by genomic position would break the translation
        when(mockLookup.getSequenceSlice("seq1", 10L, 18L)).thenReturn("ATGAAAAAA");
        when(mockLookup.getSequenceSlice("seq1", 1L, 9L)).thenReturn("CCCGGGTAA");

        GFF3Feature segHigh = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 18, "+");
        GFF3Feature segLow = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 9, "+");
        segHigh.addAttribute(GFF3Attributes.TRANS_SPLICING, "true");
        // Add high-coord segment first — this is the intended trans-spliced order
        GFF3Annotation annotation = createAnnotation(segHigh, segLow);
        fix.fixAnnotation(annotation, 1);

        // Original order preserved: segHigh(10-18) first → "ATGAAATAA"+"CCCGGGAAA" → "MKKPG"
        String key = TranslationState.buildKey("seq1", "cds1");
        assertEquals("MKKPG", translationState.get(key).newTranslation());
    }

    @Test
    void fivePrimePartialAssignedToFirstSegmentOnForwardStrand() throws Exception {
        // Partiality is auto-fixed during FF <-> GFF3 conversion
        enableFlatfileToGff3Conversion();
        // No ATG start codon → Translator fixes 5' partial
        // Forward strand → 5' partial belongs on the first (lowest coord) segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("AAACAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCTAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertTrue(seg1.isFivePrimePartial());
        assertFalse(seg2.isFivePrimePartial());
    }

    @Test
    void threePrimePartialAssignedToLastSegmentOnForwardStrand() throws Exception {
        // Partiality is auto-fixed during FF <-> GFF3 conversion
        enableFlatfileToGff3Conversion();
        // Has ATG but no stop codon → Translator fixes 3' partial
        // Forward strand → 3' partial belongs on the last (highest coord) segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("ATGAAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCCCC");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertFalse(seg1.isThreePrimePartial());
        assertTrue(seg2.isThreePrimePartial());
    }

    @Test
    void fivePrimePartialAssignedToLastSegmentOnComplementStrand() throws Exception {
        // Partiality is auto-fixed during FF <-> GFF3 conversion
        enableFlatfileToGff3Conversion();
        // Concat "TTATTGTTT" → RC "AAACAATAA" → no ATG, has stop → 5' partial
        // Complement strand → 5' partial belongs on the last (highest coord) segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("TTATTG");
        when(mockLookup.getSequenceSlice("seq1", 10L, 12L)).thenReturn("TTT");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 12, "-");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertFalse(seg1.isFivePrimePartial());
        assertTrue(seg2.isFivePrimePartial());
    }

    @Test
    void threePrimePartialAssignedToFirstSegmentOnComplementStrand() throws Exception {
        // Partiality is auto-fixed during FF <-> GFF3 conversion
        enableFlatfileToGff3Conversion();
        // Concat "GGGTTTCAT" → RC "ATGAAACCC" → has ATG, no stop → 3' partial
        // Complement strand → 3' partial belongs on the first (lowest coord) segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("GGGTTT");
        when(mockLookup.getSequenceSlice("seq1", 10L, 12L)).thenReturn("CAT");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 12, "-");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertTrue(seg1.isThreePrimePartial());
        assertFalse(seg2.isThreePrimePartial());
    }

    @Test
    void fivePrimePartialOnForwardStrandThrowsWhenValidating() throws Exception {
        // No ATG start codon → 5' partial is reported as an error on the first segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("AAACAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCTAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);

        ValidationException ex = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(ex.getMessage().contains("location 1-6 should be 5' partial"), ex.getMessage());
        assertFalse(seg1.isFivePrimePartial());
        assertFalse(seg2.isFivePrimePartial());
    }

    @Test
    void threePrimePartialOnForwardStrandThrowsWhenValidating() throws Exception {
        // Has ATG but no stop codon → 3' partial is reported as an error on the last segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("ATGAAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCCCC");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);

        ValidationException ex = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(ex.getMessage().contains("location 10-15 should be 3' partial"), ex.getMessage());
        assertFalse(seg1.isThreePrimePartial());
        assertFalse(seg2.isThreePrimePartial());
    }

    @Test
    void fivePrimePartialOnComplementStrandThrowsWhenValidating() throws Exception {
        // RC "AAACAATAA" → no ATG → 5' partial is reported as an error on the last segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("TTATTG");
        when(mockLookup.getSequenceSlice("seq1", 10L, 12L)).thenReturn("TTT");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 12, "-");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);

        ValidationException ex = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(ex.getMessage().contains("location 10-12 should be 5' partial"), ex.getMessage());
    }

    @Test
    void threePrimePartialOnComplementStrandThrowsWhenValidating() throws Exception {
        // RC "ATGAAACCC" → no stop codon → 3' partial is reported as an error on the first segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("GGGTTT");
        when(mockLookup.getSequenceSlice("seq1", 10L, 12L)).thenReturn("CAT");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "-");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 12, "-");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);

        ValidationException ex = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(ex.getMessage().contains("location 1-6 should be 3' partial"), ex.getMessage());
    }

    // ATG AAA×25 TAA: starts with ATG and ends with a stop codon, so both ends are complete.
    private static final String COMPLETE_CDS_81 = "ATG" + "AAA".repeat(25) + "TAA";

    @Test
    void threePrimePartialRemovedWhenStopCodonPresentForFlatfileToGff3() throws Exception {
        // Ends in TAA → 3' end is complete, so the existing partial=end is wrong and is removed
        enableFlatfileToGff3Conversion();
        when(mockLookup.getSequenceSlice("seqA", 1L, 81L)).thenReturn(COMPLETE_CDS_81);

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seqA", 1, 81, "+");
        cds.setThreePrimePartial();
        fix.fixAnnotation(createAnnotation(cds), 1);

        assertFalse(cds.isThreePrimePartial());
        assertFalse(cds.hasAttribute(GFF3Attributes.PARTIAL));
    }

    @Test
    void threePrimePartialWithStopCodonThrowsWhenValidating() throws Exception {
        // Ends in TAA but marked partial=end → reported as an error, feature left unchanged
        when(mockLookup.getSequenceSlice("seqA", 1L, 81L)).thenReturn(COMPLETE_CDS_81);

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seqA", 1, 81, "+");
        cds.setThreePrimePartial();
        GFF3Annotation annotation = createAnnotation(cds);

        ValidationException ex = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(ex.getMessage().contains("location 1-81 should not be 3' partial"), ex.getMessage());
        assertTrue(cds.isThreePrimePartial());
    }

    @Test
    void threePrimePartialRemovedOnComplementStrandKeepsFivePrimePartial() throws Exception {
        // Complement: 3' partial is partial=start, 5' partial is partial=end.
        // Coding strand "AAA..TAA": no ATG (5' partial is valid), ends in TAA (3' partial is wrong)
        enableFlatfileToGff3Conversion();
        when(mockLookup.getSequenceSlice("seqA", 1L, 81L))
                .thenReturn(reverseComplement("AAA" + "AAA".repeat(25) + "TAA"));

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seqA", 1, 81, "-");
        cds.setFivePrimePartial();
        cds.setThreePrimePartial();
        fix.fixAnnotation(createAnnotation(cds), 1);

        assertFalse(cds.isThreePrimePartial());
        assertTrue(cds.isFivePrimePartial());
        assertEquals(Optional.of(List.of("end")), cds.getAttributeList(GFF3Attributes.PARTIAL));
    }

    @Test
    void pseudoSetOnAllSegmentsForFlatfileToGff3() throws Exception {
        // "ATGTAA"+"AAATAA" → M * K * → internal stop codon → Translator fixes pseudo
        enableFlatfileToGff3Conversion();
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("ATGTAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("AAATAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertEquals(Optional.of("true"), seg1.getAttribute(GFF3Attributes.PSEUDO));
        assertEquals(Optional.of("true"), seg2.getAttribute(GFF3Attributes.PSEUDO));
    }

    @Test
    void pseudoThrowsWhenValidating() throws Exception {
        // "ATGTAA"+"AAATAA" → M * K * → internal stop codon → reported as an error for the whole join
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("ATGTAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("AAATAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        GFF3Annotation annotation = createAnnotation(seg1, seg2);

        ValidationException ex = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(ex.getMessage().contains("locations 1-6, 10-15 should be pseudo"), ex.getMessage());
        assertFalse(seg1.hasAttribute(GFF3Attributes.PSEUDO));
        assertFalse(seg2.hasAttribute(GFF3Attributes.PSEUDO));
    }

    @Test
    void fivePrimePartialFixedForGff3ToFlatfile() throws Exception {
        enableGff3ToFlatfileConversion();
        // No ATG start codon → 5' partial is fixed on the first segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("AAACAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCTAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertTrue(seg1.isFivePrimePartial());
        assertFalse(seg2.isFivePrimePartial());
    }

    @Test
    void threePrimePartialFixedForGff3ToFlatfile() throws Exception {
        enableGff3ToFlatfileConversion();
        // Has ATG but no stop codon → 3' partial is fixed on the last segment
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("ATGAAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("CCCCCC");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertFalse(seg1.isThreePrimePartial());
        assertTrue(seg2.isThreePrimePartial());
    }

    @Test
    void threePrimePartialRemovedWhenStopCodonPresentForGff3ToFlatfile() throws Exception {
        enableGff3ToFlatfileConversion();
        when(mockLookup.getSequenceSlice("seqA", 1L, 81L)).thenReturn(COMPLETE_CDS_81);

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seqA", 1, 81, "+");
        cds.setThreePrimePartial();
        fix.fixAnnotation(createAnnotation(cds), 1);

        assertFalse(cds.isThreePrimePartial());
        assertFalse(cds.hasAttribute(GFF3Attributes.PARTIAL));
    }

    @Test
    void pseudoSetOnAllSegmentsForGff3ToFlatfile() throws Exception {
        enableGff3ToFlatfileConversion();
        // "ATGTAA"+"AAATAA" → M * K * → internal stop codon → pseudo is fixed on all segments
        when(mockLookup.getSequenceSlice("seq1", 1L, 6L)).thenReturn("ATGTAA");
        when(mockLookup.getSequenceSlice("seq1", 10L, 15L)).thenReturn("AAATAA");

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 1, 6, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 10, 15, "+");
        fix.fixAnnotation(createAnnotation(seg1, seg2), 1);

        assertEquals(Optional.of("true"), seg1.getAttribute(GFF3Attributes.PSEUDO));
        assertEquals(Optional.of("true"), seg2.getAttribute(GFF3Attributes.PSEUDO));
    }

    private void enableFlatfileToGff3Conversion() {
        context.register(
                ConversionDirectionProvider.class,
                new ConversionDirectionProvider(ConversionFileFormat.embl, ConversionFileFormat.gff3));
    }

    private void enableGff3ToFlatfileConversion() {
        context.register(
                ConversionDirectionProvider.class,
                new ConversionDirectionProvider(ConversionFileFormat.gff3, ConversionFileFormat.embl));
    }

    private static final String CDS = "ATGAAACCCGGGTAA";
    private static final String FILLER = "TTTTTTTTTTTTTTT";
    private static final long CIRCLE = 30;

    @Test
    void translatesSingleLineCdsCrossingTheOrigin() throws Exception {
        circularSequence(CDS.substring(6) + FILLER + CDS.substring(0, 6));

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 25, 39, "+");
        fix.fixAnnotation(circularAnnotation(cds), 1);

        assertEquals("MKPG", translationOf(cds));
        assertNoPartials(cds);
    }

    @Test
    void translatesCdsWithSegmentsInVirtualSpace() throws Exception {
        circularSequence(CDS.substring(6) + FILLER + CDS.substring(0, 6));

        GFF3Feature seg1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 25, 30, "+");
        GFF3Feature seg2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 31, 39, "+");
        fix.fixAnnotation(circularAnnotation(seg1, seg2), 1);

        assertEquals("MKPG", translationOf(seg1));
        assertNoPartials(seg1, seg2);
    }

    @Test
    void translatesCdsWithSegmentWhollyPastTheOrigin() throws Exception {
        circularSequence("CCC" + CDS.substring(6) + "TTTTTTTTTTTT" + CDS.substring(0, 6));

        GFF3Feature exon1 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 25, 30, "+");
        GFF3Feature exon2 = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 34, 42, "+");
        fix.fixAnnotation(circularAnnotation(exon1, exon2), 1);

        assertEquals("MKPG", translationOf(exon1));
        assertNoPartials(exon1, exon2);
    }

    @Test
    void translatesMinusStrandCdsCrossingTheOrigin() throws Exception {
        String reverseComplement = reverseComplement(CDS);
        circularSequence(reverseComplement.substring(6) + FILLER + reverseComplement.substring(0, 6));

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 25, 39, "-");
        fix.fixAnnotation(circularAnnotation(cds), 1);

        assertEquals("MKPG", translationOf(cds));
        assertNoPartials(cds);
    }

    @Test
    void doesNotUnwrapOnLinearSequence() throws Exception {
        sequence(CDS.substring(6) + FILLER + CDS.substring(0, 6));

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 25, 39, "+");
        GFF3Annotation annotation = createAnnotation(cds);
        annotation.setSequenceRegion(new GFF3SequenceRegion("seq1", Optional.empty(), 1, CIRCLE));

        ValidationException exception = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(exception.getMessage().contains("bad base range"));
    }

    @Test
    void doesNotUnwrapWithoutHeaderForAccession() throws Exception {
        sequence(CDS.substring(6) + FILLER + CDS.substring(0, 6));
        FastaHeaderProvider headers = new FastaHeaderProvider();
        headers.addSource(seqId -> Optional.empty());
        context.register(FastaHeaderProvider.class, headers);

        GFF3Feature cds = createFeature(OntologyTerm.CDS.name(), "cds1", "seq1", 25, 39, "+");
        GFF3Annotation annotation = createAnnotation(cds);
        annotation.setSequenceRegion(new GFF3SequenceRegion("seq1", Optional.empty(), 1, CIRCLE));

        ValidationException exception = assertThrows(ValidationException.class, () -> fix.fixAnnotation(annotation, 1));
        assertTrue(exception.getMessage().contains("bad base range"));
    }

    private void sequence(String bases) throws Exception {
        assertEquals(CIRCLE, bases.length());
        when(mockLookup.getSequenceSlice(eq("seq1"), anyLong(), anyLong())).thenAnswer(invocation -> {
            long from = invocation.getArgument(1);
            long to = invocation.getArgument(2);
            if (from < 1 || to > bases.length()) {
                throw new IllegalArgumentException("bad base range: " + from + ".." + to);
            }
            return bases.substring((int) from - 1, (int) to);
        });
    }

    private void circularSequence(String bases) throws Exception {
        sequence(bases);
        FastaHeader header = new FastaHeader();
        header.setTopology("circular");
        FastaHeaderProvider headers = new FastaHeaderProvider();
        headers.addSource(seqId -> "seq1".equals(seqId) ? Optional.of(header) : Optional.empty());
        context.register(FastaHeaderProvider.class, headers);
    }

    private GFF3Annotation circularAnnotation(GFF3Feature... features) {
        GFF3Annotation annotation = createAnnotation(features);
        annotation.setSequenceRegion(new GFF3SequenceRegion("seq1", Optional.empty(), 1, CIRCLE));
        return annotation;
    }

    private String translationOf(GFF3Feature feature) {
        return translationState
                .get(TranslationState.buildKey(
                        feature.accession(), feature.getId().orElseThrow()))
                .newTranslation();
    }

    private static void assertNoPartials(GFF3Feature... segments) {
        for (GFF3Feature segment : segments) {
            assertFalse(segment.isFivePrimePartial(), "no 5' partial at the origin");
            assertFalse(segment.isThreePrimePartial(), "no 3' partial at the origin");
        }
    }

    private static String reverseComplement(String bases) {
        StringBuilder complement = new StringBuilder();
        for (char base : bases.toCharArray()) {
            complement.append(
                    switch (base) {
                        case 'A' -> 'T';
                        case 'T' -> 'A';
                        case 'C' -> 'G';
                        default -> 'C';
                    });
        }
        return complement.reverse().toString();
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
