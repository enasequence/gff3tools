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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Directives;

/**
 * Merges N GFF3 documents into a single stream using a 4-phase algorithm:
 *
 * <ol>
 *   <li>Write one {@code ##gff-version} header.
 *   <li>For each source: stream annotation bytes directly to {@code out}.
 *   <li>Write one {@code ##FASTA} directive.
 *   <li>Stream FASTA bytes from the temp file to {@code out}.
 * </ol>
 *
 * <p>FASTA sections from all sources are spooled to a single temp file during the annotation
 * phase. This keeps JVM heap usage constant — one 256 KB copy buffer — regardless of the number
 * of concurrent merge requests or the size of the FASTA sections. The temp file is always deleted
 * in a {@code finally} block, even when writing fails mid-stream.
 *
 * <p>Each call to {@link #merge} creates its own temp file, so concurrent calls on different
 * threads never share state.
 */
@Slf4j
public class Gff3Merger {

    private static final byte[] VERSION_HEADER = GFF3Directives.VERSION_LINE.getBytes(StandardCharsets.UTF_8);
    private static final byte[] FASTA_DIRECTIVE = GFF3Directives.FASTA_LINE.getBytes(StandardCharsets.UTF_8);
    private static final byte[] FASTA_MARKER = GFF3Directives.FASTA.getBytes(StandardCharsets.UTF_8);
    private static final int COPY_BUFFER_SIZE = 256 * 1024;
    private static final ThreadLocal<byte[]> COPY_BUFFER = ThreadLocal.withInitial(() -> new byte[COPY_BUFFER_SIZE]);

    /**
     * Merges the given sources into {@code out}. Sources are processed in list order.
     *
     * <p>A temp file is created for the duration of this call and deleted before returning,
     * whether the merge succeeds or fails.
     *
     * @param sources the GFF3 documents to merge; must not be empty
     * @param out     destination stream; not closed by this method
     * @throws IllegalArgumentException if {@code sources} is empty
     * @throws IOException              on any I/O failure, including temp-file creation
     */
    public void merge(List<? extends Gff3MergeSource> sources, OutputStream out) throws IOException {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No GFF3 sources to merge");
        }

        Path fastaTemp = Files.createTempFile("gff3-merge-fasta-", ".tmp");
        log.debug("Created FASTA spool file: {}", fastaTemp);

        try {
            byte[] buf = COPY_BUFFER.get();
            boolean anyFasta = false;

            out.write(VERSION_HEADER);

            // Phase 1 + 2: stream annotations; spool FASTA sections to temp file
            try (OutputStream fastaOut = Files.newOutputStream(fastaTemp)) {
                for (Gff3MergeSource source : sources) {
                    log.debug("Merging annotation section of: {}", source.getObjectId());
                    long fastaOffset = source.getFastaOffset();
                    try (InputStream in = source.openStream()) {
                        long versionLineBytes = skipVersionLine(in, source.getObjectId());
                        if (fastaOffset < 0) {
                            copy(in, out, buf);
                        } else {
                            long annotationBytes = fastaOffset - versionLineBytes;
                            copyExact(in, out, annotationBytes, buf);
                            verifyAndSkipFastaDirective(in, source.getObjectId(), fastaOffset);
                            copy(in, fastaOut, buf);
                            anyFasta = true;
                        }
                    }
                }
            }

            // Phase 3 + 4: write single ##FASTA directive then stream the spool file
            if (anyFasta) {
                out.write(FASTA_DIRECTIVE);
                try (InputStream fastaIn = Files.newInputStream(fastaTemp)) {
                    copy(fastaIn, out, buf);
                }
                log.debug("FASTA spool written ({} bytes)", Files.size(fastaTemp));
            }

        } finally {
            deleteTempFile(fastaTemp);
        }
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
            log.debug("Deleted FASTA spool file: {}", path);
        } catch (IOException e) {
            log.warn("Failed to delete FASTA spool file {}: {}", path, e.getMessage());
        }
    }

    /**
     * Skips leading blank lines then the {@code ##gff-version} line, returning total bytes
     * consumed. Throws if the first non-blank line is not a valid {@code ##gff-version 3[.x[.y]]}
     * directive — merging a file that lacks it would corrupt the output.
     */
    private long skipVersionLine(InputStream in, String objectId) throws IOException {
        long count = 0;
        StringBuilder line = new StringBuilder();
        int b;

        while ((b = in.read()) != -1) {
            count++;
            if (b == '\n') {
                String text = line.toString().strip();
                if (text.isEmpty()) {
                    line.setLength(0);
                    continue;
                }
                validateVersionDirective(text, objectId);
                return count;
            }
            line.append((char) b);
        }

        // EOF without trailing newline
        String text = line.toString().strip();
        if (!text.isEmpty()) {
            validateVersionDirective(text, objectId);
            return count;
        }

        throw new IOException("Missing ##gff-version directive in " + objectId);
    }

    private void validateVersionDirective(String text, String objectId) throws IOException {
        if (!GFF3Directives.VERSION_DIRECTIVE.matcher(text).matches()) {
            throw new IOException("Expected ##gff-version 3[.x[.y]] as first directive in " + objectId
                    + " but found: \"" + text + "\"");
        }
    }

    private void verifyAndSkipFastaDirective(InputStream in, String objectId, long fastaOffset) throws IOException {
        byte[] actual = new byte[FASTA_MARKER.length];
        int read = 0;
        while (read < actual.length) {
            int n = in.read(actual, read, actual.length - read);
            if (n == -1) break;
            read += n;
        }
        if (read < actual.length || !Arrays.equals(actual, FASTA_MARKER)) {
            throw new IOException("fastaOffset " + fastaOffset + " for " + objectId
                    + " does not point to ##FASTA — stored offset is corrupt");
        }
        int b;
        while ((b = in.read()) != -1 && b != '\n') {}
    }

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

    private void copy(InputStream in, OutputStream out, byte[] buf) throws IOException {
        int read;
        while ((read = in.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
    }
}
