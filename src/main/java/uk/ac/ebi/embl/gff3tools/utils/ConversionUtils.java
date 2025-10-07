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

import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public enum ConversionUtils {
    INSTANCE;
    private Map<String, List<ConversionEntry>> ff2gff3 = null;
    private Map<String, ConversionEntry> gff32ff = null;
    private Map<String, String> ff2gff3Qualifiers = null;
    private Map<String, String> gff32ffQualifiers = null;
    // Map of child : list of possible parents
    // Uses sOTerms (gff3 feature names)
    private Map<String, Set<String>> featureRelations = null;

    private ConversionUtils() {
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

    public static Map<String, ConversionEntry> getGFF32FFFeatureMap() {
        return INSTANCE.gff32ff;
    }

    public static Map<String, String> getGFF32FFQualifierMap() {
        return INSTANCE.gff32ffQualifiers;
    }

    private void loadMaps() {
        try {
            ff2gff3 = new HashMap<>();
            gff32ff = new HashMap<>();
            List<String> lines = readTsvFile("feature-mapping.tsv");
            lines.remove(0);
            for (String line : lines) {
                ConversionEntry conversionEntry = new ConversionEntry(line.split("\t"));
                ff2gff3.putIfAbsent(conversionEntry.feature, new ArrayList<>());
                ff2gff3.get(conversionEntry.feature).add(conversionEntry);
                gff32ff.putIfAbsent(conversionEntry.sOID, conversionEntry);
                gff32ff.putIfAbsent(conversionEntry.sOTerm, conversionEntry);
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

    public static Optional<String> getGFF3FeatureName(Feature ffFeature) {
        String featureName = ffFeature.getName();
        List<ConversionEntry> mappings = Optional.ofNullable(
                        ConversionUtils.getFF2GFF3FeatureMap().get(featureName))
                .orElse(Collections.emptyList());

        return mappings.stream()
                .filter(entry -> entry.getFeature().equalsIgnoreCase(ffFeature.getName()))
                .filter(entry -> hasAllQualifiers(ffFeature, entry))
                .max(Comparator.comparingInt(entry -> entry.getQualifiers().size()))
                .map(ConversionEntry::getSOTerm);
    }

    private static boolean hasAllQualifiers(Feature feature, ConversionEntry conversionEntry) {
        Map<String, String> requiredQualifiers = conversionEntry.getQualifiers();

        boolean matchesAllQualifiers = true;
        for (String expectedQualifierName : requiredQualifiers.keySet()) {
            boolean qualifierMatches = false;
            for (Qualifier featureQualifier : feature.getQualifiers(expectedQualifierName)) {
                // When qualifier value is not found the value is considered "true"
                String qualifierValue = featureQualifier.getValue() == null ? "true" : featureQualifier.getValue();
                qualifierMatches = qualifierValue.equalsIgnoreCase(requiredQualifiers.get(expectedQualifierName));
                if (qualifierMatches) {
                    break;
                }
            }
            matchesAllQualifiers = qualifierMatches;
            if (!matchesAllQualifiers) {
                break;
            }
        }
        return matchesAllQualifiers;
    }
}
