package uk.ac.ebi.embl.converter.rules;

import io.vavr.Tuple2;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceAttributesRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public class FFToGFF3Headers implements IConversionRule<Feature, GFF3Headers> {

    private final String accession;

    public FFToGFF3Headers(String accession) {
        this.accession = accession;
    }

    private Tuple2<Optional<GFF3Headers>, List<IConversionRule.ConversionError>> transformHeaders(String accession, ListIterator<Feature> features) {
        ArrayList<IConversionRule.ConversionError> errors = new ArrayList<>();
        Feature feature = features.next();
        // Output header
        if (feature.getName().equalsIgnoreCase("source")) {
            long start = feature.getLocations().getMinPosition();
            long end = feature.getLocations().getMaxPosition();
            GFF3SourceAttributesRecord sourceAttributes = getSourceAttributes(feature);
            GFF3Headers headers = new GFF3Headers("3.1.26", accession, start, end, sourceAttributes);
            return new Tuple2<>(Optional.of(headers), errors);
        }
        features.previous();

        return new Tuple2<>(Optional.empty(), errors);
    }

    private GFF3SourceAttributesRecord getSourceAttributes(Feature feature) {
        GFF3SourceAttributesRecord sourceAttributes = new GFF3SourceAttributesRecord();
        for (Qualifier q : feature.getQualifiers()) {
            sourceAttributes.addAttribute(q.getName(), q.getValue());
        }
        return sourceAttributes;
    }

    @Override
    public Tuple2<Optional<GFF3Headers>, List<ConversionError>> from(ListIterator<Feature> input) {
        return transformHeaders(accession, input);
    }
}
