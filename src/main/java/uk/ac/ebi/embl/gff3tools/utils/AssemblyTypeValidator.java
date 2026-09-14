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
package uk.ac.ebi.embl.gff3tools.utils;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import uk.ac.ebi.embl.api.entry.genomeassembly.AssemblyType;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;

/**
 * Resolves the caller-supplied {@code ASSEMBLY_TYPE} manifest value to an {@link AssemblyType}.
 *
 * <p>The vocabulary is owned by {@code sequencetools}, so the accepted spellings track ENA's
 * rather than being maintained here.
 */
public final class AssemblyTypeValidator {

    private AssemblyTypeValidator() {}

    /**
     * Resolves the raw manifest value against {@link AssemblyType}, matching either the manifest
     * spelling ({@code CLONE OR ISOLATE}) or the canonical one ({@code clone or isolate}),
     * case-insensitively and ignoring surrounding whitespace.
     *
     * <p>A blank value counts as not supplied: a pipeline that always forwards the manifest field
     * should not have to distinguish "absent" from "empty".
     *
     * @param analysisType    the analysis type the assembly type was supplied alongside
     * @param rawAssemblyType the raw manifest value, or {@code null}/blank when unavailable
     * @return the resolved assembly type, or empty when nothing was supplied
     * @throws IllegalArgumentException if a value is supplied for an analysis type other than
     *                                  {@link AnalysisType#SEQUENCE_ASSEMBLY}, or if it does not
     *                                  match a known assembly type
     */
    public static Optional<AssemblyType> parseAndValidate(AnalysisType analysisType, String rawAssemblyType) {
        if (rawAssemblyType == null || rawAssemblyType.isBlank()) {
            return Optional.empty();
        }

        if (analysisType != AnalysisType.SEQUENCE_ASSEMBLY) {
            throw new IllegalArgumentException("assemblyType \"%s\" must not be supplied for analysis type %s, only %s"
                    .formatted(rawAssemblyType, analysisType, AnalysisType.SEQUENCE_ASSEMBLY));
        }

        String value = rawAssemblyType.trim();
        return Optional.of(Arrays.stream(AssemblyType.values())
                .filter(candidate -> candidate.getValue().equalsIgnoreCase(value)
                        || candidate.getFixedValue().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown assemblyType \"%s\", expected one of: %s"
                        .formatted(rawAssemblyType, allowedValues()))));
    }

    /** The accepted values in their canonical spelling, for error messages. */
    private static String allowedValues() {
        return Arrays.stream(AssemblyType.values())
                .map(AssemblyType::getFixedValue)
                .collect(Collectors.joining(", "));
    }
}
