package uk.ac.ebi.embl.gff3tools.fasta.headerutils;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.fasta.Topology;

import java.util.Optional;

@Getter
@Setter
public class FastaHeader {
    String description;               // mandatory (can be empty if you insist)
    String moleculeType;        // mandatory (can be null if empty allowed)
    Topology topology;                // mandatory (can be null if empty allowed)
    Optional<String> chromosomeType;            // optional (doesnt have to be a json)
    Optional<String> chromosomeLocation;        // optional
    Optional<String> chromosomeName;            // optional
}
