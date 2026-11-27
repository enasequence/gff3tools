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

import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3TranslationReader;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Supplies the {@link GFF3TranslationReader} of the GFF3 file being read, so validations can check
 * that its {@code ##FASTA} section is readable and its headers match features. Registered by the
 * GFF3 file reader.
 *
 * <p>The instance the classpath scan creates has no reader and is inactive, so it is never
 * registered. The reader is closed by its GFF3 file reader, not here.
 */
public class GFF3TranslationReaderProvider implements ContextProvider<GFF3TranslationReader> {

    private final GFF3TranslationReader reader;

    /** Used by the classpath scan only. */
    public GFF3TranslationReaderProvider() {
        this(null);
    }

    public GFF3TranslationReaderProvider(GFF3TranslationReader reader) {
        this.reader = reader;
    }

    @Override
    public GFF3TranslationReader get(ValidationContext context) {
        return reader;
    }

    @Override
    public Class<GFF3TranslationReader> type() {
        return GFF3TranslationReader.class;
    }

    @Override
    public boolean isActive() {
        return reader != null;
    }
}
