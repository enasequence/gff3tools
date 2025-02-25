package uk.ac.ebi.embl.converter;

import uk.ac.ebi.embl.converter.Metadata;

public interface IMetadataTransformer<T> {
  /**
   * Transforms an object into an Metadata object.
   *
   * @param o An object that can be transformed into Metadata
   * @return A Metadata object.
   */
  public Metadata toMetadata(T o);
}
