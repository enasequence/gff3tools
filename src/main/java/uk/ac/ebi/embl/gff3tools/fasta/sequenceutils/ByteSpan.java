package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

public final class ByteSpan {
    public final long start;  // inclusive
    public final long endEx;  // exclusive
    public ByteSpan(long start, long endEx) { this.start = start; this.endEx = endEx; }
    public long length() { return endEx - start; }
}