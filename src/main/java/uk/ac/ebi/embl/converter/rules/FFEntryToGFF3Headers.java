package uk.ac.ebi.embl.converter.rules;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.gff3.GFF3Headers;

public class FFEntryToGFF3Headers implements IConversionRule<Entry, GFF3Headers> {

    @Override
    public GFF3Headers from(Entry entry) {

        for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

            // Output header
            if (feature.getName().equalsIgnoreCase("source")) {
                String accession = entry.getPrimaryAccession() + ".1";
                long start = feature.getLocations().getMinPosition();
                long end = feature.getLocations().getMaxPosition();
                return new GFF3Headers("3.1.26", accession, start, end);
            }
        }

        throw new NoSourcePresent();
    }

    public static class NoSourcePresent extends ConversionError {
    };
}
