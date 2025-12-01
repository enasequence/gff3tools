package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.ByteSpan;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.LineEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SequenceIndex {

    public long firstBaseByte;      // -1 if empty
    public long startNBasesCount;
    public long lastBaseByte;       // -1 if empty
    public long endNBasesCount;
    private final List<LineEntry> lines;

    public SequenceIndex(long firstBaseByte, long startNBasesCount,
                         long lastBaseByte, long endNBasesCount, List<LineEntry> lines) {
        this.firstBaseByte = firstBaseByte;
        this.startNBasesCount = startNBasesCount;
        this.lastBaseByte  = lastBaseByte;
        this.endNBasesCount = endNBasesCount;
        this.lines = new ArrayList<>(lines);
    }

    public List<LineEntry> linesView() { return Collections.unmodifiableList(lines); }

    public long totalBasesIncludingEdgeNBases() {
        if (lines.isEmpty()) return 0;
        return lines.get(lines.size() - 1).baseEnd;
    }

    public long totalBases() {
        long bases = totalBasesIncludingEdgeNBases() - endNBasesCount - startNBasesCount;
        return Math.max(0, bases);
    }

    public ByteSpan byteSpanForBaseRangeIncludingEdgeNBases(long fromBase, long toBase) {
        long total = totalBasesIncludingEdgeNBases();
        if (fromBase < 1 || toBase < fromBase || toBase > total) {
            throw new IllegalArgumentException("bad base range: " + fromBase + ".." + toBase);
        }
        int i = findLineByBase(fromBase);
        int j = findLineByBase(toBase);

        LineEntry from = lines.get(i);
        long offStart     = fromBase - from.baseStart;

        LineEntry to      = lines.get(j);
        long offEndIncl   = toBase - to.baseStart;

        long byteStart = from.byteStart + offStart;
        long byteEndEx = to.byteStart + offEndIncl + 1; // half-open

        return new ByteSpan(byteStart, byteEndEx);
    }


    public ByteSpan byteSpanForBaseRange(long fromBase, long toBase) {
        long trimmedTotal = totalBases();
        if (fromBase < 1 || toBase < fromBase || toBase > trimmedTotal) {
            throw new IllegalArgumentException("bad base range: " + fromBase + ".." + toBase);
        }
        long actualFromBase = startNBasesCount + fromBase;
        long actualToBase   = startNBasesCount + toBase;
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
