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
    void versionedSeqIdStripsTheVersionFirst() {
        List<String> variants = SeqIdVariants.variants("OZ375371.1");
        assertEquals(List.of("OZ375371.1", "OZ375371"), variants.subList(0, 2));
        // Appended forms are built from the seqId as given.
        assertEquals("OZ375371.1.1", variants.get(2));
        assertEquals(11, variants.size());
    }

    @Test
    void trailingNonDigitIsNotAVersion() {
        // "abc" after the dot is not a version, so nothing is stripped.
        List<String> variants = SeqIdVariants.variants("X.abc");
        assertEquals("X.abc", variants.get(0));
        assertEquals("X.abc.1", variants.get(1));
        assertEquals(10, variants.size());
    }

    @Test
    void onlyTheLastDotIsTreatedAsAVersionSeparator() {
        // "1.2" ends in a digit run after the last dot, so one version is stripped.
        List<String> variants = SeqIdVariants.variants("X.1.2");
        assertEquals(List.of("X.1.2", "X.1"), variants.subList(0, 2));
    }

    @Test
    void leadingDotHasNothingToStrip() {
        List<String> variants = SeqIdVariants.variants(".1");
        // dot at index 0 is not a separator, so ".1" stays whole.
        assertEquals(".1", variants.get(0));
        assertEquals(10, variants.size());
    }
}
