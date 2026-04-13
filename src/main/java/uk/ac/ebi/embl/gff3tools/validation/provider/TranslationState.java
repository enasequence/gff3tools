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
import java.util.Map;
import java.util.function.BiConsumer;
import uk.ac.ebi.embl.gff3tools.gff3.TranslationKey;

/**
 * Shared state for old/new translation pairs, allowing {@code TranslationFix} to record
 * translations and {@code TranslationComparisonValidation} to compare them.
 */
public class TranslationState {

    public record TranslationEntry(String oldTranslation, String newTranslation) {}

    private final Map<String, TranslationEntry> entries = new LinkedHashMap<>();

    /**
     * Build a consistent lookup key for a feature.
     * Returns {@code null} when {@code featureId} is absent — callers should
     * skip recording/lookup in that case since ID-less features cannot be
     * reliably matched between the fix and validation passes.
     *
     * @param accession the sequence accession (including version suffix)
     * @param featureId the feature ID, or null if absent
     * @return a translation key, or {@code null} if featureId is null
     */
    public static String buildKey(String accession, String featureId) {
        if (featureId == null) {
            return null;
        }
        return TranslationKey.of(accession, featureId);
    }

    /** Record old and new translations for a feature. */
    public void record(String key, String oldTranslation, String newTranslation) {
        entries.put(key, new TranslationEntry(oldTranslation, newTranslation));
    }

    /** Retrieve the recorded entry, or {@code null} if not present. */
    public TranslationEntry get(String key) {
        return entries.get(key);
    }

    /** Iterate all recorded translation entries. */
    public void forEach(BiConsumer<String, TranslationEntry> action) {
        entries.forEach(action);
    }

    /**
     * Iterate resolved translations: prefers {@code newTranslation}, falls back to
     * {@code oldTranslation}, skips entries where both are null/empty.
     */
    public void forEachResolved(BiConsumer<String, String> action) {
        entries.forEach((key, entry) -> {
            String translation = entry.newTranslation();
            if (translation == null || translation.isEmpty()) {
                translation = entry.oldTranslation();
            }
            if (translation != null && !translation.isEmpty()) {
                action.accept(key, translation);
            }
        });
    }
}
