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
package uk.ac.ebi.embl.gff3tools.merge;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class Gff3MergerTest {

    // --- helpers ---

    private static Gff3MergeSource source(String objectId, String content, long fastaOffset) {
        return new Gff3MergeSource() {
            @Override
            public String getObjectId() {
                return objectId;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public long getFastaOffset() {
                return fastaOffset;
            }
        };
    }

    private static String merge(List<? extends Gff3MergeSource> sources) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Gff3Merger().merge(sources, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    // --- correctness ---

    @Test
    void singleSourceWithNoFasta() throws IOException {
        String content = "##gff-version 3.1.26\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        String result = merge(List.of(source("obj1", content, -1L)));

        assertTrue(result.startsWith("##gff-version"));
        assertTrue(result.contains("ID=g1"));
        assertFalse(result.contains("##FASTA"));
    }

    @Test
    void singleSourceWithFasta() throws IOException {
        String annotation = "##gff-version 3.1.26\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        String fasta = "##FASTA\n>seq1|g1\nMACKW\n";
        String content = annotation + fasta;
        long fastaOffset = annotation.getBytes(StandardCharsets.UTF_8).length;

        String result = merge(List.of(source("obj1", content, fastaOffset)));

        assertTrue(result.contains("ID=g1"));
        assertTrue(result.contains("##FASTA"));
        assertTrue(result.contains(">seq1|g1"));
        assertTrue(result.contains("MACKW"));
    }

    @Test
    void twoSourcesAnnotationsBeforeFasta() throws IOException {
        String annoA = "##gff-version 3.1.26\nseqA\t.\tgene\t1\t100\t.\t+\t.\tID=gA\n";
        String fastaA = "##FASTA\n>seqA|gA\nMAA\n";
        String contentA = annoA + fastaA;

        String annoB = "##gff-version 3.1.26\nseqB\t.\tgene\t1\t200\t.\t+\t.\tID=gB\n";
        String fastaB = "##FASTA\n>seqB|gB\nMKK\n";
        String contentB = annoB + fastaB;

        long offsetA = annoA.getBytes(StandardCharsets.UTF_8).length;
        long offsetB = annoB.getBytes(StandardCharsets.UTF_8).length;

        String result = merge(List.of(source("A", contentA, offsetA), source("B", contentB, offsetB)));

        // single ##gff-version at top
        assertEquals(1, countOccurrences(result, "##gff-version"));

        // both annotations present
        assertTrue(result.contains("ID=gA"));
        assertTrue(result.contains("ID=gB"));

        // single ##FASTA directive
        assertEquals(1, countOccurrences(result, "##FASTA"));

        // all annotations precede ##FASTA
        int fastaPos = result.indexOf("##FASTA");
        assertTrue(result.indexOf("ID=gA") < fastaPos);
        assertTrue(result.indexOf("ID=gB") < fastaPos);

        // both translations present after ##FASTA
        assertTrue(result.indexOf(">seqA|gA") > fastaPos);
        assertTrue(result.indexOf(">seqB|gB") > fastaPos);
    }

    @Test
    void mixedSourcesOnlyOneHasFasta() throws IOException {
        String contentA = "##gff-version 3.1.26\nseqA\t.\tgene\t1\t100\t.\t+\t.\tID=gA\n";
        String annoB = "##gff-version 3.1.26\nseqB\t.\tgene\t1\t200\t.\t+\t.\tID=gB\n";
        String contentB = annoB + "##FASTA\n>seqB|gB\nMKK\n";
        long offsetB = annoB.getBytes(StandardCharsets.UTF_8).length;

        String result = merge(List.of(source("A", contentA, -1L), source("B", contentB, offsetB)));

        assertTrue(result.contains("ID=gA"));
        assertTrue(result.contains("ID=gB"));
        assertEquals(1, countOccurrences(result, "##FASTA"));
        assertTrue(result.contains(">seqB|gB"));
    }

    @Test
    void noSourcesThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> merge(List.of()));
    }

    @Test
    void missingVersionLineThrowsIOException() {
        String content = "seq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        assertThrows(IOException.class, () -> merge(List.of(source("obj1", content, -1L))));
    }

    @Test
    void bareGffVersionWithNoNumberThrowsIOException() {
        // ##gff-version with no version number is malformed
        String content = "##gff-version\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        assertThrows(IOException.class, () -> merge(List.of(source("obj1", content, -1L))));
    }

    @Test
    void emptyStreamThrowsIOException() {
        assertThrows(IOException.class, () -> merge(List.of(source("obj1", "", -1L))));
    }

    @Test
    void versionLineWithNoTrailingNewlineIsAccepted() throws IOException {
        // EOF immediately after ##gff-version line with no trailing \n
        String content = "##gff-version 3.1.26";
        String result = merge(List.of(source("obj1", content, -1L)));
        assertTrue(result.startsWith("##gff-version"));
    }

    @Test
    void blankLinesBeforeVersionLineAreSkipped() throws IOException {
        // leading blank lines are allowed before ##gff-version
        String content = "\n\n##gff-version 3.1.26\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n";
        String result = merge(List.of(source("obj1", content, -1L)));

        assertEquals(1, countOccurrences(result, "##gff-version"));
        assertTrue(result.contains("ID=g1"));
    }

    // --- temp file cleanup ---

    @Test
    void tempFileIsDeletedAfterSuccessfulMerge() throws IOException {
        Path tmpDir = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"));
        long beforeCount = countTempFiles(tmpDir);

        String content = "##gff-version 3.1.26\nseq1\t.\tgene\t1\t100\t.\t+\t.\tID=g1\n" + "##FASTA\n>seq1|g1\nMACKW\n";
        long offset = content.indexOf("##FASTA");
        merge(List.of(source("obj1", content, offset)));

        long afterCount = countTempFiles(tmpDir);
        assertEquals(beforeCount, afterCount, "Temp file must be deleted after successful merge");
    }

    @Test
    void tempFileIsDeletedEvenWhenMergeFails() {
        Path tmpDir = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"));

        // source that throws on openStream to simulate mid-merge failure
        Gff3MergeSource failingSource = new Gff3MergeSource() {
            @Override
            public String getObjectId() {
                return "fail";
            }

            @Override
            public InputStream openStream() throws IOException {
                throw new IOException("simulated GridFS failure");
            }

            @Override
            public long getFastaOffset() {
                return -1L;
            }
        };

        long beforeCount = countTempFiles(tmpDir);
        assertThrows(IOException.class, () -> merge(List.of(failingSource)));
        long afterCount = countTempFiles(tmpDir);

        assertEquals(beforeCount, afterCount, "Temp file must be deleted even when merge fails");
    }

    // --- concurrency ---

    @Test
    void concurrentMergesDoNotInterfere() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                startLatch.await();
                String anno = "##gff-version 3.1.26\nseq" + idx + "\t.\tgene\t1\t100\t.\t+\t.\tID=g" + idx + "\n";
                String content = anno + "##FASTA\n>seq" + idx + "|g" + idx + "\nMACKW\n";
                long offset = anno.getBytes(StandardCharsets.UTF_8).length;
                return merge(List.of(source("obj" + idx, content, offset)));
            }));
        }

        startLatch.countDown();
        pool.shutdown();

        for (int i = 0; i < threads; i++) {
            String result = futures.get(i).get();
            assertTrue(result.contains("ID=g" + i), "Thread " + i + " must see its own annotation");
            assertTrue(result.contains(">seq" + i + "|g" + i), "Thread " + i + " must see its own FASTA");
            assertEquals(1, countOccurrences(result, "##FASTA"), "Thread " + i + " must have exactly one ##FASTA");
        }
    }

    // --- helpers ---

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }

    private static long countTempFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "gff3-merge-fasta-*.tmp")) {
            long count = 0;
            for (@SuppressWarnings("unused") Path ignored : stream) count++;
            return count;
        } catch (IOException e) {
            return 0;
        }
    }
}
