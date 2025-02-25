package uk.ac.ebi.embl.converter;

import io.vavr.Tuple2;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.api.entry.XRef;
import uk.ac.ebi.embl.api.entry.reference.Reference;
import uk.ac.ebi.embl.api.entry.reference.Publication;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class EntryMetadataTransformer implements IMetadataTransformer<Entry> {

 /**
   * Transforms an Entry object into an Metadata object.
   *
   * @param entry The Entry object from the ENA API.
   * @return A Metadata object.
   */
  public Metadata toMetadata(Entry entry) {
    // Extract basic metadata
    String accessionNumber = entry.getPrimaryAccession();
    int version = entry.getVersion();
    String description = entry.getDescription().toString();
    List<String> keywords = entry.getKeywords().stream()
        .map(Text::getText)
        .collect(Collectors.toList());
    List<Tuple2<String, String>> sourceQualifiers = entry
            .getPrimarySourceFeature()
            .getQualifiers()
            .stream()
            .map((q) -> new Tuple2<String, String>(q.getName(), q.getValue()))
            .toList();


    // Parse references
    List<Metadata.Reference> references = entry.getReferences()
            .stream()
            .map(EntryMetadataTransformer::parseReference)
            .toList();

    // Parse cross-references
    Map<String, String> crossReferences = entry.getXRefs().stream()
        .collect(Collectors.toMap(XRef::getDatabase, XRef::getPrimaryAccession));

    // Build Metadata
    return new Metadata(
            accessionNumber,
            version,
            description,
            keywords,
            sourceQualifiers,
            references,
            crossReferences
    );
  }

  /**
   * Parses a date string into a LocalDate object.
   */
  private static LocalDate parseDate(String dateString) {
    if (dateString == null || dateString.isEmpty()) {
      return null;
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy",
        Locale.ENGLISH);
    return LocalDate.parse(dateString, formatter);
  }

  /**
   * Parses a Reference object into an Metadata.Reference object.
   */
  private static Metadata.Reference parseReference(Reference reference) {
    Publication publication = reference.getPublication();
    return new Metadata.Reference(
        reference.getReferenceNumber(),
        publication.getAuthors(),
        publication.getTitle(),
        publication.getTitle()
  }
}
