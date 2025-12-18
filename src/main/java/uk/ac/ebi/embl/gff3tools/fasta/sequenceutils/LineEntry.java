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

public final class LineEntry {
    public long baseStart; // 1-based, inclusive
    public long baseEnd; // 1-based, inclusive
    public long byteStart; // absolute byte offset of first base in this line
    public long byteEndExclusive; // absolute byte offset one past last base

    public LineEntry(long baseStart, long baseEnd, long byteStart, long byteEndExclusive) {
        this.baseStart = baseStart;
        this.baseEnd = baseEnd;
        this.byteStart = byteStart;
        this.byteEndExclusive = byteEndExclusive;
    }

    public long lengthBases() {
        return baseEnd - baseStart + 1;
    }

    public long lengthBytes() {
        return byteEndExclusive - byteStart;
    } // ASCII: same as bases
}
