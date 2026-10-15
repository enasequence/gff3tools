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
import uk.ac.ebi.embl.gff3tools.utils.TopologyUtils.Span;

class TopologyUtilsTest {

    private static final long LENGTH = 1000;

    @Test
    void pathWithoutOriginIsUnchanged() {
        List<Span> path = List.of(new Span(100, 200), new Span(300, 400));
        assertEquals(path, TopologyUtils.toVirtualSpace(path, LENGTH));
    }

    @Test
    void piecesPastTheOriginMoveIntoVirtualSpace() {
        List<Span> path = List.of(new Span(801, 850), new Span(901, 1000), new Span(1, 100), new Span(151, 200));
        assertEquals(
                List.of(new Span(801, 850), new Span(901, 1000), new Span(1001, 1100), new Span(1151, 1200)),
                TopologyUtils.toVirtualSpace(path, LENGTH));
    }

    @Test
    void gapAfterTheOriginKeepsSeparateSpans() {
        List<Span> path = List.of(new Span(851, 900), new Span(51, 100));
        List<Span> virtual = TopologyUtils.toVirtualSpace(path, LENGTH);

        assertEquals(List.of(new Span(851, 900), new Span(1051, 1100)), virtual);
        assertFalse(TopologyUtils.continuesAcrossOrigin(virtual.get(0), virtual.get(1), LENGTH));
    }

    @Test
    void stretchThroughTheOriginContinuesAcrossIt() {
        List<Span> virtual = TopologyUtils.toVirtualSpace(List.of(new Span(901, 1000), new Span(1, 100)), LENGTH);

        assertEquals(List.of(new Span(901, 1000), new Span(1001, 1100)), virtual);
        assertTrue(TopologyUtils.continuesAcrossOrigin(virtual.get(0), virtual.get(1), LENGTH));
    }

    @Test
    void secondDropLeavesThePathUnchanged() {
        List<Span> path = List.of(new Span(901, 1000), new Span(1, 100), new Span(500, 600), new Span(300, 400));
        assertEquals(path, TopologyUtils.toVirtualSpace(path, LENGTH));
    }

    @Test
    void touchingSpansAwayFromTheOriginAreNotAcrossIt() {
        assertFalse(TopologyUtils.continuesAcrossOrigin(new Span(100, 200), new Span(201, 300), LENGTH));
    }
}
