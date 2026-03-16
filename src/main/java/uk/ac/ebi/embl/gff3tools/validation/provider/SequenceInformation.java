package uk.ac.ebi.embl.gff3tools.validation.provider;

import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

import java.io.Reader;
import java.util.List;
import java.util.Optional;

public class SequenceInformation {

    private final ValidationContext context;

    public SequenceInformation(ValidationContext context) {
        this.context = context;
    }

    public SequenceReader getReader() {
        throw  new UnsupportedOperationException();
    }
}
