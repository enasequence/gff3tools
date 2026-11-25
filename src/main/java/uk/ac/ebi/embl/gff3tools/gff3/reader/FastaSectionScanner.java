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

import java.nio.charset.StandardCharsets;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Directives;

/**
 * Core scanner that detects the byte offset of the {@code ##FASTA} directive in a GFF3 byte
 * stream. Shared by {@link FastaSectionLocator#locate(java.io.InputStream)} and
 * {@link FastaSectionInputStream}.
 *
 * <p>Feed bytes one at a time via {@link #scan(byte)}. The scanner uses a line-skip optimisation:
 * once a line is known not to be {@code ##FASTA} (first byte is not {@code #}, or a mid-match
 * mismatch occurs), remaining bytes on that line are skipped without comparison. Only lines
 * starting with {@code #} are examined character by character.
 *
 * <p>Once {@link #getFastaOffset()} returns a value {@code >= 0}, the marker has been found and
 * further {@link #scan} calls are no-ops.
 */
final class FastaSectionScanner {

    private static final byte[] MARKER = GFF3Directives.FASTA.getBytes(StandardCharsets.UTF_8);

    private long bytesRead = 0;
    private long fastaOffset = -1;

    private int matchLen = 0;
    private long markerStart = -1;
    private boolean atLineStart = true;
    // Once a line is known not to be ##FASTA, skip bytes until the next \n.
    private boolean skipping = false;

    /** Returns the byte offset of {@code ##FASTA}, or {@code -1} if not yet found. */
    long getFastaOffset() {
        return fastaOffset;
    }

    /** Returns {@code true} once the marker has been found — further {@link #scan} calls are no-ops. */
    boolean found() {
        return fastaOffset >= 0;
    }

    void scan(byte b) {
        if (fastaOffset >= 0) {
            bytesRead++;
            return;
        }

        int u = b & 0xFF;

        if (skipping) {
            if (u == '\n') {
                skipping = false;
                atLineStart = true;
            }
            bytesRead++;
            return;
        }

        if (matchLen == 0) {
            if (atLineStart && u == (MARKER[0] & 0xFF)) {
                markerStart = bytesRead;
                matchLen = 1;
            } else if (atLineStart) {
                // First byte of this line is not '#' — the whole line can't be ##FASTA.
                skipping = true;
            }
        } else if (u == (MARKER[matchLen] & 0xFF)) {
            if (++matchLen == MARKER.length) fastaOffset = markerStart;
        } else {
            // Mismatch mid-match — rest of this line is irrelevant.
            matchLen = 0;
            markerStart = -1;
            skipping = true;
        }
        atLineStart = (u == '\n');
        bytesRead++;
    }
}
