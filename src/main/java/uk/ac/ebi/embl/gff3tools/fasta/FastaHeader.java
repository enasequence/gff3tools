package uk.ac.ebi.embl.gff3tools.fasta;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
public class FastaHeader {
    String description;               // mandatory (can be empty if you insist)
    String moleculeType;        // mandatory (can be null if empty allowed)
    Topology topology;                // mandatory (can be null if empty allowed)
    Optional<String> chromosomeType;            // optional (open string unless you constrain)
    Optional<String> chromosomeLocation;        // optional
    Optional<String> chromosomeName;            // optional
    // Not stored here: NCBITaxon (you said you’ll fetch it from BioSample)
}
