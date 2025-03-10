package uk.ac.ebi.embl.converter.rules;

import io.vavr.Tuple2;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;
import uk.ac.ebi.embl.converter.gff3.GFF3Model;
import uk.ac.ebi.embl.converter.gff3.IGFF3Feature;
import java.util.*;

public class FFToGFF3Model implements IConversionRule<Feature, GFF3Model> {
  final private String accession;

  public FFToGFF3Model(String accession) {
    this.accession = accession + ".1";
  }

  public Tuple2<List<GFF3Model>, List<ConversionError>> from(ListIterator<Feature> features) {

    ArrayList<IConversionRule.ConversionError> errors = new ArrayList<>();
    GFF3Model model = new GFF3Model();

    Tuple2<List<GFF3Headers>, List<ConversionError>> headerResults = new FFToGFF3Headers(accession).from(features);
    if (!headerResults._1.isEmpty()) {
      // There should be only one header on teh result.
      model.addFeature(headerResults._1.get(0));
    } else {
      // TODO: I presume this is a fatal error?
      errors.add(new NoHeaders());
    }

    errors.addAll(headerResults._2);
    IConversionRule[] rules = new IConversionRule[] {
        new FFToGFF3Gene(accession),
        new FFToGFF3Record(accession)
    };
    while (features.hasNext()) {
      for (IConversionRule rule : rules) {
        @SuppressWarnings("unchecked")
        Tuple2<List<IGFF3Feature>, List<ConversionError>> result = rule.from(features);
        for (IGFF3Feature feature : result._1) {
          model.addFeature(feature);
        }
        errors.addAll(result._2);
        break;
      }
    }
    ArrayList<GFF3Model> models = new ArrayList<>();
    models.add(model);
    return new Tuple2<>(models, errors);
  };

  static class NoHeaders extends ConversionError {}
}
