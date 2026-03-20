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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TranslationStateTest {

    @Test
    void buildKeyUsesFeatureIdWhenPresent() {
        String key = TranslationState.buildKey("seq1", "cds-1", 10);
        assertEquals("seq1:cds-1", key);
    }

    @Test
    void buildKeyUsesLineFallbackWhenFeatureIdNull() {
        String key = TranslationState.buildKey("seq1", null, 42);
        assertEquals("seq1:line_42", key);
    }

    @Test
    void recordAndGetRoundTrip() {
        TranslationState state = new TranslationState();
        state.record("seq1:cds-1", "OLD", "NEW");

        TranslationState.TranslationEntry entry = state.get("seq1:cds-1");
        assertNotNull(entry);
        assertEquals("OLD", entry.oldTranslation());
        assertEquals("NEW", entry.newTranslation());
    }

    @Test
    void getReturnsNullForUnknownKey() {
        TranslationState state = new TranslationState();
        assertNull(state.get("nonexistent"));
    }

    @Test
    void recordWithNullOldTranslation() {
        TranslationState state = new TranslationState();
        state.record("seq1:cds-1", null, "NEW");

        TranslationState.TranslationEntry entry = state.get("seq1:cds-1");
        assertNotNull(entry);
        assertNull(entry.oldTranslation());
        assertEquals("NEW", entry.newTranslation());
    }
}
