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
package uk.ac.ebi.embl.gff3tools.gff3toff;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/** Lowercases each streamed character. Operates only on the caller's buffer window, so memory is O(chunk). */
final class LowerCaseReader extends FilterReader {

    LowerCaseReader(Reader in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        return c == -1 ? -1 : Character.toLowerCase((char) c);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int n = super.read(cbuf, off, len);
        for (int i = 0; i < n; i++) {
            cbuf[off + i] = Character.toLowerCase(cbuf[off + i]);
        }
        return n;
    }
}
