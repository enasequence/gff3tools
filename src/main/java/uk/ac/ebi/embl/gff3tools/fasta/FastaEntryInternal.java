package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.fasta.headerutils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndex;

@Getter
@Setter
class FastaEntryInternal {
    String submissionId;
    String accessionId;
    FastaHeader header; //json info
    //information needed for accessing the file
    long fastaStartByte; // position of '>' in the file
    SequenceIndex sequenceIndex; // a smart index for querying ranges in the file
}
