package uk.ac.ebi.embl.gff3tools.fasta.headerutils;

import lombok.Value;

@Value
public class ParsedHeader {
    String id;
    FastaHeader header;
}