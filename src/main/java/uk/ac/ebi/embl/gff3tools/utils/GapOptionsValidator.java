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

import java.util.Optional;
import java.util.Set;
import uk.ac.ebi.embl.gff3tools.validation.builtin.AssemblyGapValidation;

/**
 * INSDC validity rules for the gap-generation options ({@code gap_type} / {@code linkage_evidence}).
 * {@code GapGenerationFix} adds its features after the FEATURE-level rules have run, so
 * {@code AssemblyGapValidation} never sees them and these two attributes — the only ones a generated
 * gap could fail on — are checked here instead, by the CLI and by {@code AnalysisContext}'s
 * constructor. The vocabulary itself stays in {@link AssemblyGapValidation#validGapTypes()}.
 */
public final class GapOptionsValidator {

    /** The gap types for which linkage_evidence is both required and allowed. */
    private static final Set<String> GAP_TYPES_REQUIRING_LINKAGE =
            Set.of("within scaffold", "repeat within scaffold", "contamination");

    private GapOptionsValidator() {}

    /**
     * @param gapType         the requested gap_type, or null/blank when not supplied
     * @param linkageEvidence the requested linkage_evidence, or null/blank when not supplied
     * @return the first rule violation as a message, or empty when the combination is valid
     */
    public static Optional<String> validate(String gapType, String linkageEvidence) {
        boolean hasGapType = gapType != null && !gapType.isBlank();
        boolean hasLinkageEvidence = linkageEvidence != null && !linkageEvidence.isBlank();

        if (hasLinkageEvidence && !hasGapType) {
            return Optional.of("linkage_evidence requires a gap_type to be supplied");
        }
        if (!hasGapType) {
            return Optional.empty();
        }

        String normalised = gapType.trim().toLowerCase();
        if (!AssemblyGapValidation.validGapTypes().contains(normalised)) {
            return Optional.of("gap_type \"" + gapType.trim() + "\" is not a valid INSDC gap_type");
        }

        boolean requiresLinkage = GAP_TYPES_REQUIRING_LINKAGE.contains(normalised);
        if (requiresLinkage && !hasLinkageEvidence) {
            return Optional.of("gap_type \"" + gapType.trim() + "\" requires a linkage_evidence to be supplied");
        }
        if (!requiresLinkage && hasLinkageEvidence) {
            return Optional.of("linkage_evidence is only valid with gap_type "
                    + "\"within scaffold\", \"repeat within scaffold\" or \"contamination\"");
        }
        return Optional.empty();
    }
}
