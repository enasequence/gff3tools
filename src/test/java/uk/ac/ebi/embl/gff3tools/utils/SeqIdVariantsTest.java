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

import java.util.List;
import org.junit.jupiter.api.Test;

class SeqIdVariantsTest {

    @Test
    void nullSeqIdYieldsNoVariants() {
        assertTrue(SeqIdVariants.variants(null).isEmpty());
    }

    @Test
    void unversionedSeqIdTriesVersionsOneToNine() {
        List<String> variants = SeqIdVariants.variants("OZ375371");
        assertEquals("OZ375371", variants.get(0));
        assertEquals(10, variants.size());
        for (int version = 1; version <= 9; version++) {
            assertEquals("OZ375371." + version, variants.get(version));
        }
    }

    @Test
    void versionedSeqIdStripsTheVersionAndAppendsNothing() {
        // Versions are only appended to ids that carry none.
        assertEquals(List.of("OZ375371.1", "OZ375371"), SeqIdVariants.variants("OZ375371.1"));
        assertEquals(List.of("X.9", "X"), SeqIdVariants.variants("X.9"));
    }

    @Test
    void trailingNonDigitIsNotAVersion() {
        // "abc" after the dot is not a version, so nothing is stripped and nothing is appended.
        assertEquals(List.of("X.abc"), SeqIdVariants.variants("X.abc"));
    }

    @Test
    void onlyTheFirstDotIsTreatedAsAVersionSeparator() {
        // The reader parses the version after the first dot, so "X.1.2" carries no version.
        assertEquals(List.of("X.1.2"), SeqIdVariants.variants("X.1.2"));
    }

    @Test
    void leadingDotHasNothingToStrip() {
        // A dot at index 0 is not a separator, so nothing can be stripped; the id carries a dot,
        // so no versions are appended either.
        assertEquals(List.of(".1"), SeqIdVariants.variants(".1"));
    }
}
