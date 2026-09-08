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

/**
 * Shared key format for identifying translations by accession and feature ID.
 *
 * <p>Used by both the FASTA translation writer (for header keys) and the
 * validation layer (for old/new translation comparison state).
 */
public final class TranslationKey {

    private TranslationKey() {}

    /**
     * Build a translation key from an accession and feature ID.
     *
     * @param accession the sequence accession (including version suffix)
     * @param featureId the feature ID
     * @return a key in the format {@code accession|urlEncodedFeatureId}
     */
    public static String of(String accession, String featureId) {
        return accession + "|" + GFF3Annotation.urlEncode(featureId);
    }

    /**
     * The accession part of a translation key, or {@code null} when the key is not in the expected
     * format.
     *
     * <p>Split on the first separator: an accession never contains {@code |}, and feature IDs are
     * URL-encoded by {@link #of}, so the first one always delimits the two parts.
     *
     * @param key a key produced by {@link #of}, or read from a {@code ##FASTA} header
     * @return the accession, or {@code null} if the key has no separator
     */
    public static String accessionOf(String key) {
        if (key == null) {
            return null;
        }
        int separator = key.indexOf('|');
        return separator > 0 ? key.substring(0, separator) : null;
    }

    /**
     * Whether a translation key belongs to the given accession.
     *
     * <p>Matching is exact rather than by prefix: {@code AB123.1} must not claim the translations
     * of {@code AB123.10}. An accession recorded without its version therefore matches nothing
     * rather than matching every version — a missing translation is a visible failure, a
     * misattributed one is not.
     */
    public static boolean belongsTo(String key, String accession) {
        return accession != null && accession.equals(accessionOf(key));
    }
}
