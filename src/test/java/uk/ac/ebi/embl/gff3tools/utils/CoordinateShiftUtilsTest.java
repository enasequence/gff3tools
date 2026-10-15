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
package uk.ac.ebi.embl.gff3tools.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.utils.CoordinateShiftUtils.Span;

class CoordinateShiftUtilsTest {

    private static final long LENGTH = 1000;

    @Test
    void pathWithoutOriginIsUnchanged() {
        List<Span> path = List.of(new Span(100, 200), new Span(300, 400));
        assertEquals(path, CoordinateShiftUtils.toVirtualSpace(path, LENGTH));
    }

    @Test
    void piecesPastTheOriginMoveIntoVirtualSpace() {
        List<Span> path = List.of(new Span(801, 850), new Span(901, 1000), new Span(1, 100), new Span(151, 200));
        assertEquals(
                List.of(new Span(801, 850), new Span(901, 1000), new Span(1001, 1100), new Span(1151, 1200)),
                CoordinateShiftUtils.toVirtualSpace(path, LENGTH));
    }

    @Test
    void gapAfterTheOriginKeepsSeparateSpans() {
        List<Span> path = List.of(new Span(851, 900), new Span(51, 100));
        List<Span> virtual = CoordinateShiftUtils.toVirtualSpace(path, LENGTH);

        assertEquals(List.of(new Span(851, 900), new Span(1051, 1100)), virtual);
        assertFalse(CoordinateShiftUtils.continuesAcrossOrigin(virtual.get(0), virtual.get(1), LENGTH));
    }

    @Test
    void stretchThroughTheOriginContinuesAcrossIt() {
        List<Span> virtual =
                CoordinateShiftUtils.toVirtualSpace(List.of(new Span(901, 1000), new Span(1, 100)), LENGTH);

        assertEquals(List.of(new Span(901, 1000), new Span(1001, 1100)), virtual);
        assertTrue(CoordinateShiftUtils.continuesAcrossOrigin(virtual.get(0), virtual.get(1), LENGTH));
    }

    @Test
    void secondDropLeavesThePathUnchanged() {
        List<Span> path = List.of(new Span(901, 1000), new Span(1, 100), new Span(500, 600), new Span(300, 400));
        assertEquals(path, CoordinateShiftUtils.toVirtualSpace(path, LENGTH));
    }

    @Test
    void touchingSpansAwayFromTheOriginAreNotAcrossIt() {
        assertFalse(CoordinateShiftUtils.continuesAcrossOrigin(new Span(100, 200), new Span(201, 300), LENGTH));
    }

    @Test
    void flatFilePositionPastTheOriginIsShiftedBack() {
        assertEquals("10..12", CoordinateShiftUtils.toFlatFilePosition("1010..1012", LENGTH));
        assertEquals("10", CoordinateShiftUtils.toFlatFilePosition("1010", LENGTH));
    }

    @Test
    void flatFilePositionAcrossTheOriginBecomesAJoin() {
        assertEquals("join(999..1000,1)", CoordinateShiftUtils.toFlatFilePosition("999..1001", LENGTH));
        assertEquals(
                "complement(join(999..1000,1))",
                CoordinateShiftUtils.toFlatFilePosition("complement(999..1001)", LENGTH));
    }

    @Test
    void flatFilePositionWithinTheSequenceIsUnchanged() {
        assertEquals("10 .. 12", CoordinateShiftUtils.toFlatFilePosition("10 .. 12", LENGTH));
        assertEquals("complement(10..12)", CoordinateShiftUtils.toFlatFilePosition("complement(10..12)", LENGTH));
        assertEquals("not a position", CoordinateShiftUtils.toFlatFilePosition("not a position", LENGTH));
    }

    private static final List<Span> PIECES = List.of(new Span(951, 1000), new Span(1, 50));
    private static final List<Span> VIRTUAL_PIECES = List.of(new Span(951, 1000), new Span(1001, 1050));

    @Test
    void virtualPositionAfterTheOriginTakesItsPiecesShift() {
        assertEquals("1010..1012", CoordinateShiftUtils.toVirtualPosition("10..12", PIECES, VIRTUAL_PIECES));
        assertEquals(
                "complement(1010..1012)",
                CoordinateShiftUtils.toVirtualPosition("complement(10..12)", PIECES, VIRTUAL_PIECES));
    }

    @Test
    void virtualPositionBeforeTheOriginIsUnchanged() {
        assertEquals("960..962", CoordinateShiftUtils.toVirtualPosition("960..962", PIECES, VIRTUAL_PIECES));
    }

    @Test
    void virtualPositionAcrossTheOriginBecomesOneRange() {
        assertEquals("999..1001", CoordinateShiftUtils.toVirtualPosition("join(999..1000,1)", PIECES, VIRTUAL_PIECES));
        assertEquals(
                "complement(999..1001)",
                CoordinateShiftUtils.toVirtualPosition("complement(join(999..1000,1))", PIECES, VIRTUAL_PIECES));
    }

    @Test
    void virtualPositionOutsideTheFeatureIsUnchanged() {
        assertEquals("500..502", CoordinateShiftUtils.toVirtualPosition("500..502", PIECES, VIRTUAL_PIECES));
    }

    @Test
    void positionIsMappedInsideAnAttributeValue() {
        assertEquals(
                "(pos:join(999..1000,1),aa:Glu,seq:cgt)",
                CoordinateShiftUtils.mapPosition(
                        "(pos:999..1001,aa:Glu,seq:cgt)",
                        position -> CoordinateShiftUtils.toFlatFilePosition(position, LENGTH)));
        assertEquals("(aa:Glu)", CoordinateShiftUtils.mapPosition("(aa:Glu)", position -> "changed"));
    }
}
