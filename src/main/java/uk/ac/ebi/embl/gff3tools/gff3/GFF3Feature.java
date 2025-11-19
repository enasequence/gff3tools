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
    final String seqId;
    final Optional<Integer> seqIdVersion;
    final String source;
    final String name;
    final long start;
    final long end;
    final String score;
    final String strand;
    final String phase;
    final Map<String, List<String>> attributes;

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
        // TODO Do we need to do this?
        Map<String, List<String>> hashAttributes = new HashMap<>(attributes);
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

    private String getAttributeString(Map<String, List<String>> attributes) {
        StringBuilder attrBuilder = new StringBuilder();

        attributes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String key = entry.getKey();
            List<String> list = entry.getValue();

            for (String value : list) {
                attrBuilder.append(key).append("=").append(value).append(";");
                attrBuilder.append(list);
                attrBuilder.append(";");
            }
        });
        return attrBuilder.toString();
    }

    public long getLength() {
        return Math.max(end - start + 1, 0);
    }

    public Optional<List<String>> getAttributeByName(String name) {
        return Optional.of(attributes.get(name));
    }

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name) && attributes.get(name) != null;
    }

    public void setAttributeValueList(String key, List<String> values) {
        values.removeIf(s -> s == null || s.trim().isBlank()); // remove empty bits
        if (values.isEmpty()) {
            attributes.remove(key);
        } else {
            attributes.put(key, values);
        }
    }

    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    public void setAttribute(String name, String value) {
        if (value != null && !value.trim().isBlank()) {
            attributes.getOrDefault(name, new ArrayList<String>()).add(value.trim());
        }
    }

    public boolean isFivePrimePartial() {
        List<String> partialValue = getAttributeValueList(GFF3Attributes.PARTIAL);
        return partialValue.size() == 1 && "start".equalsIgnoreCase(partialValue.get(0));
    }

    public boolean isThreePrimePartial() {
        List<String> partialValue = getAttributeValueList(GFF3Attributes.PARTIAL);
        return partialValue.size() == 1 && "end".equalsIgnoreCase(partialValue.get(0));
    }
}
