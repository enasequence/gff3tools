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

import java.util.Set;
import java.util.function.Supplier;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * The translations the source GFF3 file carries in its {@code ##FASTA} section, keyed as
 * {@code accession|featureId}. {@code GFF3FileReader} registers one for its file. The
 * auto-discovered instance is empty, for input that has no such section (a flat file, a TSV, or
 * GFF3 read from stdin).
 *
 * <p>The keys are read on first use: finding them scans the file backwards from its end.
 */
public class SourceTranslations implements ContextProvider<SourceTranslations> {

    private final Supplier<Set<String>> keySupplier;
    private Set<String> keys;

    public SourceTranslations() {
        this(Set::of);
    }

    public SourceTranslations(Supplier<Set<String>> keySupplier) {
        this.keySupplier = keySupplier;
    }

    /** Whether the source file has a translation for the given key. */
    public boolean contains(String key) {
        if (key == null) {
            return false;
        }
        if (keys == null) {
            keys = keySupplier.get();
        }
        return keys.contains(key);
    }

    @Override
    public SourceTranslations get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<SourceTranslations> type() {
        return SourceTranslations.class;
    }
}
