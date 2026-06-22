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
import lombok.Getter;

/**
 * User-supplied analysis settings made available to fixes and validators via the
 * validation context.
 *
 * <p>Both the {@link AnalysisType} type and the minimum gap size are external input. The
 * minimum gap size must always be greater than zero.
 */
public final class AnalysisContext {

    @Getter
    private final AnalysisType analysisType;

    @Getter
    private final int minGapSize;

    /**
     * @param analysisType the analysis type (must not be {@code null})
     * @param minGapSize   the minimum gap size (must be greater than zero)
     * @throws NullPointerException     if {@code analysisType} is {@code null}
     * @throws IllegalArgumentException if {@code minGapSize} is not greater than zero
     */
    public AnalysisContext(AnalysisType analysisType, int minGapSize) {
        this.analysisType = Objects.requireNonNull(analysisType, "analysisType must not be null");
        if (minGapSize <= 0) {
            throw new IllegalArgumentException("minGapSize must be greater than 0, but was " + minGapSize);
        }
        this.minGapSize = minGapSize;
    }
}
