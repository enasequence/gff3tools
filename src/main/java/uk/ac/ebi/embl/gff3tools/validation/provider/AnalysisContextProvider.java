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
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Provider that supplies the user-supplied {@link AnalysisContext}.
 *
 * <p>The {@link AnalysisType} and minimum gap size are external input, so this provider
 * must be registered explicitly (e.g. via
 * {@code ValidationEngineBuilder.withProvider(...)}) rather than auto-discovered.
 */
public class AnalysisContextProvider implements ContextProvider<AnalysisContext> {

    public static final AnalysisType DEFAULT_ANALYSIS_TYPE = AnalysisType.UNKNOWN;
    public static final int DEFAULT_MIN_GAP_SIZE = 10;

    private final AnalysisContext analysisContext;

    /** Creates a provider with the default analysis type and minimum gap size. */
    public AnalysisContextProvider() {
        this(DEFAULT_ANALYSIS_TYPE, DEFAULT_MIN_GAP_SIZE);
    }

    /**
     * @param analysisType the analysis type (must not be {@code null})
     * @param minGapSize   the minimum gap size (must be greater than zero)
     */
    public AnalysisContextProvider(AnalysisType analysisType, int minGapSize) {
        this(new AnalysisContext(analysisType, minGapSize));
    }

    public AnalysisContextProvider(AnalysisContext analysisContext) {
        Objects.requireNonNull(analysisContext, "analysisContext must not be null");
        this.analysisContext = analysisContext;
    }

    @Override
    public AnalysisContext get(ValidationContext context) {
        return analysisContext;
    }

    @Override
    public Class<AnalysisContext> type() {
        return AnalysisContext.class;
    }
}
