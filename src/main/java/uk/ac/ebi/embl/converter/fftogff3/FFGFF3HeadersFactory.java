package uk.ac.ebi.embl.converter.fftogff3;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;

import java.util.Optional;

public class FFGFF3HeadersFactory implements IConversionRule<Entry, GFF3Headers> {

    @Override
    public GFF3Headers from(Entry entry) {

            Feature feature = Optional.ofNullable(entry.getPrimarySourceFeature())
                    .orElseThrow(NoSourcePresent::new);

            return new GFF3Headers(
                    "3.1.26",
                    entry.getPrimaryAccession() + ".1",
                    feature.getLocations().getMinPosition(),
                    feature.getLocations().getMaxPosition()
            );
    }

    public static class NoSourcePresent extends ConversionError {
    };
}
