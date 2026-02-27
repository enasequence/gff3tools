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
package uk.ac.ebi.embl.gff3tools.fftogff3;

import java.util.*;
import java.util.stream.Stream;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.api.entry.qualifier.Qualifier;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.utils.ConversionEntry;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;

public class FeatureMapping {

    public static String getGFF3FeatureName(Feature ffFeature) throws ValidationException {
        String featureName = ffFeature.getName();
        List<ConversionEntry> mappings = Optional.ofNullable(
                        ConversionUtils.getFF2GFF3FeatureMap().get(featureName))
                .orElse(Collections.emptyList());

        return mappings.stream()
                .filter(entry -> entry.getFeature().equalsIgnoreCase(ffFeature.getName()))
                .filter(entry -> hasAllQualifiers(ffFeature, entry))
                .max(Comparator.comparingInt(entry -> entry.getQualifiers().size()))
                .map(ConversionEntry::getSOTerm)
                .orElseThrow(() -> new ValidationException(
                        "There is no SO Term mapping for INSDC feature \"%s\"".formatted(featureName)));
    }

    public static Stream<String> getGFF3FeatureCandidateIdsAndNames(String ffFeatureName) {
        return Stream.concat(getGFF3FeatureCandidateISOIDs(ffFeatureName), getGFF3FeatureCandidateNames(ffFeatureName));
    }

    public static Stream<String> getGFF3FeatureCandidateNames(String ffFeatureName) {
        return Optional.ofNullable(ConversionUtils.getFF2GFF3FeatureMap().get(ffFeatureName))
                .orElse(Collections.emptyList())
                .stream()
                .map(ConversionEntry::getSOTerm);
    }

    public static Stream<String> getGFF3FeatureCandidateNamesNoQualifiersRequired(String ffFeatureName) {
        return Optional.ofNullable(ConversionUtils.getFF2GFF3FeatureMap().get(ffFeatureName))
                .orElse(Collections.emptyList())
                .stream()
                .filter(entry -> entry.getQualifiers().isEmpty())
                .map(ConversionEntry::getSOTerm);
    }

    public static Stream<String> getGFF3FeatureCandidateISOIDs(String ffFeatureName) {
        return Optional.ofNullable(ConversionUtils.getFF2GFF3FeatureMap().get(ffFeatureName))
                .orElse(Collections.emptyList())
                .stream()
                .map(ConversionEntry::getSOID);
    }

    public static Stream<String> getGFF3FeatureCandidateISOIDsNoQualifiersRequired(String ffFeatureName) {
        return Optional.ofNullable(ConversionUtils.getFF2GFF3FeatureMap().get(ffFeatureName))
                .orElse(Collections.emptyList())
                .stream()
                .filter(entry -> entry.getQualifiers().isEmpty())
                .map(ConversionEntry::getSOID);
    }

    public static Optional<String> getGFF3Attribute(String ffQualifierName) {
        return Optional.ofNullable(ConversionUtils.getFF2GFF3QualifierMap().get(ffQualifierName));
    }

    private static boolean hasAllQualifiers(Feature feature, ConversionEntry conversionEntry) {
        Map<String, String> requiredQualifiers = conversionEntry.getQualifiers();

        boolean matchesAllQualifiers = true;
        for (String expectedQualifierName : requiredQualifiers.keySet()) {
            boolean qualifierMatches = false;
            for (Qualifier featureQualifier : feature.getQualifiers(expectedQualifierName)) {
                // When qualifier value is not found the value is considered "true"
                String qualifierValue = featureQualifier.getValue() == null ? "true" : featureQualifier.getValue();
                String expectedQualifierValue = requiredQualifiers.get(expectedQualifierName);
                // Matches exact values or values with wildcards like "transposon*" or "<NAME>"
                qualifierMatches = ConversionUtils.matchesWildcardValue(expectedQualifierValue, qualifierValue);
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
