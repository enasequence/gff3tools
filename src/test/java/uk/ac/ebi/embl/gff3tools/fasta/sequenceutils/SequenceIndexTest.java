package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SequenceIndex byte-range mapping and validation.
 */
public class SequenceIndexTest {

    // Helpers to build a sane fixture ----------------------------------------

    private LineEntry line(long baseStart, long baseEnd, long byteStart, long byteEndEx) {
        return new LineEntry(baseStart, baseEnd, byteStart, byteEndEx);
    }

    private SequenceIndex newIndexFixture() {
        List<LineEntry> lines = new ArrayList<>();
        // L1: 12 bases, bytes 100..111 (exclusive end 112)
        lines.add(line(1, 12, 100, 112));
        // L2: 10 bases, bytes 113..122 (exclusive end 123)
        lines.add(line(13, 22, 113, 123));
        // L3: 11 bases, bytes 124..134 (exclusive end 135)
        lines.add(line(23, 33, 124, 135));
        // first/last base bytes (inclusive): 100 and 134
        return new SequenceIndex(100, 134, lines);
    }

    // ------------------------------------------------------------------------

    @Test
    void totalBases_isEndOfLastLine() {
        SequenceIndex idx = newIndexFixture();
        assertEquals(33, idx.totalBases());
        assertEquals(100, idx.firstBaseByte);
        assertEquals(134, idx.lastBaseByte);
    }

    @Test
    void byteSpans_singleLine_inside() {
        SequenceIndex idx = newIndexFixture();

        // base 1..1 -> L1 offset 0 -> bytes [100,101)
        var spans = idx.byteSpansForBaseRange(1, 1);
        assertEquals(1, spans.size());
        assertEquals(100, spans.get(0).start);
        assertEquals(101, spans.get(0).endEx);

        // base 12..12 -> last byte of L1 -> [111,112)
        spans = idx.byteSpansForBaseRange(12, 12);
        assertEquals(1, spans.size());
        assertEquals(111, spans.get(0).start);
        assertEquals(112, spans.get(0).endEx);

        // base 15..18 -> within L2 (L2 baseStart=13 => offsets 2..5) -> [115,119)
        spans = idx.byteSpansForBaseRange(15, 18);
        assertEquals(1, spans.size());
        assertEquals(115, spans.get(0).start);
        assertEquals(119, spans.get(0).endEx);
    }

    @Test
    void byteSpans_acrossLines() {
        SequenceIndex idx = newIndexFixture();

        // base 5..17 crosses L1 (5..12) and L2 (13..17)
        var spans = idx.byteSpansForBaseRange(5, 17);
        assertEquals(2, spans.size());

        // L1 slice: offsetStart=4 => [104,112)
        assertEquals(104, spans.get(0).start);
        assertEquals(112, spans.get(0).endEx);

        // L2 slice: base 13..17 => offsets 0..4 => [113,118)
        assertEquals(113, spans.get(1).start);
        assertEquals(118, spans.get(1).endEx);
    }

    @Test
    void byteSpans_fullRange_allLines() {
        SequenceIndex idx = newIndexFixture();

        var spans = idx.byteSpansForBaseRange(1, 33);
        assertEquals(3, spans.size());

        assertEquals(100, spans.get(0).start); // full L1
        assertEquals(112, spans.get(0).endEx);

        assertEquals(113, spans.get(1).start); // full L2
        assertEquals(123, spans.get(1).endEx);

        assertEquals(124, spans.get(2).start); // full L3
        assertEquals(135, spans.get(2).endEx);
    }

    @Test
    void badRanges_throw() {
        SequenceIndex idx = newIndexFixture();

        assertThrows(IllegalArgumentException.class, () -> idx.byteSpansForBaseRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> idx.byteSpansForBaseRange(10, 9));
    }

    @Test
    void applyDeletion_currentImpl_throwsDueToUnmodifiable() { //TODO
        // As written, SequenceIndex stores lines as Collections.unmodifiableList(lines)
        // and applyDeletion() tries to mutate it via lines.clear() -> UnsupportedOperationException.
        SequenceIndex idx = newIndexFixture();
        assertThrows(UnsupportedOperationException.class, () -> idx.applyDeletion(3, 5));
    }
}
