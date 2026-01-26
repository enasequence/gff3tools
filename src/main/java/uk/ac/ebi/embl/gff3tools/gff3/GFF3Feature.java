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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
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

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @NonNull
    private Map<String, List<String>> attributes = new HashMap<>();

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

    /**
     * Gets the list of attribute keys present in the feature.
     * @return The list of attributes present.
     */
    public Set<String> getAttributeKeys() {
        return attributes.keySet();
    }

    /**
     * Gets the attributes associated with the specified name.
     * @param name the name of the attribute.
     * @return an Optional containing the list of attribute values, if present.
     */
    public Optional<List<String>> getAttributeList(String name) {
        return Optional.ofNullable(attributes.get(name));
    }

    /**
     * Gets the first attribute associated with the specified name.
     * @param name the name of the attribute.
     * @return an Optional containing the first attribute, if present.
     */
    public Optional<String> getAttribute(String name) {
        return getAttributeList(name).filter((l) -> !l.isEmpty()).map((l) -> l.get(0));
    }

    /**
     * Returns if the feature contains an attribute by the given name
     * @param name the name of the attribute.
     * @return a boolean denoting the presence of the named attribute.
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name) && attributes.get(name) != null;
    }

    /**
     * Sets the attributes for a given name on this feature.
     * If the values is an empty list and an attribute with the given name exists for the feature,
     * the attribute is removed.
     *
     * @param name the name of the attribute.
     * @param values the list of values for the named attribute
     */
    public void setAttributeList(String key, List<String> values) {
        if (values == null) {
            attributes.remove(key);
            return;
        }

        List<String> filtered =
                values.stream().filter(s -> s != null && !s.trim().isBlank()).toList();

        if (filtered.isEmpty()) {
            attributes.remove(key);
        } else {
            attributes.put(key, new ArrayList<>(filtered));
        }
    }

    /**
     * Adds values to the list of attributes for the given name.
     * If no attribute is found for the given name a new list will be created.
     * Makes use of addAttribute
     *
     * @param name the name of the attribute.
     * @param values the list of values to add to this attribute.
     */
    public void addAttributes(String key, List<String> values) {
        if (values != null) {
            for (String value : values) {
                addAttribute(key, value);
            }
        }
    }

    /**
     * Adds attributes to the feature.
     * This function will not existing attributes if they exist.
     *
     * @param values the list of values to add to this attribute.
     */
    public void addAttributes(Map<String, List<String>> values) {
        if (values != null) {
            for (Map.Entry<String, List<String>> entry : values.entrySet()) {
                addAttributes(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Removes the attribute by the given name from this feature.
     * @param name the name of the attribute.
     */
    public void removeAttributeList(String key) {
        attributes.remove(key);
    }

    /**
     * Adds an attribute to the list of attributes for the given name.
     * If no attribute is found for the given name a new list will be created with a single element.
     *
     * @param name the name of the attribute.
     * @param value the value of the attribute.
     */
    public void addAttribute(String name, String value) {
        if (value != null && !value.trim().isBlank()) {
            List<String> attribute = attributes.getOrDefault(name, new ArrayList<>());
            attribute.add(value);
            attributes.put(name, attribute);
        }
    }

    public boolean isFivePrimePartial() {
        return getAttribute(GFF3Attributes.PARTIAL)
                .map((v) -> v.equalsIgnoreCase("start"))
                .orElse(false);
    }

    public boolean isThreePrimePartial() {
        return getAttribute(GFF3Attributes.PARTIAL)
                .map((v) -> v.equalsIgnoreCase("end"))
                .orElse(false);
    }
}
