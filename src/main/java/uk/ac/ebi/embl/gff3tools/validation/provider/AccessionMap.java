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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps old accessions to new ones. Supports two modes:
 *
 * <ul>
 *   <li><b>Map mode</b> — constructed with an explicit old→new mapping.
 *   <li><b>List mode</b> — constructed with an ordered list of new accessions.
 *       Each previously-unseen old accession is assigned the next new accession from the list.
 * </ul>
 *
 * <p>Needed as a distinct type because {@link uk.ac.ebi.embl.gff3tools.validation.ValidationContext}
 * uses {@code Class<T>} as key, and raw {@code Map.class} is too generic.
 */
public class AccessionMap {

    private final Map<String, String> map;
    private final List<String> accessionList;
    private int nextIndex;

    /** Map mode: explicit old→new mapping. */
    public AccessionMap(Map<String, String> map) {
        this.map = map;
        this.accessionList = null;
    }

    /** List mode: new accessions assigned on demand as old accessions are encountered. */
    public AccessionMap(List<String> accessions) {
        this.map = new LinkedHashMap<>();
        this.accessionList = accessions;
        this.nextIndex = 0;
    }

    /** Returns the new accession for the given old accession, or {@code null} if not mapped. */
    public String get(String oldAccession) {
        String mapped = map.get(oldAccession);
        if (mapped != null) {
            return mapped;
        }
        if (accessionList != null && !map.containsKey(oldAccession) && nextIndex < accessionList.size()) {
            mapped = accessionList.get(nextIndex++);
            map.put(oldAccession, mapped);
            return mapped;
        }
        return null;
    }

    /** Returns the resolved mapping (in list mode, only contains assignments made so far). */
    public Map<String, String> getMap() {
        return map;
    }

    /** Returns the number of new accessions provided (list mode only). */
    public int providedCount() {
        return accessionList != null ? accessionList.size() : map.size();
    }

    /** Returns the number of old accessions that have been assigned so far. */
    public int assignedCount() {
        return map.size();
    }
}
