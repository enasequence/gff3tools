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
package uk.ac.ebi.embl.gff3tools.cli;

import java.util.Arrays;
import picocli.CommandLine;

public enum TranslationMode {
    gff3_fasta("gff3-fasta"),
    fasta("fasta"),
    attribute("attribute");

    private final String cliName;

    TranslationMode(String cliName) {
        this.cliName = cliName;
    }

    @Override
    public String toString() {
        return cliName;
    }

    /**
     * Custom picocli converter that accepts only the hyphenated CLI names
     * (e.g. {@code gff3-fasta}, not {@code gff3_fasta}).
     */
    public static class Converter implements CommandLine.ITypeConverter<TranslationMode> {
        @Override
        public TranslationMode convert(String value) {
            for (TranslationMode mode : values()) {
                if (mode.cliName.equals(value)) {
                    return mode;
                }
            }
            throw new CommandLine.TypeConversionException(
                    "expected one of " + Arrays.toString(TranslationMode.values()) + " but was '" + value + "'");
        }
    }
}
