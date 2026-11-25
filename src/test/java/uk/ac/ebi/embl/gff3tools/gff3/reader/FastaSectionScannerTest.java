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
package uk.ac.ebi.embl.gff3tools.gff3.reader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FastaSectionScannerTest {

    private static long feed(String content) {
        FastaSectionScanner scanner = new FastaSectionScanner();
        for (byte b : content.getBytes(StandardCharsets.UTF_8)) {
            scanner.scan(b);
        }
        return scanner.getFastaOffset();
    }

    // ── basic detection ──────────────────────────────────────────────────────

    @Test
    void emptyInputReturnsMinusOne() {
        assertEquals(-1L, feed(""));
    }

    @Test
    void noFastaMarkerReturnsMinusOne() {
        assertEquals(-1L, feed("##gff-version 3\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n"));
    }

    @Test
    void markerAtStartReturnsZero() {
        assertEquals(0L, feed("##FASTA\n>s\nM\n"));
    }

    @Test
    void markerAfterAnnotationReturnsCorrectOffset() {
        String annotation = "##gff-version 3\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expected, feed(annotation + "##FASTA\n>s\nM\n"));
    }

    // ── found() flag ─────────────────────────────────────────────────────────

    @Test
    void foundFalseBeforeMarker() {
        FastaSectionScanner scanner = new FastaSectionScanner();
        for (byte b : "##gff-version 3\n".getBytes(StandardCharsets.UTF_8)) {
            scanner.scan(b);
        }
        assertFalse(scanner.found());
    }

    @Test
    void foundTrueAfterMarker() {
        FastaSectionScanner scanner = new FastaSectionScanner();
        for (byte b : "##FASTA\n".getBytes(StandardCharsets.UTF_8)) {
            scanner.scan(b);
        }
        assertTrue(scanner.found());
    }

    @Test
    void scanIsNoOpAfterFound() {
        FastaSectionScanner scanner = new FastaSectionScanner();
        for (byte b : "##FASTA\n>s\nM\n".getBytes(StandardCharsets.UTF_8)) {
            scanner.scan(b);
        }
        long offsetAfterFullScan = scanner.getFastaOffset();
        // feed more bytes — offset must not change
        scanner.scan((byte) 'X');
        scanner.scan((byte) 'Y');
        assertEquals(offsetAfterFullScan, scanner.getFastaOffset());
    }

    // ── line-skip optimisation ────────────────────────────────────────────────

    @Test
    void markerMidLineIsIgnored() {
        // ##FASTA appears inside a tab-separated field, not at a line start
        assertEquals(-1L, feed("seq1\t.\t##FASTA\t1\t100\t.\t+\t.\tID=g1\n"));
    }

    @Test
    void partialMatchThenMismatchSkipsLine() {
        // ##gff starts with ## like ##FASTA but diverges at 'g' vs 'F'
        // the rest of the line must be skipped, not re-scanned
        String annotation = "##gff-version 3\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expected, feed(annotation + "##FASTA\n>s\nM\n"));
    }

    @Test
    void nonHashLineStartSkipsEntireLine() {
        // sequence lines (s, A-Z) start with non-'#' — scanner should skip them
        String annotation = "##gff-version 3\nseqA\t.\tgene\t1\t50\t.\t+\t.\tID=gA\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expected, feed(annotation + "##FASTA\n>seqA\nMAA\n"));
    }

    @Test
    void multipleAnnotationLinesSkippedCorrectly() {
        String annotation = "##gff-version 3\n"
                + "seqA\t.\tgene\t1\t100\t.\t+\t.\tID=gA\n"
                + "seqB\t.\tgene\t1\t200\t.\t+\t.\tID=gB\n"
                + "seqC\t.\tgene\t1\t300\t.\t+\t.\tID=gC\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expected, feed(annotation + "##FASTA\n>seqA\nMAA\n"));
    }

    // ── marker split across buffer-like boundaries ────────────────────────────

    @Test
    void markerDetectedWhenFedOneByteAtATime() {
        String annotation = "##gff-version 3\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;
        FastaSectionScanner scanner = new FastaSectionScanner();
        for (byte b : (annotation + "##FASTA\n>s\nM\n").getBytes(StandardCharsets.UTF_8)) {
            scanner.scan(b);
        }
        assertEquals(expected, scanner.getFastaOffset());
    }

    @Test
    void markerDetectedWhenSplitAtEveryPosition() {
        String annotation = "##gff-version 3\n";
        String full = annotation + "##FASTA\n>s\nM\n";
        byte[] bytes = full.getBytes(StandardCharsets.UTF_8);
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;

        // Feed in two chunks split at every possible position within ##FASTA
        for (int split = 0; split < bytes.length; split++) {
            FastaSectionScanner scanner = new FastaSectionScanner();
            for (byte b : bytes) scanner.scan(b);
            assertEquals(expected, scanner.getFastaOffset(), "Failed at split position " + split);
        }
    }
}
