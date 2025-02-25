package uk.ac.ebi.embl.converter;

import io.vavr.Tuple2;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;

import java.util.Date;
import java.util.List;
import java.util.Map;

public record Metadata(
        String accessionNumber, // Accession number (e.g., AB000123)
        int version, // Version of the entry (e.g., 1)
        String description, // Description of the sequence (e.g., "Homo sapiens mRNA for hypothetical protein" )
        List<String> keywords, // Keywords describing the sequence
        List<Tuple2<String, String>> sourceQualifiers, // List of "source" qualifiers
        List<Reference> references, // List of references
        Map<String, String> crossReferences // Cross-references to other databases (e.g., UniProt, GO)

//        Date creationDate, // Date the entry was created
//        Date lastUpdatedDate, // Date the entry was last updated
//        String organismName, // Name of the organism (e.g., "Homo sapiens") part of source
//        String taxonomyId, // Taxonomy ID (e.g., "9606") part of source
//        List<String> taxonomicClassification, // Taxonomic classification (e.g., ["Eukaryota", "Metazoa", ...]) source
//        Map<String, String> crossReferences, // Cross-references to other databases (e.g., UniProt, GO)
//        String moleculeType, // Type of molecule (e.g., "mRNA")
//        String sampleSource, // Source of the sample (e.g., tissue type)
//        String geographicLocation, // Geographic location of the sample
//        Date collectionDate, // Date the sample was collected
//        String submitter, // Name of the submitter
//        Date submissionDate, // Date the data was submitted
//        String projectInformation // Information about the project (e.g., ENCODE, 1000 Genomes)
) {

  // Nested record for reference information
  public record Reference(
      int referenceNumber, // Reference number (e.g., 1)
      String authors, // Authors of the reference
      String title // Title of the reference
//      String journal, // Journal where the reference was published
//      String pubmedId, // PubMed ID of the reference
//      String doi // DOI of the reference
  ) {
  }
}
