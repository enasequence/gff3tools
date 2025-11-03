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
        String value = (String) attributes.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name) && attributes.get(name) != null;
    }

    public List<String> getAttributeValueList(String key) {
        Object value = attributes.get(key);
        if (value == null) return List.of();

        if (value instanceof List<?>) {
            return (List<String>) value;
        } else {
            List<String> out = new ArrayList<>();
            out.add(value.toString());
            return out;
        }
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

    public List<String> getAttributeValueList(String name) {
        Object value = attributes.get(name);
        if (value == null) return List.of();

        List<String> out = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item != null) out.add(item.toString().trim());
            }
        } else {
            out.add(value.toString());
        }

        return out;
    }

    public void setAttributeValueList(String note, List<String> values) {
        if (values.size() == 1) {
            attributes.put(note, values.get(0));
        } else if (values.isEmpty()) {
            attributes.remove(note);
        } else {
            attributes.put(note, values);
        }
    }
}
