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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;

class GapOptionsValidatorTest {

    // --- Nothing supplied ---

    @Test
    void bothNull_noException() {
        assertDoesNotThrow(() -> GapOptionsValidator.validate(null, null));
    }

    @Test
    void bothBlank_noException() {
        assertDoesNotThrow(() -> GapOptionsValidator.validate("", ""));
        assertDoesNotThrow(() -> GapOptionsValidator.validate("   ", "   "));
    }

    // --- linkage-evidence without gap-type ---

    @Test
    void linkageEvidenceWithoutGapType_null_throws() {
        assertThrows(CLIException.class, () -> GapOptionsValidator.validate(null, "unspecified"));
    }

    @Test
    void linkageEvidenceWithoutGapType_blank_throws() {
        assertThrows(CLIException.class, () -> GapOptionsValidator.validate("", "unspecified"));
    }

    // --- unknown gap-type (new behaviour) ---

    @Test
    void unknownGapType_throws() {
        assertThrows(CLIException.class, () -> GapOptionsValidator.validate("not a gap type", null));
    }

    // --- valid type requiring linkage, with linkage ---

    @Test
    void typeRequiringLinkage_withLinkage_noException() {
        assertDoesNotThrow(() -> GapOptionsValidator.validate("within scaffold", "paired-ends"));
        assertDoesNotThrow(() -> GapOptionsValidator.validate("repeat within scaffold", "paired-ends"));
        assertDoesNotThrow(() -> GapOptionsValidator.validate("contamination", "paired-ends"));
    }

    // --- valid type requiring linkage, without linkage ---

    @Test
    void typeRequiringLinkage_withoutLinkage_throws() {
        assertThrows(CLIException.class, () -> GapOptionsValidator.validate("within scaffold", null));
        assertThrows(CLIException.class, () -> GapOptionsValidator.validate("repeat within scaffold", null));
        assertThrows(CLIException.class, () -> GapOptionsValidator.validate("contamination", null));
    }

    // --- valid type not allowing linkage, without linkage ---

    @Test
    void typeNotAllowingLinkage_withoutLinkage_noException() {
        assertDoesNotThrow(() -> GapOptionsValidator.validate("between scaffolds", null));
        assertDoesNotThrow(() -> GapOptionsValidator.validate("unknown", null));
        assertDoesNotThrow(() -> GapOptionsValidator.validate("telomere", null));
    }

    // --- valid type not allowing linkage, with linkage ---

    @Test
    void typeNotAllowingLinkage_withLinkage_throws() {
        assertThrows(CLIException.class, () -> GapOptionsValidator.validate("between scaffolds", "paired-ends"));
    }

    // --- case-insensitivity / trimming ---

    @Test
    void gapType_isTrimmedAndLowercased() {
        assertDoesNotThrow(() -> GapOptionsValidator.validate("  Within Scaffold  ", "paired-ends"));
    }
}
