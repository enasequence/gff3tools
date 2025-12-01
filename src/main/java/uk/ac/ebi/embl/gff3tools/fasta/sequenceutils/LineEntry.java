package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

public final class LineEntry {
    public long baseStart;        // 1-based, inclusive (after edits)
    public long baseEnd;          // 1-based, inclusive
    public long byteStart;        // absolute byte offset of first base in this line
    public long byteEndExclusive; // absolute byte offset one past last base

    public LineEntry(long baseStart, long baseEnd, long byteStart, long byteEndExclusive) {
        this.baseStart = baseStart;
        this.baseEnd = baseEnd;
        this.byteStart = byteStart;
        this.byteEndExclusive = byteEndExclusive;
    }

    public long lengthBases() { return baseEnd - baseStart + 1; }
    public long lengthBytes() { return byteEndExclusive - byteStart; } // ASCII: same as bases
}
