package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndex;

@Getter
@Setter
public class FastaEntry {
    String id;
    FastaHeader header; //json info
    //information needed for accessing the file
    long fastaStartByte; // position of '>' in the file
    SequenceIndex sequenceIndex; // a smart index for querying ranges in the file
}
