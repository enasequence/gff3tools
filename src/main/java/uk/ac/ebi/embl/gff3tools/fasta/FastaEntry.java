package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FastaEntry {
    String Id; //accessionNumber
    FastaHeader header;
}
