package uk.ac.ebi.embl.converter.rules;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class FFFeatureToGFF3Feature implements IConversionRule<Entry, GFF3Feature> {
    Map<String, Integer> geneCount = new HashMap<>();
    String currentParent = null;

    @Override
    public GFF3Feature from(Entry entry) {

        try {
            entry.setPrimaryAccession(entry.getPrimaryAccession() + ".1");
            entry.getSequence().setAccession(entry.getSequence().getAccession() + ".1");

            Map<String, List<ConversionEntry>> featureMap = ConversionUtils.getFFToGFF3FeatureMap();
            List<List<String>> rows = new ArrayList<>();

            for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

                List<String> columns = new ArrayList<>();
                // Output header
                if (feature.getName().equalsIgnoreCase("source")) {
                    continue; // early exit
                }

                // TODO: insert a gene feature if/where appropriate

                Optional<ConversionEntry> first = featureMap.get(feature.getName()).stream()
                        .filter(conversionEntry -> hasAllQualifiers(feature, conversionEntry)).findFirst();

                // Rule: Throw an error if we find an unmapped feature
                if (first.isEmpty())
                    throw new Exception("Mapping not found for " + feature.getName());


                columns.add(entry.getSequence().getAccession());

                // Add source
                columns.add(".");

                // Add type
                columns.add(feature.getName());

                // Add start
                columns.add(feature.getLocations().getMinPosition().toString());

                // Add end
                // TODO: Ask if this leads to data loss in case on joins etc.
                columns.add(feature.getLocations().getMaxPosition().toString());

                // Add score
                columns.add(".");

                // Write strand
                columns.add(feature.getLocations().isComplement() ? "-" : "+");

                // Write phase
                columns.add(getPhase(feature));

                // Write attributes
                columns.add(getAttributes(feature));

                rows.add(columns);

            }
            return new GFF3Feature(rows);
        }catch (Exception e){
            throw new ConversionError();
        }
    }



    private String getPhase(Feature feature) {

        // Rule: Use the phase value if present in a qualified.
        // Rule: If phase qualifier is not present, calculate it only for CDS (default
        // 0) or use "." otherwise

        Qualifier phase = feature.getQualifiers()
                .stream().filter(qualifier -> qualifier.getName().equalsIgnoreCase("phase"))
                .findFirst().orElse(null);
        Qualifier codonStart = feature.getQualifiers()
                .stream().filter(qualifier -> qualifier.getName().equalsIgnoreCase("codon_start"))
                .findFirst().orElse(null);
        if (phase != null) {
            return phase.getValue();
        } else if (feature.getName().equalsIgnoreCase("CDS")) {
            return codonStart == null ? "0" : String.valueOf((Long.parseLong(codonStart.getValue()) - 1));
        }

        return ".";
    }

    private String getAttributes(Feature feature) {
        Map<String, String> attributes = new LinkedHashMap<>(); // keep order
        feature.getQualifiers().forEach(qualifier -> {
            // TODO: remove attributes which caused the match from conversion entries
            attributes.put(qualifier.getName(), qualifier.getValue());
        });

        // Rule: Assign a unique ID to mRNAs with a gene qualifier
        // Rule: Add the ID of the parent mRNA to all other features that have the same
        // gene qualifier value
        String gene = attributes.getOrDefault("gene", null);
        if (gene != null) {
            if (feature.getName().equalsIgnoreCase("gene") || feature.getName().equalsIgnoreCase("mRNA")) {
                int count = this.geneCount.getOrDefault(gene, 0) + 1;
                geneCount.put(gene, count);
                currentParent = feature.getName()+"%d_%s".formatted(count, gene);
                attributes.put("ID", currentParent);
            } else {
                attributes.put("Parent", currentParent);
                attributes.remove("gene");
            }
        }

        if( ! getPartiality(feature).isBlank()) {
            attributes.put("partial", getPartiality(feature));
        }

        return attributes.entrySet().stream()
                .map(att -> "%s=%s".formatted(att.getKey(), att.getValue()))
                .sorted()
                .collect(Collectors.joining(";"));
    }

    private String getPartiality(Feature feature) {

        StringJoiner partiality = new StringJoiner(",");

        if (feature.getLocations().isFivePrimePartial()) {
            partiality.add("start");
        }
        if (feature.getLocations().isThreePrimePartial()) {
            partiality.add("end");
        }
        // Returns empty string if non partial location
        return partiality.length() > 1 ? partiality.toString() : "";
    }

    private boolean hasAllQualifiers(Feature feature, ConversionEntry conversionEntry) {
        boolean firstQualifierMatches = conversionEntry.getQualifier1() == null;
        boolean secondQualifierMatches = conversionEntry.getQualifier2() == null;

        for (Qualifier qualifier : feature.getQualifiers()) {
            String formatted = "/%s=%s".formatted(qualifier.getName(), qualifier.getValue());
            firstQualifierMatches |= formatted.equalsIgnoreCase(conversionEntry.getQualifier1());
            secondQualifierMatches |= formatted.equalsIgnoreCase(conversionEntry.getQualifier2());
        }
        return firstQualifierMatches && secondQualifierMatches;
    }
}
