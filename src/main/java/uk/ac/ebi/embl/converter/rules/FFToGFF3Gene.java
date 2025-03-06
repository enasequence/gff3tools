package uk.ac.ebi.embl.converter.rules;

import io.vavr.Tuple2;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.gff3.GFF3Gene;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;
import uk.ac.ebi.embl.converter.gff3.GFF3Record;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceAttributesRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public class FFToGFF3Gene implements IConversionRule<Feature, GFF3Gene>{
    String accession;
    public FFToGFF3Gene(String accession) {
        this.accession = accession;
    }

    @Override
    public Tuple2<Optional<GFF3Gene>, List<ConversionError>> from(ListIterator<Feature> input) {
        ArrayList<ConversionError> errors = new ArrayList<>();
        FFToGFF3Record fftogff3record = new FFToGFF3Record(accession);
        Feature feature = input.next();
        // Output header
        if (feature.getName().equalsIgnoreCase("gene")) {
            Tuple2<Optional<GFF3Record>, List<ConversionError>> result =  fftogff3record.fromFeature(feature);
            if (result._1.isPresent()) {
                GFF3Record record = result._1.get();
                List<String> genes = record.getAttributeValues("gene");
                if (genes.isEmpty()) {
                    errors.add(new NoGeneQualifierOnGeneFeature());
                    return new Tuple2<>(Optional.empty(), errors);
                }
                if (genes.size() > 1) {
                    //TODO We need to do something about multiple genes on a single gene feature.
                    // Ideally we want to return two GENE features in these cases. We may need to change the parse interface.
                }
                String id = genes.get(0);
                GFF3Gene gene = new GFF3Gene(id, record);
                record.removeAttribute("gene");
                // TODO parse children here.

                return new Tuple2<>(Optional.of(gene), errors);
            }
        }
        input.previous();
        return new Tuple2<>(Optional.empty(), errors);
    }

    static class NoGeneQualifierOnGeneFeature extends ConversionError {}
}
