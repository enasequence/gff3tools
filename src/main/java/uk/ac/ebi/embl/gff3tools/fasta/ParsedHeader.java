package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Value;

@Value
public class ParsedHeader {
    String id;
    FastaHeader header;
}