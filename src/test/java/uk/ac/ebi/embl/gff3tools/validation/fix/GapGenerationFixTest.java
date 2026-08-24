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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContext;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

class GapGenerationFixTest {

    private static final String ACCESSION = "SEQ1";

    private OntologyClient ontologyClient;
    private SequenceLookup sequenceLookup;

    @BeforeEach
    void setUp() {
        ontologyClient = mock(OntologyClient.class);
        sequenceLookup = mock(SequenceLookup.class);
        // Every feature named "gap" or "assembly_gap" resolves to SO:0000730; anything else does not.
        when(ontologyClient.findTermByNameOrSynonym(anyString())).thenReturn(Optional.empty());
        when(ontologyClient.findTermByNameOrSynonym("gap")).thenReturn(Optional.of(OntologyTerm.GAP.ID));
        when(ontologyClient.findTermByNameOrSynonym("assembly_gap")).thenReturn(Optional.of(OntologyTerm.GAP.ID));
    }

    // ---------------------------------------------------------------- helpers

    /** A fix wired with the mocked lookup and, when supplied, an AnalysisContext. */
    private GapGenerationFix newFix(AnalysisContext analysisContext) {
        GapGenerationFix fix = new GapGenerationFix();
        ValidationContext context = TestUtils.createTestContext(ontologyClient);
        TestUtils.registerProvider(context, SequenceLookup.class, sequenceLookup);
        if (analysisContext != null) {
            TestUtils.registerProvider(context, AnalysisContext.class, analysisContext);
        }
        TestUtils.injectContext(fix, context);
        return fix;
    }

    /** Default wiring: minGapLength 10, no gap_type. */
    private GapGenerationFix newFix() {
        return newFix(new AnalysisContext(AnalysisType.UNKNOWN, 10));
    }

    private static GFF3Annotation annotation(GFF3Feature... features) {
        return TestUtils.createGFF3Annotation(ACCESSION, 1, 1000, features);
    }

    private static GFF3Feature gapFeature(String name, long start, long end, String id) {
        return TestUtils.createGFF3Feature(id, null, name, ACCESSION, start, end);
    }

    private void runsAre(GapRegion... runs) {
        try {
            when(sequenceLookup.getGapRegions(ACCESSION)).thenReturn(List.of(runs));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The features the fix added, in the order it added them. */
    private static List<GFF3Feature> generated(GFF3Annotation annotation, int preExistingCount) {
        return new ArrayList<>(annotation
                .getFeatures()
                .subList(preExistingCount, annotation.getFeatures().size()));
    }

    private static String spans(List<GFF3Feature> features) {
        return features.stream().map(f -> f.getStart() + "-" + f.getEnd()).collect(Collectors.joining(", "));
    }

    // ---------------------------------------------------------- threshold

    @Test
    void addsGapForUncoveredRunAtOrAboveThreshold() {
        runsAre(new GapRegion(41, 50)); // exactly 10 bases
        GFF3Annotation annotation = annotation();

        newFix().fix(annotation, 1);

        assertEquals(1, annotation.getFeatures().size());
        GFF3Feature gap = annotation.getFeatures().get(0);
        assertEquals("gap", gap.getName());
        assertEquals(41, gap.getStart());
        assertEquals(50, gap.getEnd());
        assertEquals(Optional.of("10"), gap.getAttribute(GFF3Attributes.ESTIMATED_LENGTH));
    }

    @Test
    void ignoresRunShorterThanThreshold() {
        runsAre(new GapRegion(41, 49)); // 9 bases
        GFF3Annotation annotation = annotation();

        newFix().fix(annotation, 1);

        assertTrue(annotation.getFeatures().isEmpty());
    }

    @Test
    void usesMinGapSizeFromAnalysisContext() {
        runsAre(new GapRegion(41, 60)); // 20 bases
        GFF3Annotation annotation = annotation();

        newFix(new AnalysisContext(AnalysisType.UNKNOWN, 50)).fix(annotation, 1);

        assertTrue(annotation.getFeatures().isEmpty(), "20-base run is below a minGapSize of 50");
    }

    @Test
    void fallsBackToDefaultThresholdWithoutAnalysisContext() {
        runsAre(new GapRegion(41, 50), new GapRegion(80, 88)); // 10 bases, then 9
        GFF3Annotation annotation = annotation();

        newFix(null).fix(annotation, 1);

        assertEquals("41-50", spans(annotation.getFeatures()), "default minGapSize is 10");
    }

    // ----------------------------------------------------------- coverage

    @ParameterizedTest
    @ValueSource(strings = {"gap", "assembly_gap"})
    void skipsRunAlreadyCoveredExactly(String featureName) {
        runsAre(new GapRegion(41, 76));
        GFF3Annotation annotation = annotation(gapFeature(featureName, 41, 76, "existing"));

        newFix().fix(annotation, 1);

        assertEquals(1, annotation.getFeatures().size(), "nothing added on top of full coverage");
    }

    @Test
    void skipsRunWhoseUnionOfExistingGapsCoversIt() {
        // The submitter legitimately partitioned one N-run into two adjacent gaps.
        runsAre(new GapRegion(41, 76));
        GFF3Annotation annotation = annotation(gapFeature("gap", 41, 60, "a"), gapFeature("gap", 61, 76, "b"));

        newFix().fix(annotation, 1);

        assertEquals(2, annotation.getFeatures().size());
    }

    @Test
    void skipsRunCoveredByGapExtendingBeyondBothEdges() {
        runsAre(new GapRegion(41, 76));
        GFF3Annotation annotation = annotation(gapFeature("gap", 30, 90, "wide"));

        newFix().fix(annotation, 1);

        assertEquals(1, annotation.getFeatures().size());
    }

    @Test
    void addsFullRunGapWhenOnlyPartiallyCovered() {
        // The spec's worked example: run 41-76, submitter annotated only 43-60.
        runsAre(new GapRegion(41, 76));
        GFF3Feature existing = gapFeature("gap", 43, 60, "gap1");
        GFF3Annotation annotation = annotation(existing);

        newFix().fix(annotation, 1);

        List<GFF3Feature> added = generated(annotation, 1);
        assertEquals("41-76", spans(added), "one gap spanning the whole run, not the fragments");
        assertEquals(Optional.of("36"), added.get(0).getAttribute(GFF3Attributes.ESTIMATED_LENGTH));
        // The submitter's feature is left exactly as it was.
        assertEquals(43, existing.getStart());
        assertEquals(60, existing.getEnd());
    }

    @Test
    void addsSingleFullRunGapDespiteSeveralPartialGapsInside() {
        runsAre(new GapRegion(1, 50));
        GFF3Annotation annotation = annotation(gapFeature("gap", 5, 20, "a"), gapFeature("gap", 10, 15, "b"));

        newFix().fix(annotation, 1);

        assertEquals("1-50", spans(generated(annotation, 2)));
    }

    @Test
    void ignoresNonGapFeaturesWhenAssessingCoverage() {
        // A gene spanning the run must not be mistaken for coverage.
        runsAre(new GapRegion(41, 76));
        GFF3Annotation annotation = annotation(gapFeature("gene", 1, 200, "gene1"));

        newFix().fix(annotation, 1);

        assertEquals("41-76", spans(generated(annotation, 1)));
    }

    @Test
    void ignoresMalformedGapFeatureWithEndBeforeStart() {
        // end < start covers nothing. It must not suppress generation, and must not abort the run:
        // GapRegion's constructor rejects such a range, so the feature has to be filtered first.
        runsAre(new GapRegion(1, 50));
        GFF3Annotation annotation = annotation(gapFeature("gap", 40, 20, "broken"));

        newFix().fix(annotation, 1);

        assertEquals("1-50", spans(generated(annotation, 1)));
    }

    @Test
    void handlesMultipleRunsOnOneSequence() {
        runsAre(new GapRegion(41, 76), new GapRegion(100, 120), new GapRegion(200, 205));
        GFF3Annotation annotation = annotation();

        newFix().fix(annotation, 1);

        assertEquals("41-76, 100-120", spans(annotation.getFeatures()), "the 6-base run is below threshold");
    }

    // ----------------------------------------------------------------- ids

    @Test
    void assignsDocumentWideIdsAcrossAnnotations() {
        runsAre(new GapRegion(41, 76));
        GapGenerationFix fix = newFix();

        GFF3Annotation first = annotation();
        fix.fix(first, 1);
        GFF3Annotation second = annotation();
        fix.fix(second, 1);

        assertEquals(Optional.of("gap"), first.getFeatures().get(0).getAttribute(GFF3Attributes.ATTRIBUTE_ID));
        assertEquals(Optional.of("gap_1"), second.getFeatures().get(0).getAttribute(GFF3Attributes.ATTRIBUTE_ID));
    }

    @Test
    void skipsIdsAlreadyUsedByTheSubmitter() {
        // A submitter feature with ID=gap must not be shadowed by the generated one.
        runsAre(new GapRegion(41, 76));
        GFF3Annotation annotation = annotation(gapFeature("gene", 1, 10, "gap"));

        newFix().fix(annotation, 1);

        assertEquals(Optional.of("gap_1"), generated(annotation, 1).get(0).getAttribute(GFF3Attributes.ATTRIBUTE_ID));
    }

    // ---------------------------------------------------------- attributes

    @Test
    void emitsNoGapTypeByDefault() {
        runsAre(new GapRegion(41, 76));
        GFF3Annotation annotation = annotation();

        newFix().fix(annotation, 1);

        GFF3Feature gap = annotation.getFeatures().get(0);
        assertFalse(gap.hasAttribute(GFF3Attributes.GAP_TYPE));
        assertFalse(gap.hasAttribute(GFF3Attributes.LINKAGE_EVIDENCE));
    }

    @Test
    void emitsGapTypeAndLinkageEvidenceFromAnalysisContext() {
        runsAre(new GapRegion(41, 76));
        GFF3Annotation annotation = annotation();

        newFix(new AnalysisContext(AnalysisType.UNKNOWN, 10, "within scaffold", "paired-ends"))
                .fix(annotation, 1);

        GFF3Feature gap = annotation.getFeatures().get(0);
        assertEquals(Optional.of("within scaffold"), gap.getAttribute(GFF3Attributes.GAP_TYPE));
        assertEquals(Optional.of("paired-ends"), gap.getAttribute(GFF3Attributes.LINKAGE_EVIDENCE));
    }

    @Test
    void generatedFeatureIsAppendedAndCarriesTheAnnotationSeqId() {
        runsAre(new GapRegion(41, 76));
        GFF3Feature gene = gapFeature("gene", 1, 10, "gene1");
        GFF3Annotation annotation = annotation(gene);

        newFix().fix(annotation, 1);

        assertSame(gene, annotation.getFeatures().get(0), "generated gaps append, never insert");
        GFF3Feature gap = annotation.getFeatures().get(1);
        assertEquals(ACCESSION, gap.getSeqId());
        assertEquals("+", gap.getStrand());
        assertEquals(".", gap.getSource());
    }

    // ------------------------------------------------------------- no-ops

    @Test
    void noOpWithoutSequenceLookup() {
        GapGenerationFix fix = new GapGenerationFix();
        TestUtils.injectContext(fix, TestUtils.createTestContext(ontologyClient));
        GFF3Annotation annotation = annotation();

        fix.fix(annotation, 1);

        assertTrue(annotation.getFeatures().isEmpty());
    }

    @Test
    void noOpWhenLookupCannotResolveTheAccession() throws Exception {
        when(sequenceLookup.getGapRegions(ACCESSION)).thenThrow(new IllegalStateException("no sequence source"));
        GFF3Annotation annotation = annotation();

        assertDoesNotThrow(() -> newFix().fix(annotation, 1), "an unreadable sequence must not abort the run");
        assertTrue(annotation.getFeatures().isEmpty());
    }

    @Test
    void noOpWhenSequenceHasNoRuns() {
        runsAre();
        GFF3Annotation annotation = annotation();

        newFix().fix(annotation, 1);

        assertTrue(annotation.getFeatures().isEmpty());
    }

    @Test
    void isIdempotent() {
        runsAre(new GapRegion(41, 76));
        GapGenerationFix fix = newFix();
        GFF3Annotation annotation = annotation();

        fix.fix(annotation, 1);
        fix.fix(annotation, 1);

        assertEquals("41-76", spans(annotation.getFeatures()), "the second pass finds full coverage");
    }

    // -------------------------------------------------------- subtract()

    @Test
    void subtractReturnsWholeRunWhenNothingCovered() {
        assertEquals("1-20", intervals(GapGenerationFix.subtract(new GapRegion(1, 20), List.of())));
    }

    @Test
    void subtractReturnsEmptyWhenFullyCovered() {
        assertTrue(GapGenerationFix.subtract(new GapRegion(1, 20), List.of(new GapRegion(1, 20)))
                .isEmpty());
    }

    @Test
    void subtractSplitsAroundAnInteriorInterval() {
        assertEquals(
                "1-10, 14-36",
                intervals(GapGenerationFix.subtract(new GapRegion(1, 36), List.of(new GapRegion(11, 13)))));
    }

    @Test
    void subtractCollapsesNestedAndOverlappingIntervals() {
        assertEquals(
                "1-4, 21-50",
                intervals(GapGenerationFix.subtract(
                        new GapRegion(1, 50), List.of(new GapRegion(5, 20), new GapRegion(10, 15)))));
    }

    @Test
    void subtractEmitsNoZeroLengthSegmentBetweenAdjacentIntervals() {
        assertEquals(
                "1-4, 16-30",
                intervals(GapGenerationFix.subtract(
                        new GapRegion(1, 30), List.of(new GapRegion(5, 10), new GapRegion(11, 15)))));
    }

    @Test
    void subtractClipsIntervalsExtendingBeyondTheRun() {
        assertEquals(
                "31-40", intervals(GapGenerationFix.subtract(new GapRegion(10, 40), List.of(new GapRegion(1, 30)))));
    }

    @Test
    void subtractIgnoresIntervalsOutsideTheRunAndMalformedOnes() {
        // GapRegion's constructor rejects end < start, but its fields are public and mutable, so an
        // inverted interval is still reachable and must be ignored rather than mis-clipped.
        GapRegion inverted = new GapRegion();
        inverted.startBase = 35;
        inverted.endBase = 20;
        assertEquals(
                "10-40",
                intervals(GapGenerationFix.subtract(
                        new GapRegion(10, 40), List.of(new GapRegion(1, 5), inverted, new GapRegion(60, 70)))));
    }

    /** {@code spans} over GapRegions, mirroring the GFF3Feature helper above. */
    private static String intervals(List<GapRegion> regions) {
        return regions.stream().map(r -> r.startBase + "-" + r.endBase).collect(Collectors.joining(", "));
    }
}
