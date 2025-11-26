package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

public final class LineEntry {
    long baseStart;       // 1-based base index at line start (inclusive)
    long baseEnd;         // 1-based base index at line end (inclusive)
    long byteStart;       // absolute byte offset of first base in the line
    long byteEndExclusive;// absolute byte offset just after the last base

    public LineEntry(long bStart, long bEnd, long byStart, long byEndEx) {
        this.baseStart = bStart;
        this.baseEnd = bEnd;
        this.byteStart = byStart;
        this.byteEndExclusive = byEndEx;
    }
}

