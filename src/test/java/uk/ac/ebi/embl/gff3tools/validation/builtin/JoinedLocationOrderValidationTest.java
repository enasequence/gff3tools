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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class JoinedLocationOrderValidationTest {

    private static final String GENE_FEATURE = "gene";
    private static final String ACCESSION = "ACC123";
    private static final String PLUS = "+";
    private static final String MINUS = "-";

    private JoinedLocationOrderValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new JoinedLocationOrderValidation();
        TestUtils.injectContext(validation);
        annotation = new GFF3Annotation();
    }

    @Test
    void ascendingSegmentsAreValid() {
        segment("gene1", 1, 500);
        segment("gene1", 600, 700);
        segment("gene1", 800, 822);

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    @Test
    void outOfOrderSegmentsAreReported() {
        segment("gene1", 1, 500);
        segment("gene1", 800, 822);
        segment("gene1", 600, 700);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
        assertTrue(exception.getMessage().contains("ascending coordinate order"));
        assertTrue(exception.getMessage().contains("segment 600..700 starts before the preceding segment 800..822"));
        assertTrue(exception.getMessage().contains("\"gene1\""));
        assertTrue(exception.getMessage().contains(ACCESSION));
    }

    /** The order the join is built from is the file order, whichever strand the feature reads on. */
    @Test
    void minusStrandAscendingSegmentsAreValid() {
        segmentOnStrand("gene1", 1, 500, MINUS);
        segmentOnStrand("gene1", 600, 700, MINUS);

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    @Test
    void minusStrandOutOfOrderSegmentsAreReported() {
        segmentOnStrand("gene1", 600, 700, MINUS);
        segmentOnStrand("gene1", 1, 500, MINUS);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 1..500 starts before the preceding segment 600..700"));
    }

    @Test
    void singleSegmentFeatureIsSkipped() {
        segment("gene1", 600, 700);

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    /** Features without an ID are separate features, not segments of one, however they are listed. */
    @Test
    void featuresWithoutAnIdAreNotJudgedAgainstEachOther() {
        add(build(GENE_FEATURE, null, ACCESSION, 800, 822, PLUS));
        add(build(GENE_FEATURE, null, ACCESSION, 600, 700, PLUS));

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    @Test
    void unrelatedFeaturesListedOutOfOrderAreNotJudgedAgainstEachOther() {
        segment("gene1", 800, 822);
        segment("gene2", 600, 700);

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    /**
     * Overlap is the business of the no-overlap rule; two segments starting together are in order as
     * far as this one is concerned.
     */
    @Test
    void segmentsSharingAStartAreInOrder() {
        segment("gene1", 600, 700);
        segment("gene1", 600, 900);

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    @Test
    void transSplicedFeatureIsExempt() {
        segment("gene1", 800, 822).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");
        segment("gene1", 600, 700).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    @Test
    void transSplicingOnASingleSegmentExemptsTheWholeFeature() {
        segment("gene1", 800, 822).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");
        segment("gene1", 600, 700);

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    /** The rps12 case: three segments whose order carries the biology rather than the coordinates. */
    @Test
    void transSplicedRps12SegmentsAreExempt() {
        segmentOnStrand("rps12", 99266, 99291, MINUS).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");
        segmentOnStrand("rps12", 99828, 100059, MINUS).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");
        segmentOnStrand("rps12", 71632, 71745, MINUS).addAttribute(GFF3Attributes.TRANS_SPLICING, "true");

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    /** Only trans-splicing licenses reordering, unlike the wider exemption CDS_MRNA_LOCATION grants. */
    @Test
    void ribosomalSlippageIsNotExempt() {
        segment("gene1", 800, 822).addAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE, "true");
        segment("gene1", 600, 700).addAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE, "true");

        assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    @Test
    void everyDisorderedFeatureIsReportedTogether() {
        segment("geneA", 800, 822);
        segment("geneA", 600, 700);
        segment("geneB", 1800, 1822);
        segment("geneB", 1600, 1700);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 600..700 starts before the preceding segment 800..822"));
        assertTrue(
                exception.getMessage().contains("segment 1600..1700 starts before the preceding segment 1800..1822"));
    }

    // ---- circular topology ---------------------------------------------------

    /** A feature crossing the origin of a circular sequence steps back once, legitimately. */
    @Test
    void originSpanningJoinOnCircularSequenceIsValid() {
        injectTopology("circular");
        segment("gene1", 900, 1000);
        segment("gene1", 1, 200);

        assertDoesNotThrow(() -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    @Test
    void originSpanningJoinOnLinearSequenceIsReported() {
        injectTopology("linear");
        segment("gene1", 900, 1000);
        segment("gene1", 1, 200);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 1..200 starts before the preceding segment 900..1000"));
    }

    /** One step back is the origin; a second one is a scrambled join whatever the topology. */
    @Test
    void secondBackwardStepOnCircularSequenceIsReported() {
        injectTopology("circular");
        segment("gene1", 900, 1000);
        segment("gene1", 1, 200);
        segment("gene1", 400, 500);
        segment("gene1", 300, 350);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 300..350 starts before the preceding segment 400..500"));
    }

    @Test
    void unknownTopologyIsTreatedAsLinear() {
        injectTopology("something-else");
        segment("gene1", 900, 1000);
        segment("gene1", 1, 200);

        assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    /** Without a header source the run cannot know the topology, so nothing is excused. */
    @Test
    void absentHeaderIsTreatedAsLinear() {
        ValidationContext context = TestUtils.createTestContext();
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(seqId -> Optional.empty());
        context.register(FastaHeaderProvider.class, provider);
        TestUtils.injectContext(validation, context);

        segment("gene1", 900, 1000);
        segment("gene1", 1, 200);

        assertThrows(ValidationException.class, () -> validation.validateJoinedLocationOrder(annotation, 1));
    }

    // ---- builders ------------------------------------------------------------

    private void injectTopology(String topology) {
        FastaHeader header = new FastaHeader();
        header.setTopology(topology);
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(seqId -> ACCESSION.equals(seqId) ? Optional.of(header) : Optional.empty());
        ValidationContext context = TestUtils.createTestContext();
        context.register(FastaHeaderProvider.class, provider);
        TestUtils.injectContext(validation, context);
    }

    private GFF3Feature segment(String id, long start, long end) {
        return segmentOnStrand(id, start, end, PLUS);
    }

    private GFF3Feature segmentOnStrand(String id, long start, long end, String strand) {
        return add(build(GENE_FEATURE, id, ACCESSION, start, end, strand));
    }

    private GFF3Feature add(GFF3Feature feature) {
        annotation.addFeature(feature);
        return feature;
    }

    private static GFF3Feature build(String name, String id, String seqId, long start, long end, String strand) {
        return new GFF3Feature(
                Optional.ofNullable(id),
                Optional.empty(),
                seqId,
                Optional.empty(),
                ".",
                name,
                start,
                end,
                ".",
                strand,
                "");
    }
}
