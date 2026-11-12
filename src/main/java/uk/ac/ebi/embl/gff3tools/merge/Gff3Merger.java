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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Merges N GFF3 documents into a single stream using a 4-phase algorithm:
 *
 * <ol>
 *   <li>Write one {@code ##gff-version} header.
 *   <li>For each source: stream annotation bytes (skipping its own {@code ##gff-version} line).
 *   <li>Write one {@code ##FASTA} directive.
 *   <li>Write buffered FASTA bytes from every source that had a FASTA section.
 * </ol>
 *
 * <p>FASTA bytes are held in memory during annotation streaming. This is acceptable because
 * protein translations are small (typically kilobytes per file). Very large assemblies should use
 * temp-file buffering; that is a future optimisation.
 */
@Slf4j
public class Gff3Merger {

    private static final byte[] VERSION_HEADER = "##gff-version 3.1.26\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FASTA_DIRECTIVE = "##FASTA\n".getBytes(StandardCharsets.UTF_8);
    private static final int COPY_BUFFER_SIZE = 256 * 1024;

    /**
     * Merges the given sources into {@code out}. Sources are processed in list order.
     *
     * @param sources the GFF3 documents to merge; must not be empty
     * @param out     destination stream; not closed by this method
     * @throws IllegalArgumentException if {@code sources} is empty
     * @throws IOException              on any I/O failure
     */
    public void merge(List<? extends Gff3MergeSource> sources, OutputStream out) throws IOException {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No GFF3 sources to merge");
        }

        // Phase 1 + 2: write single header, then annotation bytes from each source
        out.write(VERSION_HEADER);

        List<byte[]> fastaBuffers = new ArrayList<>(sources.size());
        byte[] buf = new byte[COPY_BUFFER_SIZE];

        for (Gff3MergeSource source : sources) {
            log.debug("Merging annotation section of: {}", source.getObjectId());
            long fastaOffset = source.getFastaOffset();
            try (InputStream in = source.openStream()) {
                long versionLineBytes = skipFirstLine(in);

                if (fastaOffset < 0) {
                    // No FASTA section: stream all remaining bytes as annotations
                    copy(in, out, buf);
                    fastaBuffers.add(new byte[0]);
                } else {
                    long annotationBytes = fastaOffset - versionLineBytes;
                    copyExact(in, out, annotationBytes, buf);
                    // Skip "##FASTA\n" in the source (it will be written once below)
                    skipFirstLine(in);
                    // Buffer the FASTA sequences
                    ByteArrayOutputStream fastaBuf = new ByteArrayOutputStream();
                    copy(in, fastaBuf, buf);
                    fastaBuffers.add(fastaBuf.toByteArray());
                }
            }
        }

        // Phase 3 + 4: write one ##FASTA directive, then all buffered FASTA bytes
        boolean anyFasta = fastaBuffers.stream().anyMatch(b -> b.length > 0);
        if (anyFasta) {
            out.write(FASTA_DIRECTIVE);
            for (byte[] fasta : fastaBuffers) {
                if (fasta.length > 0) {
                    out.write(fasta);
                }
            }
        }
    }

    /** Reads bytes from {@code in} until (and including) the next {@code '\n'}. Returns bytes read. */
    private long skipFirstLine(InputStream in) throws IOException {
        long count = 0;
        int b;
        while ((b = in.read()) != -1) {
            count++;
            if (b == '\n') break;
        }
        return count;
    }

    /** Copies exactly {@code limit} bytes from {@code in} to {@code out}. */
    private void copyExact(InputStream in, OutputStream out, long limit, byte[] buf) throws IOException {
        long remaining = limit;
        while (remaining > 0) {
            int toRead = (int) Math.min(remaining, buf.length);
            int read = in.read(buf, 0, toRead);
            if (read == -1) break;
            out.write(buf, 0, read);
            remaining -= read;
        }
    }

    /** Copies all remaining bytes from {@code in} to {@code out}. */
    private void copy(InputStream in, OutputStream out, byte[] buf) throws IOException {
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
    }
}
