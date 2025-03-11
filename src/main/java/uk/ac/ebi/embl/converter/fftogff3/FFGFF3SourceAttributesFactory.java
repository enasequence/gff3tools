package uk.ac.ebi.embl.converter.fftogff3;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.IConversionRule;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceMetadata;

public class FFGFF3SourceAttributesFactory implements IConversionRule<Entry, GFF3SourceMetadata> {

    @Override
    public GFF3SourceMetadata from(Entry entry) throws ConversionError {

        for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

            // Output header
            if (feature.getName().equalsIgnoreCase("source")) {
                String organism = feature.getQualifiers("organism").stream().findFirst()
                        .map(Qualifier::getValue)
                        .orElseGet(() -> null);
                return new GFF3SourceMetadata(organism);
            }
        }

        throw new NoSourcePresent();
    }

    public static class NoSourcePresent extends ConversionError {
    };
}
