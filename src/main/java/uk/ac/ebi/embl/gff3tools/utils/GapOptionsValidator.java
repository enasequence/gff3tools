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

import java.util.Set;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.validation.builtin.AssemblyGapValidation;

/**
 * Fail-fast, CLI-level validation of gap-generation options (--gap-type / --linkage-evidence),
 * shared by the {@code conversion} (FASTA->GFF3) and {@code fix} commands. Value validity is
 * defined by {@link AssemblyGapValidation#validGapTypes()} (single source of truth).
 */
public final class GapOptionsValidator {

    // INSDC gap types for which linkage_evidence is both required and allowed. Mirrors
    // AssemblyGapValidation; kept here only to fail fast with a clear usage message.
    private static final Set<String> GAP_TYPES_REQUIRING_LINKAGE =
            Set.of("within scaffold", "repeat within scaffold", "contamination");

    private GapOptionsValidator() {}

    public static void validate(String gapType, String linkageEvidence) throws CLIException {
        boolean hasGapType = gapType != null && !gapType.isBlank();
        boolean hasLinkageEvidence = linkageEvidence != null && !linkageEvidence.isBlank();

        // 1. linkage-evidence without gap-type
        if (hasLinkageEvidence && !hasGapType) {
            throw new CLIException("--linkage-evidence requires --gap-type to be set");
        }

        if (hasGapType) {
            String normalizedGapType = gapType.trim().toLowerCase();

            // 2. gap-type must be in the full INSDC vocabulary (single source of truth)
            if (!AssemblyGapValidation.validGapTypes().contains(normalizedGapType)) {
                throw new CLIException("--gap-type \"" + gapType.trim() + "\" is not a valid INSDC gap_type");
            }

            // 3. linkage-evidence relationship
            boolean requiresLinkage = GAP_TYPES_REQUIRING_LINKAGE.contains(normalizedGapType);
            if (requiresLinkage && !hasLinkageEvidence) {
                throw new CLIException("--gap-type \"" + gapType.trim() + "\" requires --linkage-evidence to be set");
            }
            if (!requiresLinkage && hasLinkageEvidence) {
                throw new CLIException("--linkage-evidence is only valid with --gap-type "
                        + "\"within scaffold\", \"repeat within scaffold\" or \"contamination\"");
            }
        }
    }
}
