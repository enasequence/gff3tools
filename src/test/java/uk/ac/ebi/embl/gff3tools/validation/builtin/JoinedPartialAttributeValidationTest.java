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
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

class JoinedPartialAttributeValidationTest {

    private static final String GENE_FEATURE = "gene";
    private static final String ACCESSION = "ACC123";
    private static final String PLUS = "+";
    private static final String MINUS = "-";
    private static final String START = "start";
    private static final String END = "end";

    private JoinedPartialAttributeValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new JoinedPartialAttributeValidation();
        annotation = new GFF3Annotation();
    }

    /** join(&lt;1..500,600..700,800..&gt;822): open where the join begins and ends. */
    @Test
    void partialOnTheTerminalSegmentsIsValid() {
        partialSegment("gene1", 1, 500, START);
        segment("gene1", 600, 700);
        partialSegment("gene1", 800, 822, END);

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    @Test
    void joinWithoutAnyPartialAttributeIsValid() {
        segment("gene1", 1, 500);
        segment("gene1", 600, 700);
        segment("gene1", 800, 822);

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    @Test
    void partialOnAnInnerSegmentIsReported() {
        partialSegment("gene1", 1, 500, START);
        partialSegment("gene1", 600, 700, START);
        partialSegment("gene1", 800, 822, END);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedPartialAttribute(annotation, 1));
        assertTrue(exception.getMessage().contains("Only the terminal segments"));
        assertTrue(exception.getMessage().contains("segment 600..700 carries a partial attribute"));
        assertTrue(exception.getMessage().contains("1..500 and 800..822"));
        assertTrue(exception.getMessage().contains("\"gene1\""));
        assertTrue(exception.getMessage().contains(ACCESSION));
    }

    /** Presence is what is judged, so the value the attribute names makes no difference. */
    @Test
    void partialOnAnInnerSegmentIsReportedWhicheverBoundaryItNames() {
        segment("gene1", 1, 500);
        partialSegment("gene1", 600, 700, END);
        segment("gene1", 800, 822);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedPartialAttribute(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 600..700 carries a partial attribute"));
    }

    /** Which boundary each terminal segment declares open is not this rule's business. */
    @Test
    void terminalSegmentsMayCarryEitherValue() {
        partialSegment("gene1", 1, 500, END);
        segment("gene1", 600, 700);
        partialSegment("gene1", 800, 822, START);

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    /**
     * The orphan location join: two segments are both terminal, so a join open at the junction
     * between them has no inner segment to report.
     */
    @Test
    void twoSegmentJoinOpenAtItsJunctionIsValid() {
        partialSegmentOnStrand("CDS", 109279, 109392, MINUS, END);
        partialSegmentOnStrand("CDS", 109953, 110048, MINUS, START);

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    @Test
    void singleSegmentFeatureIsSkipped() {
        partialSegment("gene1", 600, 700, START).addAttribute(GFF3Attributes.PARTIAL, END);

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    /** The join is read in file order, so the strand it lies on changes nothing. */
    @Test
    void minusStrandInnerPartialIsReported() {
        partialSegmentOnStrand("gene1", 1, 500, MINUS, START);
        partialSegmentOnStrand("gene1", 600, 700, MINUS, START);
        partialSegmentOnStrand("gene1", 800, 822, MINUS, END);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedPartialAttribute(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 600..700 carries a partial attribute"));
    }

    /** Features without an ID are separate features, not segments of one join. */
    @Test
    void featuresWithoutAnIdAreNotJudgedAsOneJoin() {
        add(build(GENE_FEATURE, null, ACCESSION, 1, 500, PLUS));
        add(build(GENE_FEATURE, null, ACCESSION, 600, 700, PLUS)).addAttribute(GFF3Attributes.PARTIAL, START);
        add(build(GENE_FEATURE, null, ACCESSION, 800, 822, PLUS));

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    @Test
    void unrelatedFeaturesAreNotJudgedAsOneJoin() {
        segment("gene1", 1, 500);
        partialSegment("gene2", 600, 700, START);
        segment("gene3", 800, 822);

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    /**
     * The rps12 case, join(complement(66903..&gt;67016),183994..184671,185444..&gt;185472): trans-splicing
     * orders the segments by biology rather than by coordinate, which leaves the first and last
     * lines carrying the transcript's open ends, exactly where this rule expects them.
     */
    @Test
    void transSplicedFeatureOpenAtItsTerminalSegmentsIsValid() {
        transSpliced(partialSegmentOnStrand("rps12", 66903, 67016, MINUS, END));
        transSpliced(segment("rps12", 183994, 184671));
        transSpliced(partialSegment("rps12", 185444, 185472, END));

        assertDoesNotThrow(() -> validation.validateJoinedPartialAttribute(annotation, 1));
    }

    /** Trans-splicing reorders segments; it does not license an open end inside the join. */
    @Test
    void transSplicedFeatureWithAnInnerPartialIsReported() {
        transSpliced(segment("rps12", 66903, 67016));
        transSpliced(partialSegment("rps12", 183994, 184671, END));
        transSpliced(segment("rps12", 185444, 185472));

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedPartialAttribute(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 183994..184671 carries a partial attribute"));
    }

    @Test
    void everyMisplacedPartialIsReportedTogether() {
        segment("geneA", 1, 500);
        partialSegment("geneA", 600, 700, START);
        partialSegment("geneA", 720, 780, END);
        segment("geneA", 800, 822);
        segment("geneB", 1000, 1500);
        partialSegment("geneB", 1600, 1700, START);
        segment("geneB", 1800, 1822);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateJoinedPartialAttribute(annotation, 1));
        assertTrue(exception.getMessage().contains("segment 600..700 carries a partial attribute"));
        assertTrue(exception.getMessage().contains("segment 720..780 carries a partial attribute"));
        assertTrue(exception.getMessage().contains("segment 1600..1700 carries a partial attribute"));
    }

    // ---- builders ------------------------------------------------------------

    private GFF3Feature segment(String id, long start, long end) {
        return add(build(GENE_FEATURE, id, ACCESSION, start, end, PLUS));
    }

    private GFF3Feature partialSegment(String id, long start, long end, String partial) {
        return partialSegmentOnStrand(id, start, end, PLUS, partial);
    }

    private GFF3Feature partialSegmentOnStrand(String id, long start, long end, String strand, String partial) {
        GFF3Feature feature = add(build(GENE_FEATURE, id, ACCESSION, start, end, strand));
        feature.addAttribute(GFF3Attributes.PARTIAL, partial);
        return feature;
    }

    private GFF3Feature transSpliced(GFF3Feature feature) {
        feature.addAttribute(GFF3Attributes.TRANS_SPLICING, "true");
        return feature;
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
