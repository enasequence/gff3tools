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
package uk.ac.ebi.embl.gff3tools.metrics;

import java.util.Locale;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Output format of the {@code --metrics} report. */
public enum MetricsFormat {
    JSON,
    TEXT;

    /**
     * Resolves the report format: an explicit {@code --metrics-format} wins; otherwise the format
     * follows the destination — human-readable text for the terminal ('-'), JSON for files.
     */
    public static MetricsFormat resolve(MetricsFormat format, boolean toStderr) {
        if (format != null) {
            return format;
        }
        return toStderr ? TEXT : JSON;
    }

    /** Case-insensitive CLI parsing for {@code --metrics-format}; picocli's built-in is exact-match. */
    public static MetricsFormat fromCli(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "json" -> JSON;
            case "text" -> TEXT;
            default -> throw new TypeConversionException("Invalid value '%s': expected json or text".formatted(value));
        };
    }

    /** picocli converter wired on the {@code --metrics-format} option. */
    public static class Converter implements ITypeConverter<MetricsFormat> {
        @Override
        public MetricsFormat convert(String value) {
            return fromCli(value);
        }
    }
}
