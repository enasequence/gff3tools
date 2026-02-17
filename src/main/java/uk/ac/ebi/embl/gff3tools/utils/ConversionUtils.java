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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ConversionUtils {
    INSTANCE;
    private Map<String, List<ConversionEntry>> ff2gff3 = null;
    private Map<String, List<ConversionEntry>> gff32ff = null;
    private Map<String, String> ff2gff3Qualifiers = null;
    private Map<String, String> gff32ffQualifiers = null;
    // Map of child : list of possible parents
    // Uses sOTerms (gff3 feature names)
    private Map<String, Set<String>> featureRelations = null;
    private OntologyClient ontologyClient = null;
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionUtils.class);

    // Pattern to detect wildcard placeholders like <NAME> or <length of feature> anywhere in a value
    public static final Pattern WILDCARD_TEXT = Pattern.compile("<[^>]+>");

    /**
     * Checks whether an actual qualifier value matches an expected value that may contain
     * wildcard placeholders or glob patterns.
     *
     * <p>Supported wildcard types:
     * <ul>
     *   <li>{@code <NAME>} — named placeholder, matches <b>1 or more</b> characters</li>
     *   <li>{@code *} — glob wildcard, matches <b>0 or more</b> characters</li>
     * </ul>
     *
     * <p>Uses string prefix/suffix matching instead of regex for performance, since this is
     * called per qualifier per feature (potentially millions of times for large files).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "transposon*"} matches {@code "transposon"}, {@code "transposon:Ac"}</li>
     *   <li>{@code "<length of feature>"} matches {@code "91"} (any non-empty value)</li>
     *   <li>{@code "<NAME>"} matches {@code "anything"} (any non-empty value)</li>
     * </ul>
     *
     * @param expectedValue the expected value from the mapping TSV, possibly with wildcards
     * @param actualValue the actual qualifier value from the feature
     * @return true if the actual value matches the expected value (with wildcard expansion)
     */
    public static boolean matchesWildcardValue(String expectedValue, String actualValue) {
        boolean hasNamedWildcard = WILDCARD_TEXT.matcher(expectedValue).find();
        boolean hasGlob = expectedValue.contains("*");

        if (!hasNamedWildcard && !hasGlob) {
            return actualValue.equalsIgnoreCase(expectedValue);
        }

        if (hasNamedWildcard) {
            return matchesNamedWildcard(expectedValue, actualValue);
        }

        return matchesGlob(expectedValue, actualValue);
    }

    /**
     * Matches a named wildcard pattern like {@code "prefix<NAME>suffix"}.
     * The wildcard must match at least one character.
     */
    private static boolean matchesNamedWildcard(String expectedValue, String actualValue) {
        int wildcardStart = expectedValue.indexOf('<');
        String prefix = expectedValue.substring(0, wildcardStart);

        int wildcardEnd = expectedValue.lastIndexOf('>');
        String suffix = expectedValue.substring(wildcardEnd + 1);

        // The actual value must be at least as long as the literal parts combined,
        // plus at least one character for the wildcard
        if (actualValue.length() < prefix.length() + suffix.length() + 1) {
            return false;
        }

        return actualValue.toLowerCase().startsWith(prefix.toLowerCase())
                && actualValue.toLowerCase().endsWith(suffix.toLowerCase());
    }

    /**
     * Matches a glob pattern where {@code *} matches zero or more characters.
     * Only supports a single {@code *} at the end of the pattern (trailing glob).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "transposon*"} matches {@code "transposon"}, {@code "transposon:Ac"}</li>
     *   <li>{@code "*"} matches any value including empty string</li>
     * </ul>
     */
    private static boolean matchesGlob(String expectedValue, String actualValue) {
        int starIndex = expectedValue.indexOf('*');
        String prefix = expectedValue.substring(0, starIndex);
        String suffix = expectedValue.substring(starIndex + 1);

        if (actualValue.length() < prefix.length() + suffix.length()) {
            return false;
        }

        return actualValue.toLowerCase().startsWith(prefix.toLowerCase())
                && actualValue.toLowerCase().endsWith(suffix.toLowerCase());
    }

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

    /**
     * Returns the EMBL feature (ConversionEntry) for an SO term, considering GFF3 attributes
     * for qualifier-based selection when multiple mappings exist for the same SO term.
     *
     * @param SOTerm the SO term (e.g., "gap") or SO ID (e.g., "SO:0000730")
     * @param gff3Attributes the attributes from the GFF3 feature, used for qualifier matching
     * @return the best matching ConversionEntry, or null if no mapping found
     */
    public static ConversionEntry getINSDCFeatureForSOTerm(String SOTerm, Map<String, List<String>> gff3Attributes) {
        List<ConversionEntry> candidates = INSTANCE.gff32ff.get(SOTerm);

        if (candidates == null || candidates.isEmpty()) {
            LOGGER.info("SOTerm \"%s\" not found in tsv mapping. Search for matches using ontology parents"
                    .formatted(SOTerm));
            Stream<String> parents = INSTANCE.ontologyClient.getParents(SOTerm);
            for (Iterator<String> it = parents.iterator(); it.hasNext(); ) {
                String parent = it.next();
                candidates = INSTANCE.gff32ff.get(parent);
                if (candidates != null && !candidates.isEmpty()) {
                    LOGGER.info("SOTerm \"%s\" found ontology parent \"%s\" with %d candidate(s)"
                            .formatted(SOTerm, parent, candidates.size()));
                    break;
                }
            }
        }

        ConversionEntry result = selectBestMatch(candidates, gff3Attributes);
        if (result != null) {
            LOGGER.debug("SOTerm \"%s\" mapped to INSDC feature \"%s\"".formatted(SOTerm, result.getFeature()));
        }
        return result;
    }

    /**
     * Selects the best matching ConversionEntry from candidates based on qualifier matching.
     * Prefers entries with the most matching qualifiers. Falls back to entries with no
     * qualifier requirements if no specific match is found.
     *
     * @return the best matching entry, or null if candidates is null or empty
     */
    private static ConversionEntry selectBestMatch(
            List<ConversionEntry> candidates, Map<String, List<String>> gff3Attributes) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .filter(entry -> hasAllRequiredQualifiers(entry, gff3Attributes))
                .max(Comparator.comparingInt(entry -> entry.getQualifiers().size()))
                .orElseGet(
                        () ->
                                // Fallback: entry with no qualifier requirements
                                candidates.stream()
                                        .filter(entry -> entry.getQualifiers().isEmpty())
                                        .findFirst()
                                        .orElse(candidates.get(0)) // Last resort: first candidate
                        );
    }

    /**
     * Checks if all required qualifiers from the ConversionEntry are present in the GFF3 attributes.
     * Supports wildcard matching for qualifier values (e.g., {@code <length of feature>}).
     *
     * <p>Note: This method mirrors {@link uk.ac.ebi.embl.gff3tools.fftogff3.FeatureMapping#hasAllQualifiers}
     * which performs the same logic for the FF→GFF3 direction using EMBL Feature objects.
     */
    private static boolean hasAllRequiredQualifiers(ConversionEntry entry, Map<String, List<String>> gff3Attributes) {
        Map<String, String> requiredQualifiers = entry.getQualifiers();

        for (Map.Entry<String, String> required : requiredQualifiers.entrySet()) {
            String qualifierName = required.getKey();
            String expectedValue = required.getValue();

            List<String> actualValues = gff3Attributes.get(qualifierName);
            if (actualValues == null || actualValues.isEmpty()) {
                return false;
            }

            // Check if any actual value matches (supports wildcards like "transposon*" or "<NAME>")
            boolean matches =
                    actualValues.stream().anyMatch(actualValue -> matchesWildcardValue(expectedValue, actualValue));

            if (!matches) {
                return false;
            }
        }
        return true;
    }

    public static OntologyClient getOntologyClient() {
        return INSTANCE.ontologyClient;
    }

    private void addConversionEntry(ConversionEntry conversionEntry) {
        ff2gff3.putIfAbsent(conversionEntry.feature, new ArrayList<>());
        ff2gff3.get(conversionEntry.feature).add(conversionEntry);
        gff32ff.computeIfAbsent(conversionEntry.sOTerm, k -> new ArrayList<>()).add(conversionEntry);
        gff32ff.computeIfAbsent(conversionEntry.sOID, k -> new ArrayList<>()).add(conversionEntry);
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
