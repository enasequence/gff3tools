package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FASTAFile {
    String Id; //accessionNumber
    FastaHeader header;
    SequenceAccessor sequenceAccessor;
}
