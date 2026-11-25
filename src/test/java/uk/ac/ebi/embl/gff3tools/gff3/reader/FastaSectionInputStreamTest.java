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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FastaSectionInputStreamTest {

    private static long consume(FastaSectionInputStream in) throws IOException {
        byte[] buf = new byte[256];
        while (in.read(buf) != -1) {}
        return in.getFastaOffset();
    }

    private static FastaSectionInputStream wrap(String content) {
        return new FastaSectionInputStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void noFastaReturnsMinusOne() throws IOException {
        long offset = consume(wrap("##gff-version 3\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n"));
        assertEquals(-1L, offset);
    }

    @Test
    void fastaOffsetMatchesAnnotationLength() throws IOException {
        String annotation = "##gff-version 3\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        String content = annotation + "##FASTA\n>seq1|g1\nMACKW\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;

        long offset = consume(wrap(content));
        assertEquals(expected, offset);
    }

    @Test
    void emptyStreamReturnsMinusOne() throws IOException {
        long offset = consume(wrap(""));
        assertEquals(-1L, offset);
    }

    @Test
    void markerOnFirstLine() throws IOException {
        String content = "##FASTA\n>seq1\nMACKW\n";
        long offset = consume(wrap(content));
        assertEquals(0L, offset);
    }

    @Test
    void markerSplitAcrossBuffers() throws IOException {
        StringBuilder sb = new StringBuilder("##gff-version 3\n");
        while ((sb.toString().getBytes(StandardCharsets.UTF_8).length + "##FA".length()) % 256 != 0) {
            sb.append("# comment\n");
        }
        String annotation = sb.toString();
        String content = annotation + "##FASTA\n>seq1\nMACKW\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;

        long offset = consume(wrap(content));
        assertEquals(expected, offset);
    }

    @Test
    void partialMarkerNotMatchedMidLine() throws IOException {
        String content = "##gff-version 3\nseq1\t.\t##FASTA\t1\t100\t.\t+\t.\tID=g1\n";
        long offset = consume(wrap(content));
        assertEquals(-1L, offset);
    }

    @Test
    void allBytesPassThroughUnmodified() throws IOException {
        String content = "##gff-version 3\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n##FASTA\n>s\nM\n";
        byte[] expected = content.getBytes(StandardCharsets.UTF_8);

        FastaSectionInputStream in = wrap(content);
        byte[] actual = in.readAllBytes();

        assertArrayEquals(expected, actual);
    }

    @Test
    void singleByteReadPathDetectsOffset() throws IOException {
        String annotation = "##gff-version 3\n";
        String content = annotation + "##FASTA\n>s\nM\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;

        FastaSectionInputStream in = wrap(content);
        while (in.read() != -1) {}

        assertEquals(expected, in.getFastaOffset());
    }

    @Test
    void multipleAnnotationLinesBeforeFasta() throws IOException {
        String annotation = "##gff-version 3\n"
                + "seqA\t.\tgene\t1\t100\t.\t+\t.\tID=gA\n"
                + "seqB\t.\tgene\t1\t200\t.\t+\t.\tID=gB\n";
        String content = annotation + "##FASTA\n>seqA|gA\nMAA\n>seqB|gB\nMKK\n";
        long expected = annotation.getBytes(StandardCharsets.UTF_8).length;

        assertEquals(expected, consume(wrap(content)));
    }
}
