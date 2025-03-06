package uk.ac.ebi.embl.converter.rules;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceMetadata;

public class FFEntryToGFF3SourceAttributes implements IConversionRule<Entry, GFF3SourceMetadata> {

    @Override
    public GFF3SourceMetadata from(Entry entry) throws ConversionError {

        for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

            // Output header
            if (feature.getName().equalsIgnoreCase("source")) {
                GFF3SourceMetadata sourceMetadata = new GFF3SourceMetadata();
                for (Qualifier q: feature.getQualifiers()) {
                    sourceMetadata.addAttribute(q.getName(), q.getValue());
                }
                return sourceMetadata;
            }
        }

        throw new NoSourcePresent();
    }

    public static class NoSourcePresent extends ConversionError {
    };
}
