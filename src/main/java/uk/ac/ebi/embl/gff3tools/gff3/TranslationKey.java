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
}
