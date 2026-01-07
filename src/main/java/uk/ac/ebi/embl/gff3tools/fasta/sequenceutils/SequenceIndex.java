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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SequenceIndex {

    public long firstBaseByte; // -1 if empty
    public long startNBasesCount;
    public long lastBaseByte; // -1 if empty
    public long endNBasesCount;
    private final List<LineEntry> lines;
    public long nextHeaderByte; // byte offset of next '>' at line start, or fileSize (EOF)

    public SequenceIndex(
            long firstBaseByte,
            long startNBasesCount,
            long lastBaseByte,
            long endNBasesCount,
            List<LineEntry> lines,
            long nextHeader) {
        this.firstBaseByte = firstBaseByte;
        this.startNBasesCount = startNBasesCount;
        this.lastBaseByte = lastBaseByte;
        this.endNBasesCount = endNBasesCount;
        this.lines = new ArrayList<>(lines);
        this.nextHeaderByte = nextHeader;
    }

    public List<LineEntry> linesView() {
        return Collections.unmodifiableList(lines);
    }

    public long totalBases() {
        if (lines.isEmpty()) return 0;
        return lines.get(lines.size() - 1).baseEnd;
    }

    public long totalBasesExcludingEdgeNBases() {
        long bases = totalBases() - endNBasesCount - startNBasesCount;
        return Math.max(0, bases);
    }

    public ByteSpan byteSpanForBaseRangeIncludingEdgeNBases(long fromBase, long toBase) {
        long total = totalBases();
        if (fromBase < 1 || toBase < fromBase || toBase > total) {
            throw new IllegalArgumentException("bad base range: " + fromBase + ".." + toBase);
        }
        int i = findLineByBase(fromBase);
        int j = findLineByBase(toBase);

        LineEntry from = lines.get(i);
        long offStart = fromBase - from.baseStart;

        LineEntry to = lines.get(j);
        long offEndIncl = toBase - to.baseStart;

        long byteStart = from.byteStart + offStart;
        long byteEndEx = to.byteStart + offEndIncl + 1; // half-open

        return new ByteSpan(byteStart, byteEndEx);
    }

    public ByteSpan byteSpanForBaseRange(long fromBase, long toBase) {
        long trimmedTotal = totalBasesExcludingEdgeNBases();
        if (fromBase < 1 || toBase < fromBase || toBase > trimmedTotal) {
            throw new IllegalArgumentException("bad base range: " + fromBase + ".." + toBase);
        }
        long actualFromBase = startNBasesCount + fromBase;
        long actualToBase = startNBasesCount + toBase;
        return byteSpanForBaseRangeIncludingEdgeNBases(actualFromBase, actualToBase);
    }

    private int findLineByBase(long base) {
        int lo = 0, hi = lines.size() - 1, ans = hi;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            LineEntry L = lines.get(mid);
            if (base < L.baseStart) hi = mid - 1;
            else if (base > L.baseEnd) lo = mid + 1;
            else return mid;
            ans = lo;
        }
        return Math.max(0, Math.min(ans, lines.size() - 1));
    }
}
