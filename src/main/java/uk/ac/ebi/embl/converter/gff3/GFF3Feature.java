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
package uk.ac.ebi.embl.converter.gff3;

import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
@Setter
public class GFF3Feature {
    // Non-Mutable members used in constructor
    final Optional<String> id;
    final Optional<String> parentId;
    final String accession;
    final String source;
    final String name;
    final long start;
    final long end;
    final String score;
    final String strand;
    final String phase;
    final Map<String, String> attributes;

    // Mutable members
    List<GFF3Feature> children = new ArrayList<>();
    GFF3Feature parent;

    // Methods
    public void addChild(GFF3Feature child) {
        children.add(child);
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public static GFF3Feature fromString(String line) {
        String[] parts = line.split("\t");
        Optional<String> id = Optional.empty();
        if (parts.length > 0) {
            id = Optional.of(parts[0]);
        }

        Optional<String> parentId = Optional.empty();
        if (parts.length > 0) {
            parentId = Optional.of(parts[1]);
        }
        return new GFF3Feature(
                id,
                parentId,
                parts[2],
                parts[3],
                parts[4],
                Long.parseLong(parts[5]),
                Long.parseLong(parts[6]),
                parts[7],
                parts[8],
                parts[9],
                attributesFromString(parts[10]));
    }

    private static Map<String, String> attributesFromString(String line) {
        Map<String, String> attributes = new HashMap<>();
        String[] parts = line.split(";");
        for (String part : parts) {
            String[] keyValue = part.split("=");
            attributes.put(keyValue[0], keyValue[1]);
        }
        return attributes;
    }
}
