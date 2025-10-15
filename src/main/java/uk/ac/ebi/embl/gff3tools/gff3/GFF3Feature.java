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
package uk.ac.ebi.embl.gff3tools.gff3;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
@Getter
@Setter
@Builder(toBuilder = true)
public class GFF3Feature {
    // Non-Mutable members used in constructor
    final Optional<String> id;
    final Optional<String> parentId;
    final String seqId;
    final Optional<Integer> seqIdVersion;
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

    public String accession() {
        String versionSuffix = seqIdVersion.map(v -> "." + v).orElse("");
        return seqId + versionSuffix;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public String hashCodeString() {
        Map<String, Object> hashAttributes = new HashMap<>(attributes);
        // Removing partial from the attribute as it can change for the first and last
        // location in a compound Join
        hashAttributes.remove("partial");

        String hashCodeStr = String.join(
                "|",
                id.orElse(""),
                parentId.orElse(""),
                seqId,
                source,
                name,
                score,
                phase,
                getAttributeString(hashAttributes));

        return getSHA256Hash(hashCodeStr);
    }

    private String getSHA256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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
                attrBuilder.append(value.toString());
            }

            attrBuilder.append(";");
        });
        return attrBuilder.toString();
    }

    public long getLength() {
        return Math.max(end - start + 1, 0);
    }

    public String getAttributeByName(String name) {
        return (String) attributes.get(name);
    }

    public GFF3Feature setAttribute(Map<String, Object> updatedAttributes, GFF3Feature feature) {
        return feature.toBuilder().attributes(Map.copyOf(updatedAttributes)).build();
    }
}
