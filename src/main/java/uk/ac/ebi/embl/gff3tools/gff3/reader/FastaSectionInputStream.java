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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link FilterInputStream} that detects the byte offset of the {@code ##FASTA} directive
 * as bytes flow through to the caller — no extra pass, no temp file, constant heap.
 *
 * <p>Pairs with {@link FastaSectionLocator}: use {@code FastaSectionLocator} when the file is
 * already on disk (binary search, O(log n)), and this class when the bytes arrive as a stream
 * (scanning piggybacked on the existing read via {@link FastaSectionScanner}, zero extra I/O).
 * Lines that cannot be {@code ##FASTA} are skipped — only {@code #}-prefixed lines are examined
 * character by character.
 *
 * <pre>{@code
 * FastaSectionInputStream wrapped = new FastaSectionInputStream(file.getInputStream());
 * gridFsTemplate.store(wrapped, filename, contentType);
 * long fastaOffset = wrapped.getFastaOffset();  // computed for free during the store
 * }</pre>
 *
 * <p>Not thread-safe; use one instance per upload.
 */
public class FastaSectionInputStream extends FilterInputStream {

    private final FastaSectionScanner scanner = new FastaSectionScanner();

    public FastaSectionInputStream(InputStream in) {
        super(in);
    }

    /**
     * Returns the byte offset of the {@code ##FASTA} directive, or {@code -1} if not found.
     * Only meaningful after the stream has been fully consumed.
     */
    public long getFastaOffset() {
        return scanner.getFastaOffset();
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) scanner.scan((byte) b);
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = super.read(buf, off, len);
        if (n > 0) {
            for (int i = off; i < off + n; i++) scanner.scan(buf[i]);
        }
        return n;
    }
}
