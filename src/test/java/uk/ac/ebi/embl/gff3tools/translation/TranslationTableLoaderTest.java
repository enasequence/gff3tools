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
package uk.ac.ebi.embl.gff3tools.translation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TranslationTableLoaderTest {

    @Test
    void singletonReturnsSameInstance() {
        TranslationTableLoader loader1 = TranslationTableLoader.getInstance();
        TranslationTableLoader loader2 = TranslationTableLoader.getInstance();

        assertSame(loader1, loader2, "Loader should be a singleton");
    }

    @Test
    void loadsAllTranslationTables() {
        TranslationTableLoader loader = TranslationTableLoader.getInstance();

        Map<Integer, TranslationTable> tables = loader.all();

        assertNotNull(tables);
        assertFalse(tables.isEmpty(), "Translation tables should not be empty");
    }

    @Test
    void getKnownTranslationTable() {
        TranslationTableLoader loader = TranslationTableLoader.getInstance();

        TranslationTable table = loader.get(1);

        assertNotNull(table);
        assertEquals(1, table.getId());
        assertEquals("The Standard Code", table.getName());
    }

    @Test
    void getUnknownTranslationTableThrowsException() {
        TranslationTableLoader loader = TranslationTableLoader.getInstance();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> loader.get(999));

        assertTrue(
                ex.getMessage().contains("Unknown translation table"),
                "Expected clear error message for unknown table");
    }

    @Test
    void translationTableContains64Codons() {
        TranslationTable table = TranslationTableLoader.getInstance().get(1);

        assertEquals(64, table.getOtherCodonMap().size());
        assertEquals(64, table.getStartCodonMap().size());
    }

    @Test
    void standardCodeHasCorrectStopCodons() {

        TranslationTable table = TranslationTableLoader.getInstance().get(1);

        Map<String, Character> codons = table.getOtherCodonMap();

        assertEquals('*', codons.get("taa"));
        assertEquals('*', codons.get("tag"));
        assertEquals('*', codons.get("tga"));
    }

    @Test
    void startCodonsTranslateToMethionine() {
        TranslationTable table = TranslationTableLoader.getInstance().get(1);

        Map<String, Character> startCodons = table.getStartCodonMap();

        // ATG (aug) should be M
        assertEquals('M', startCodons.get("atg"));

        // Non-start codon should not magically become M
        assertNotEquals('M', startCodons.get("ttt"));
    }

    @Test
    void codonMapsAreImmutable() {
        TranslationTable table = TranslationTableLoader.getInstance().get(1);

        assertThrows(UnsupportedOperationException.class, () -> table.getOtherCodonMap()
                .put("aaa", 'X'));

        assertThrows(UnsupportedOperationException.class, () -> table.getStartCodonMap()
                .put("aaa", 'X'));
    }
}
