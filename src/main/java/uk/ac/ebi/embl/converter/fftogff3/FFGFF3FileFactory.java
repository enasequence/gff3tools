package uk.ac.ebi.embl.converter.fftogff3;

import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.api.entry.Entry;

public class FFGFF3FileFactory implements IConversionRule<Entry, GFF3File> {
  public GFF3File from(Entry entry) throws ConversionError {
    GFF3File model = new GFF3File();
    GFF3Headers headers = new FFGFF3HeadersFactory().from(entry);
    model.addFeature(headers);
    model.addFeature(new FFGFF3SourceAttributesFactory().from(entry));
    model.addFeature(new FFGFF3FeaturesFactory().from(entry));
    return model;
  };
}
