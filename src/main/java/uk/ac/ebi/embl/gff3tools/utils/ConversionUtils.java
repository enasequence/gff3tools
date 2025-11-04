/*
 * Copyright 2025 EMBL - European Bioinformatics Institute
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package uk.ac.ebi.embl.gff3tools.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ConversionUtils {
    INSTANCE;
    private Map<String, List<ConversionEntry>> ff2gff3 = null;
    private Map<String, ConversionEntry> gff32ff = null;
    private Map<String, String> ff2gff3Qualifiers = null;
    private Map<String, String> gff32ffQualifiers = null;
    // Map of child : list of possible parents
    // Uses sOTerms (gff3 feature names)
    private Map<String, Set<String>> featureRelations = null;
    private OntologyClient ontologyClient = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionUtils.class);

    private ConversionUtils() {
        this.ontologyClient = OntologyClient.getInstance();
        this.loadMaps();
    }

    public static Map<String, List<ConversionEntry>> getFF2GFF3FeatureMap() {
        return INSTANCE.ff2gff3;
    }

    public static Map<String, String> getFF2GFF3QualifierMap() {
        return INSTANCE.ff2gff3Qualifiers;
    }

    public static Map<String, Set<String>> getFeatureRelationMap() {
        return INSTANCE.featureRelations;
    }

    public static Map<String, String> getGFF32FFQualifierMap() {
        return INSTANCE.gff32ffQualifiers;
    }

    // Returns the EMBL feature name for an SOTerm
    public static ConversionEntry getINSDCFeatureForSOTerm(String SOTerm) {
        ConversionEntry conversionEntry = INSTANCE.gff32ff.get(SOTerm);
        if (conversionEntry == null) {
            LOGGER.info("SOTerm \"%s\" not found in tsv mapping. Search for matches using ontology parents"
                    .formatted(SOTerm));
            Stream<String> parents = INSTANCE.ontologyClient.getParents(SOTerm);
            for (Iterator<String> it = parents.iterator(); it.hasNext(); ) {
                String parent = it.next();
                conversionEntry = INSTANCE.gff32ff.get(parent);
                if (conversionEntry != null) {
                    LOGGER.info("SOTerm \"%s\" mapped to INSDC feature \"%s\" using ontology through parent \"%s\""
                            .formatted(SOTerm, conversionEntry.feature, parent));
                    break;
                }
            }
        }

        return conversionEntry;
    }

    public static OntologyClient getOntologyClient() {
        return INSTANCE.ontologyClient;
    }

    private void addConversionEntry(ConversionEntry conversionEntry) {
        ff2gff3.putIfAbsent(conversionEntry.feature, new ArrayList<>());
        ff2gff3.get(conversionEntry.feature).add(conversionEntry);
        gff32ff.put(conversionEntry.sOTerm, conversionEntry);
        gff32ff.put(conversionEntry.sOID, conversionEntry);
    }

    private void loadMaps() {
        try {
            ff2gff3 = new HashMap<>();
            gff32ff = new HashMap<>();

            List<String> lines = readTsvFile("feature-mapping.tsv");
            lines.remove(0);
            for (String line : lines) {
                String[] parts = line.split("\t");
                ConversionEntry conversionEntry = new ConversionEntry(
                        parts[0].trim(),
                        parts[1],
                        parts[3],
                        Arrays.stream(parts).skip(4).toArray(n -> new String[n]));
                addConversionEntry(conversionEntry);
            }

            ff2gff3Qualifiers = new HashMap<>();
            gff32ffQualifiers = new HashMap<>();
            featureRelations = new HashMap<>();
            lines = readTsvFile("qualifier-mapping.tsv");
            lines.remove(0);
            for (String line : lines) {
                String[] words = line.split("\t");
                ff2gff3Qualifiers.put(words[0], words[1]);
                gff32ffQualifiers.put(words[1], words[0]);
            }

            lines = readTsvFile("feature-parent-child-relation.tsv");
            lines.remove(0);
            for (String line : lines) {
                String[] words = line.split("\t");
                String childFeatureStr = words[0];
                String parentFeatureStr = words[1];
                Set<String> parentList = featureRelations.getOrDefault(childFeatureStr, new HashSet<>());
                parentList.add(parentFeatureStr);

                List<ConversionEntry> parentConversions = ff2gff3.get(parentFeatureStr);
                if (parentConversions != null) {
                    // Add all sOTerms from parentConversions that match the parentFeatureStr
                    for (ConversionEntry entry : parentConversions) {
                        if (entry.feature.equals(parentFeatureStr)) {
                            parentList.add(entry.sOTerm);
                        }
                    }
                }

                List<ConversionEntry> childConversions = ff2gff3.get(childFeatureStr);
                if (childConversions != null) {
                    // For each matching childConversions, map its sOTerm to the parent list
                    for (ConversionEntry entry : childConversions) {
                        if (entry.feature.equals(childFeatureStr)) {
                            featureRelations.put(entry.sOTerm, parentList);
                        }
                    }
                }
                featureRelations.put(childFeatureStr, parentList);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> readTsvFile(String fileName) {
        try (InputStream inputStream = ConversionUtils.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.toList());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading file: " + fileName, e);
        }
    }
}
