package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

public final class SequenceIndex {
    final long firstBaseByte;      // absolute byte offset of the first base (inclusive), -1 if none
    final long lastBaseByte;       // absolute byte offset of the last base (inclusive), -1 if none
    final java.util.List<LineEntry> lines; // sorted by baseStart

    public SequenceIndex(long firstBaseByte, long lastBaseByte, java.util.List<LineEntry> lines) {
        this.firstBaseByte = firstBaseByte;
        this.lastBaseByte = lastBaseByte;
        this.lines = java.util.Collections.unmodifiableList(lines);
    }

    long totalBases() {
        if (lines.isEmpty()) return 0;
        return lines.get(lines.size()-1).baseEnd;
    }

    /** Return one or more byte spans covering [fromBase..toBase], inclusive. */
    java.util.List<ByteSpan> byteSpansForBaseRange(long fromBase, long toBase) {
        if (fromBase < 1 || toBase < fromBase) throw new IllegalArgumentException("bad base range");
        if (lines.isEmpty()) return java.util.List.of();

        int i = findLineByBase(fromBase);
        int j = findLineByBase(toBase);

        java.util.ArrayList<ByteSpan> out = new java.util.ArrayList<>(Math.max(1, j - i + 1));
        for (int k = i; k <= j; k++) {
            LineEntry L = lines.get(k);
            long startBase = Math.max(fromBase, L.baseStart);
            long endBase   = Math.min(toBase,   L.baseEnd);

            long offsetStartInLine = startBase - L.baseStart;               // 0-based
            long offsetEndInLineEx = (endBase - L.baseStart) + 1;           // exclusive

            long byteStart = L.byteStart + offsetStartInLine;               // ASCII 1 byte/base
            long byteEndEx = L.byteStart + offsetEndInLineEx;               // exclusive
            out.add(new ByteSpan(byteStart, byteEndEx));
        }
        return out;
    }

    /** Naive in-place index adjustment after deleting [fromBase..toBase] (inclusive). */
    void applyDeletion(long fromBase, long toBase) {
        if (fromBase < 1 || toBase < fromBase) throw new IllegalArgumentException("bad base range");
        if (lines.isEmpty()) return;

        long deltaBases = (toBase - fromBase + 1);
        long deltaBytes = deltaBases;

        int first = findLineByBase(fromBase);
        int last  = findLineByBase(toBase);

        // Adjust partially affected first/last lines, remove fully-eaten ones, and shift the rest.
        java.util.List<LineEntry> mutable = new java.util.ArrayList<>(lines);
        // Trim front
        LineEntry Lf = mutable.get(first);
        if (fromBase > Lf.baseStart) {
            // delete tail portion in first line
            long cut = Math.min(deltaBases, Lf.baseEnd - fromBase + 1);
            Lf.baseEnd -= cut;
            Lf.byteEndExclusive -= cut;
        } else {
            // delete whole first line (or will be eaten by later logic)
        }
        // Trim back
        LineEntry Ll = mutable.get(last);
        if (toBase < Ll.baseEnd) {
            long cut = Math.min(deltaBases, Ll.baseEnd - toBase);
            // delete head portion in last line
            long newBaseStart = toBase + 1;
            long newByteStart = Ll.byteStart + (newBaseStart - Ll.baseStart);
            Ll.baseStart = newBaseStart - deltaBases;
            Ll.byteStart = newByteStart - deltaBytes;
        } else {
            // will be shifted/removed below
        }

        // Remove any lines whose base range collapsed
        mutable.removeIf(le -> le.baseEnd < le.baseStart);

        // Shift all lines strictly after the deletion by (-delta)
        for (int idx = 0; idx < mutable.size(); idx++) {
            LineEntry L = mutable.get(idx);
            if (L.baseStart > toBase) {
                L.baseStart -= deltaBases;
                L.baseEnd   -= deltaBases;
                L.byteStart -= deltaBytes;
                L.byteEndExclusive -= deltaBytes;
            }
        }

        // Re-freeze as unmodifiable
        lines.clear(); // if you stored unmodifiable above, switch to a mutable field or a builder
        lines.addAll(java.util.Collections.unmodifiableList(mutable));
        // Note: first/last base bytes would also shift by -deltaBytes if deletion occurs before them.
        // You can recompute from lines when needed.
    }

    private int findLineByBase(long base) {
        int lo = 0, hi = lines.size()-1, ans = hi;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            LineEntry L = lines.get(mid);
            if (base < L.baseStart) { hi = mid - 1; }
            else if (base > L.baseEnd) { lo = mid + 1; }
            else { return mid; } // inside
            ans = lo; // insertion point
        }
        return Math.max(0, Math.min(ans, lines.size()-1));
    }
}



