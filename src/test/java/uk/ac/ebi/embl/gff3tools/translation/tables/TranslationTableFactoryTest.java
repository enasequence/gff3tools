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
package uk.ac.ebi.embl.gff3tools.translation.tables;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TranslationTableFactoryTest {

    @Test
    public void testSingletonInstance() {
        TranslationTableFactory instance1 = TranslationTableFactory.getInstance();
        TranslationTableFactory instance2 = TranslationTableFactory.getInstance();
        assertSame(instance1, instance2, "getInstance should return the same instance");
    }

    @Test
    public void testAllTranslationTablesExist() {
        TranslationTableFactory factory = TranslationTableFactory.getInstance();
        for (TranslationTableDescriptor descriptor : TranslationTableDescriptor.values()) {
            TranslationTable table = factory.getTranslationTable(descriptor.getNumber());
            assertNotNull(table, "Translation table " + descriptor.getNumber() + " should exist");
            assertEquals(descriptor.getNumber(), table.getNumber().intValue());
        }
    }

    @Test
    public void testNullTableNumber() {
        assertNull(TranslationTableFactory.getInstance().getTranslationTable(null));
    }

    @Test
    public void testInvalidTableNumber() {
        assertNull(TranslationTableFactory.getInstance().getTranslationTable(999));
    }

    @Test
    public void testStandardTableCodonMaps() {
        TranslationTable table = TranslationTableFactory.getInstance().getTranslationTable(1);
        assertNotNull(table);
        assertEquals("The Standard Code", table.getName());

        // Test some standard codons
        assertEquals(Character.valueOf('M'), table.getStartCodonMap().get("ATG"));
        assertEquals(Character.valueOf('*'), table.getOtherCodonMap().get("TAA"));
        assertEquals(Character.valueOf('*'), table.getOtherCodonMap().get("TAG"));
        assertEquals(Character.valueOf('*'), table.getOtherCodonMap().get("TGA"));
        assertEquals(Character.valueOf('K'), table.getOtherCodonMap().get("AAA"));
        assertEquals(Character.valueOf('F'), table.getOtherCodonMap().get("TTT"));
    }

    @Test
    public void testBacterialTable() {
        TranslationTable table = TranslationTableFactory.getInstance().getTranslationTable(11);
        assertNotNull(table);
        assertEquals("The Bacterial and Plant Plastid Code", table.getName());
        assertEquals(Integer.valueOf(11), table.getNumber());
    }

    @Test
    public void testMitochondrialTable() {
        TranslationTable table = TranslationTableFactory.getInstance().getTranslationTable(2);
        assertNotNull(table);
        assertEquals("The Vertebrate Mitochondrial Code", table.getName());

        // TGA codes for W in vertebrate mitochondria, not stop
        assertEquals(Character.valueOf('W'), table.getOtherCodonMap().get("TGA"));
    }

    @Test
    public void testSameTableInstanceReturned() {
        // Verify that the same TranslationTable instance is returned for the same number
        TranslationTable table1 = TranslationTableFactory.getInstance().getTranslationTable(1);
        TranslationTable table2 = TranslationTableFactory.getInstance().getTranslationTable(1);
        assertSame(table1, table2, "Same table instance should be returned");
    }
}
