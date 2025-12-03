package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.FastaHeader;

@Getter
@Setter
public class FastaEntry {
    String submissionId;
    String accessionId;
    FastaHeader header; //json info
    long totalBases;
    long startCountNs;
    long endCountNs;
}
