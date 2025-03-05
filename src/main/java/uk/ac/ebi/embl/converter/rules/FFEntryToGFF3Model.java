package uk.ac.ebi.embl.converter.rules;

import uk.ac.ebi.embl.converter.gff3.GFF3Headers;
import uk.ac.ebi.embl.converter.gff3.GFF3Model;
import uk.ac.ebi.embl.api.entry.Entry;

public class FFEntryToGFF3Model implements IConversionRule<Entry, GFF3Model> {
  public GFF3Model from(Entry entry) throws ConversionError {
    GFF3Model model = new GFF3Model();
    GFF3Headers headers = new FFEntryToGFF3Headers().from(entry);
    model.addFeature(headers);
    model.addFeature(new FFEntryToGFF3SourceAttributes().from(entry));
    return model;
  };
}
