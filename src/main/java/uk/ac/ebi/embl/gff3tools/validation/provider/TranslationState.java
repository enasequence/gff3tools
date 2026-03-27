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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state for old/new translation pairs, allowing {@code TranslationFix} to record
 * translations and {@code TranslationComparisonValidation} to compare them.
 */
public class TranslationState {

    public record TranslationEntry(String oldTranslation, String newTranslation) {}

    private final Map<String, TranslationEntry> entries = new ConcurrentHashMap<>();

    /**
     * Build a consistent lookup key for a feature.
     *
     * @param seqId the sequence ID
     * @param featureId the feature ID, or null if absent
     * @param line the GFF3 line number (used as fallback when featureId is null)
     */
    public static String buildKey(String seqId, String featureId, int line) {
        String id = featureId != null ? featureId : "line_" + line;
        return seqId + ":" + id;
    }

    /** Record old and new translations for a feature. */
    public void record(String key, String oldTranslation, String newTranslation) {
        entries.put(key, new TranslationEntry(oldTranslation, newTranslation));
    }

    /** Retrieve the recorded entry, or {@code null} if not present. */
    public TranslationEntry get(String key) {
        return entries.get(key);
    }
}
