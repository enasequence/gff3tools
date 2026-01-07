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
package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

public class SequenceIndexTest {

    /**
     * Synthetic line layout (ASCII, 1 byte/base; '\n' not part of lines):
     *
     * Line1: bases 1..4  at bytes [100,104)  -> base->byte: 1:100, 2:101, 3:102, 4:103, '\n':104
     * Line2: bases 5..8  at bytes [105,109)  -> 5:105, 6:106, 7:107, 8:108, '\n':109
     * Line3: bases 9..12 at bytes [110,114)  -> 9:110, 10:111, 11:112, 12:113, '\n':114
     *
     * So:
     *  - first base byte = 100
     *  - last  base byte = 113
     *  - total bases including edge Ns = 12
     */
    private SequenceIndex buildIndex(long startN, long endN) {
        List<LineEntry> lines =
                List.of(new LineEntry(1, 4, 100, 104), new LineEntry(5, 8, 105, 109), new LineEntry(9, 12, 110, 114));
        return new SequenceIndex(
                /*firstBaseByte*/ 100,
                /*startNBasesCount*/ startN,
                /*lastBaseByte*/ 113,
                /*endNBasesCount*/ endN,
                lines,
                114); // random value i put in, no meaning rn
    }

    @Test
    void totalsIncludingAndTrimmed() {
        SequenceIndex idx = buildIndex(/*startN*/ 2, /*endN*/ 3);

        assertEquals(12, idx.totalBases(), "totalBasesIncludingEdgeNBases");
        assertEquals(7, idx.totalBasesExcludingEdgeNBases(), "trimmed totalBases");
    }

    @Test
    void byteSpanIncludingEdgesSameLine() {
        SequenceIndex idx = buildIndex(0, 0);

        // [from..to] = [2..4] -> bytes [101..103], endExclusive = 104
        ByteSpan s = idx.byteSpanForBaseRangeIncludingEdgeNBases(2, 4);

        assertEquals(101, s.start);
        assertEquals(104, s.endEx);
        assertEquals(3, s.length());
    }

    @Test
    void byteSpanIncludingEdgesCrossesNewline() {
        SequenceIndex idx = buildIndex(0, 0);

        // [2..5] crosses the newline between line1 and line2
        // start = base2@101, endEx = base5@105 + 1 = 106, newline at 104 is included
        ByteSpan s = idx.byteSpanForBaseRangeIncludingEdgeNBases(2, 5);

        assertEquals(101, s.start);
        assertEquals(106, s.endEx);
        assertEquals(5, s.length()); // 2,3,\n,5,exclusive end char
    }

    @Test
    void includingEdgesValidatesTotal() {
        SequenceIndex idx = buildIndex(0, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> idx.byteSpanForBaseRangeIncludingEdgeNBases(1, 13),
                "toBase beyond total (including Ns) should throw");
    }

    @Test
    void trimmedByteSpanMapsThroughStartN() {
        SequenceIndex idx = buildIndex(2, 3);
        assertEquals(7, idx.totalBasesExcludingEdgeNBases());

        ByteSpan s = idx.byteSpanForBaseRange(1, 3); // Ignore first 2 Ns, ignore last 3 Ns

        assertEquals(102, s.start);
        assertEquals(106, s.endEx);
        assertEquals(4, s.length()); // 3 bases + exclusive end
    }

    @Test
    void trimmedSpanCrossesMultipleLines() {
        SequenceIndex idx = buildIndex(2, 3); // trimmed total = 7 bases

        ByteSpan s = idx.byteSpanForBaseRange(4, 7);

        assertEquals(106, s.start);
        assertEquals(111, s.endEx);
        assertEquals(5, s.length());
    }

    @Test
    void trimmedValidatesRangeAgainstTrimmedTotal() {
        SequenceIndex idx = buildIndex(2, 3); // trimmed total = 7
        assertThrows(
                IllegalArgumentException.class,
                () -> idx.byteSpanForBaseRange(1, 8),
                "toBase beyond trimmed total should throw");
    }

    @Test
    void zeroEdgeNsBehaviorMatchesIncludingMethod() {
        SequenceIndex idx = buildIndex(0, 0); // no additional N bases

        ByteSpan a = idx.byteSpanForBaseRange(2, 5);
        ByteSpan b = idx.byteSpanForBaseRangeIncludingEdgeNBases(2, 5);

        assertEquals(b.start, a.start);
        assertEquals(b.endEx, a.endEx);
    }
}
