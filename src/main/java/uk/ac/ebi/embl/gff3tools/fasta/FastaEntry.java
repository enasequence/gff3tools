package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.fasta.sequenceutils.SequenceIndex;

@Getter
@Setter
public class FastaEntry {
    String id; // submissionNumber or accessionNumber
    FastaHeader header;
    SequenceIndex sequenceIndex;

    long fastaStart;     // position of '>' in the file
    long sequenceStart;  // first allowed base after header (absolute byte offset)
    long sequenceEnd;    // last allowed base before next header (absolute byte offset)
}
