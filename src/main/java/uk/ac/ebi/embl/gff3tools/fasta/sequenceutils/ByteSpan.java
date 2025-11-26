package uk.ac.ebi.embl.gff3tools.fasta.sequenceutils;

final class ByteSpan {
    final long start;   // inclusive
    final long endEx;   // exclusive
    ByteSpan(long s, long e) { this.start = s; this.endEx = e; }
}