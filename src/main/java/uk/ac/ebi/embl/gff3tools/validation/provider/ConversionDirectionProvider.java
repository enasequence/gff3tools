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

import uk.ac.ebi.embl.gff3tools.cli.ConversionFileFormat;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Tells fixes and validations which conversion direction (if any) the engine is running for.
 *
 * <p>The no-arg constructor is used by the classpath scan in {@code ValidationRegistry} and
 * represents "no conversion" (e.g. plain GFF3 validation). {@code FileConversionCommand}
 * registers an instance with the real direction explicitly, which takes precedence.
 */
public class ConversionDirectionProvider implements ContextProvider<ConversionDirectionProvider> {

    private final ConversionFileFormat fromFileType;
    private final ConversionFileFormat toFileType;

    /** No conversion direction; used for validation-only runs. */
    public ConversionDirectionProvider() {
        this(null, null);
    }

    public ConversionDirectionProvider(ConversionFileFormat fromFileType, ConversionFileFormat toFileType) {
        this.fromFileType = fromFileType;
        this.toFileType = toFileType;
    }

    /** True when converting an EMBL flat file to GFF3. */
    public boolean isFlatfileToGff3() {
        return fromFileType == ConversionFileFormat.embl && toFileType == ConversionFileFormat.gff3;
    }

    @Override
    public ConversionDirectionProvider get(ValidationContext context) {
        return this;
    }

    @Override
    public Class<ConversionDirectionProvider> type() {
        return ConversionDirectionProvider.class;
    }
}
