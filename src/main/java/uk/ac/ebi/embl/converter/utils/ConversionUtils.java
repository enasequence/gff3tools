package uk.ac.ebi.embl.converter.utils;

import uk.ac.ebi.embl.converter.GFF3ToFFConverter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ConversionUtils {

    static Map<String, List<ConversionEntry>> ff2gff3 = null;
    static Map<String, ConversionEntry> gff32ff = null;

    public static Map<String, List<ConversionEntry>> getFFToGFF3FeatureMap() throws URISyntaxException, IOException {
        if (ff2gff3 !=null) return ff2gff3;
        loadMaps();
        return ff2gff3;
    }


    public static Map<String, ConversionEntry> getGFF3ToFFFeatureMap() throws URISyntaxException, IOException {
        if (gff32ff !=null) return gff32ff;
        loadMaps();
        return gff32ff;
    }

    private static void loadMaps() throws URISyntaxException, IOException {
        ff2gff3 = new HashMap<>();
        gff32ff = new HashMap<>();
        Path filePath = Paths.get(Objects.requireNonNull(GFF3ToFFConverter.class.getResource("/feature-mapping.tsv")).toURI());
        List<String> lines = Files.readAllLines(filePath);
        lines.remove(0);
        for (String line : lines) {
            ConversionEntry conversionEntry = new ConversionEntry(line.split("\t"));
            ff2gff3.putIfAbsent(conversionEntry.feature, new ArrayList<>());
            ff2gff3.get(conversionEntry.feature).add(conversionEntry);
            gff32ff.putIfAbsent(conversionEntry.sOID, conversionEntry);
            gff32ff.putIfAbsent(conversionEntry.sOTerm, conversionEntry);
        }
    }

}
