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

import io.vavr.Tuple2;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConversionEntry {
    String sOID;
    String sOTerm;
    String definition;
    String feature;
    Map<String, String> qualifiers;

    ConversionEntry(String[] tokens) {
        this.sOID = tokens[0];
        this.sOTerm = tokens[1];
        this.definition = tokens[2];
        this.feature = tokens[3];
        this.qualifiers = new HashMap<>();
        // Splitting only Qualifier1 and Qualifier2 values
        List<String> qualifiersTokens = Arrays.stream(tokens).skip(4).toList();
        for (String token : qualifiersTokens) {
            qualifiers.putAll(parseQualifier(token).apply(Map::of));
        }
    }

    private Tuple2<String, String> parseQualifier(String str) {
        String[] parts = str.split("=");
        String value;
        switch (parts.length) {
            case 2 -> value = parts[1].replaceAll("^\"|\"$", "");
            case 1 -> value = "true";
            default -> throw new RuntimeException("Invalid qualifier format: " + str);
        }
        String key = parts[0].replaceFirst("/", "");
        return new Tuple2<>(key, value);
    }

    public Map<String, String> getQualifiers() {
        return qualifiers;
    }

    public String getSOID() {
        return sOID;
    }

    public String getFeature() {
        return feature;
    }

    public String getSOTerm() {
        return sOTerm;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ConversionEntry{");
        sb.append(", sOTerm='").append(sOTerm).append('\'');
        sb.append(", feature='").append(feature).append('\'');
        sb.append(", qualifiers='").append(qualifiers).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
