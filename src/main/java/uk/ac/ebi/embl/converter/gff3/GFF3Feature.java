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
    final Map<String, Object> attributes;

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

    @Override
    public int hashCode() {
        Map<String, Object> hashAttributes = new HashMap<>(attributes);
        // Removing partial from the attribute as it can change for the first and last location in a compound Join
        hashAttributes.remove("partial");

        return Objects.hash(
                id, parentId, accession, source, name, score, strand, phase, getAttributeString(hashAttributes));
    }

    private String getAttributeString(Map<String, Object> attributes) {
        StringBuilder attrBuilder = new StringBuilder();

        attributes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey();
            Object value = entry.getValue();

            attrBuilder.append(key).append("=");

            if (value instanceof List) {
                List<?> list = new ArrayList<>((List<?>) value);
                list.sort(Comparator.comparing(Object::toString));
                attrBuilder.append(list);
            } else {
                attrBuilder.append(value);
            }

            attrBuilder.append(";");
        });
        return attrBuilder.toString();
    }
}
