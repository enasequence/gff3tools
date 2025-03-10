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
    public Tuple2<List<GFF3Gene>, List<ConversionError>> from(ListIterator<Feature> input) {
        ArrayList<ConversionError> errors = new ArrayList<>();
        FFToGFF3Record fftogff3record = new FFToGFF3Record(accession);
        Feature feature = input.next();
        ArrayList<GFF3Gene> genes = new ArrayList<>();
        // Output header
        if (feature.getName().equalsIgnoreCase("gene")) {
            Tuple2<List<GFF3Record>, List<ConversionError>> result =  fftogff3record.fromFeature(feature);
            GFF3Record record = result._1.get(0);
            List<String> geneNames = record.getAttributeValues("gene");
            if (geneNames.isEmpty()) {
                errors.add(new NoGeneQualifierOnGeneFeature());
                return new Tuple2<>(genes, errors);
            } else {
                for (String id : geneNames) {
                    GFF3Gene gene = new GFF3Gene(id, record);
                    record.removeAttribute("gene", id);

                    // Child parsing
                    while (input.hasNext()) {
                        Feature potentialChildFeature = input.next();
                        Tuple2<List<GFF3Record>, List<ConversionError>> results = fftogff3record.fromFeature(potentialChildFeature);
                        // GFF3Record is always present.
                        GFF3Record childRecord = results._1.get(0);
                        if(childRecord.getAttributeValues("gene").contains(id)) {
                            childRecord.removeAttribute("gene");
                            childRecord.addAttribute("Parent", id);
                            gene.addChild(childRecord);
                        } else {
                            // Not a child, step back and stop parsing children
                            input.previous();
                            break;
                        }
                    }
                    genes.add(gene);
                }
            }

            return new Tuple2<>(genes, errors);
        }
        input.previous();
        return new Tuple2<>(genes, errors);
    }

    static class NoGeneQualifierOnGeneFeature extends ConversionError {}
}
