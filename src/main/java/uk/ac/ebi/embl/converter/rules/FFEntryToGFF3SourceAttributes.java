package uk.ac.ebi.embl.converter.rules;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.gff3.GFF3SourceAttributesRecord;

public class FFEntryToGFF3SourceAttributes implements IConversionRule<Entry, GFF3SourceAttributesRecord> {

    @Override
    public GFF3SourceAttributesRecord from(Entry entry) throws ConversionError {

        for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

            // Output header
            if (feature.getName().equalsIgnoreCase("source")) {
                GFF3SourceAttributesRecord sourceAttributes = new GFF3SourceAttributesRecord();
                for (Qualifier q: feature.getQualifiers()) {
                    sourceAttributes.addAttribute(q.getName(), q.getValue());
                }
                return sourceAttributes;
            }
        }

        throw new NoSourcePresent();
    }

    public static class NoSourcePresent extends ConversionError {
    };
}
