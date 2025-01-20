package uk.ac.ebi.embl.converter;

import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.api.validation.ValidationResult;
import uk.ac.ebi.embl.converter.utils.ConversionEntry;
import uk.ac.ebi.embl.converter.utils.ConversionUtils;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class FFToGFF3Converter {

    Map<String, List<ConversionEntry>> featureMap;
    Map<String, Integer> geneCount = new HashMap<>();
    String currentParent = null;

    FFToGFF3Converter() throws Exception {
        featureMap = ConversionUtils.getFFToGFF3FeatureMap();
    }

    private void writeHeader(Entry entry, Writer writer, Feature feature) throws IOException {
        // Rule: Start will be the min position of all locations and end the max
        long start = feature.getLocations().getMinPosition();
        long end = feature.getLocations().getMaxPosition();
        String accession = entry.getPrimaryAccession();
        writer.write("##gff-version 3.1.26\n");
        writer.write("##sequence-region %s %d %d\n".formatted(accession, start, end));
    }

    //TODO: Update/Remove all main methods once build is stable
    public static void main(String[] args) throws Exception {
        FFToGFF3Converter ffToGFF3Converter = new FFToGFF3Converter();
        String filename = "src/test/resources/embl_BN000065/embl_BN000065.embl";
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        try (BufferedReader bufferedReader =  Files.newBufferedReader(Path.of(filename))) {
            EmblEntryReader entryReader = new EmblEntryReader(bufferedReader, EmblEntryReader.Format.EMBL_FORMAT, filename, readerOptions );
            ValidationResult validationResult = entryReader.read();
            Entry entry = entryReader.getEntry();
            Writer gff3Writer = new StringWriter();
            ffToGFF3Converter.writeGFF3(entry, gff3Writer);
            Files.write(Paths.get("delete-this.gff3"), gff3Writer.toString().getBytes());
        }
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


    private String getAttributes(Feature feature) {
        Map<String, String> attributes = new LinkedHashMap<>(); // keep order
        feature.getQualifiers().forEach(qualifier -> {
            // TODO: remove attributes which caused the match from conversion entries
            attributes.put(qualifier.getName(), qualifier.getValue());
        });

        // Rule: Assign a unique ID to mRNAs with a gene qualifier
        // Rule: Add the ID of the parent mRNA to all other features that have the same gene qualifier value
        String gene = attributes.getOrDefault("gene", null);
        if (gene!=null) {
            if (feature.getName().equalsIgnoreCase("mRNA")) {
                int count = this.geneCount.getOrDefault(gene, 0) + 1;
                geneCount.put(gene, count);
                currentParent = "mRNA%d_%s".formatted(count, gene);
                attributes.put("ID",currentParent);
            } else {
                attributes.put("Parent",currentParent);
                attributes.remove("gene");
            }
        }

        return attributes.entrySet().stream()
                .map(att -> "%s=%s".formatted(att.getKey(), att.getValue()))
                .sorted()
                .collect(Collectors.joining(";"));

    }


    private String getPhase(Feature feature) {

        // Rule: Use the phase value if present in a qualified.
        // Rule: If phase qualifier is not present, calculate it only for CDS (default 0) or use "." otherwise

        Qualifier phase = feature.getQualifiers()
                .stream().filter(qualifier -> qualifier.getName().equalsIgnoreCase("phase"))
                .findFirst().orElse(null);
        Qualifier codonStart = feature.getQualifiers()
                .stream().filter(qualifier -> qualifier.getName().equalsIgnoreCase("codon_start"))
                .findFirst().orElse(null);
        if (phase!=null) {
            return phase.getValue();
        } else if (feature.getName().equalsIgnoreCase("CDS")) {
            return codonStart==null ? "0" : String.valueOf((Long.parseLong(codonStart.getValue())-1));
        }

        return ".";
    }


    public void writeGFF3(Entry entry, Writer writer) throws Exception {

        // Rule: Always append a version number to the accession
        // TODO: Check if a version already exists and update it
        entry.setPrimaryAccession( entry.getPrimaryAccession()+".1" );
        entry.getSequence().setAccession(entry.getSequence().getAccession()+".1");

        for (Feature feature : entry.getFeatures().stream().sorted().toList()) {

            // Output header
            if (feature.getName().equalsIgnoreCase("source")) {
                writeHeader(entry, writer, feature);
                continue; // early exit
            }

            // TODO: insert a gene feature if/where appropriate

            Optional<ConversionEntry> first = featureMap.get(feature.getName()).stream()
                    .filter(conversionEntry -> hasAllQualifiers(feature, conversionEntry)).findFirst();

            // Rule: Throw an error if we find an unmapped feature
            if (first.isEmpty()) throw new Exception("Mapping not found for " + feature.getName());


            List<String> columns = new ArrayList<>();
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

            writer.write(String.join("\t", columns));
            writer.write("\n");
        }
    }

}
