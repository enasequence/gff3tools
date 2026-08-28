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

import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import uk.ac.ebi.embl.api.entry.genomeassembly.AssemblyType;
import uk.ac.ebi.embl.gff3tools.utils.AssemblyTypeValidator;

/**
 * User-supplied analysis settings made available to fixes and validators via the
 * validation context.
 *
 * <p>The {@link AnalysisType} type, the minimum gap size and the assembly type are all external
 * input. The minimum gap size must always be greater than zero. The assembly type is optional.
 */
public final class AnalysisContext {

    @Getter
    private final AnalysisType analysisType;

    /*** Only available when supplied for {@link AnalysisType}.{@code SEQUENCE_ASSEMBLY} */
    @Getter
    private final Optional<AssemblyType> assemblyType;

    @Getter
    private final int minGapSize;

    /**
     * @param analysisType the analysis type (must not be {@code null})
     * @param minGapSize   the minimum gap size (must be greater than zero)
     * @throws NullPointerException     if {@code analysisType} is {@code null}
     * @throws IllegalArgumentException if {@code minGapSize} is not greater than zero
     */
    public AnalysisContext(AnalysisType analysisType, int minGapSize) {
        this(analysisType, minGapSize, null);
    }

    /**
     * @param analysisType the analysis type (must not be {@code null})
     * @param minGapSize   the minimum gap size (must be greater than zero)
     * @param assemblyType the raw assembly type, or {@code null}/blank when unavailable
     * @throws NullPointerException     if {@code analysisType} is {@code null}
     * @throws IllegalArgumentException if {@code minGapSize} is not greater than zero, if
     *                                  {@code assemblyType} is supplied for an analysis type other
     *                                  than {@link AnalysisType#SEQUENCE_ASSEMBLY}, or if it does
     *                                  not match a known assembly type
     */
    public AnalysisContext(AnalysisType analysisType, int minGapSize, String assemblyType) {
        this.analysisType = Objects.requireNonNull(analysisType, "analysisType must not be null");
        if (minGapSize <= 0) {
            throw new IllegalArgumentException("minGapSize must be greater than 0, but was " + minGapSize);
        }
        this.minGapSize = minGapSize;
        this.assemblyType = AssemblyTypeValidator.parseAndValidate(analysisType, assemblyType);
    }
}
