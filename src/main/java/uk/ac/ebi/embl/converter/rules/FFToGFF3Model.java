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

  public Tuple2<Optional<GFF3Model>, List<ConversionError>> from(ListIterator<Feature> features) {

    ArrayList<IConversionRule.ConversionError> errors = new ArrayList<>();
    GFF3Model model = new GFF3Model();

    Tuple2<Optional<GFF3Headers>, List<ConversionError>> headerResults = new FFToGFF3Headers(accession).from(features);
    headerResults._1.ifPresentOrElse(model::addFeature, () -> errors.add(new NoHeaders()));

    errors.addAll(headerResults._2);
    IConversionRule[] rules = new IConversionRule[] {
        new FFToGFF3Gene(accession),
        new FFToGFF3Record(accession)
    };
    while (features.hasNext()) {
      for (IConversionRule rule : rules) {
        @SuppressWarnings("unchecked")
        Tuple2<Optional<IGFF3Feature>, List<ConversionError>> result = rule.from(features);
        result._1.ifPresent(model::addFeature);
        errors.addAll(result._2);
        break;
      }
    }
    return new Tuple2<>(Optional.of(model), errors);
  };

  static class NoHeaders extends ConversionError {}
}
