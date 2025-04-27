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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3ValidationError;

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

    public static GFF3Feature fromReader(BufferedReader reader) throws GFF3ValidationError, IOException {
        reader.mark(1024);
        String line = reader.readLine();
        String[] parts = line.split("\t");
        if (line.startsWith("#")) {
            reader.reset();
        }
        if (parts.length != 9) {
            throw new GFF3ValidationError("Feature doesn't have 9 columns");
        }

        Map<String, String> attributes = attributesFromString(parts[8]);

        Optional<String> id = attributes.containsKey("ID") ? Optional.of(attributes.get("ID")) : Optional.empty();
        Optional<String> parentId =
                attributes.containsKey("parentID") ? Optional.of(attributes.get("parentID")) : Optional.empty();

        return new GFF3Feature(
                id,
                parentId,
                parts[0],
                parts[1],
                parts[2],
                Long.parseLong(parts[3]),
                Long.parseLong(parts[4]),
                parts[5],
                parts[6],
                parts[7],
                attributesFromString(parts[8]));
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
