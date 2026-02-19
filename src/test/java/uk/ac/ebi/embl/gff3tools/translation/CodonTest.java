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

import org.junit.jupiter.api.Test;

public class CodonTest {

    @Test
    public void testConstructor() {
        Codon codon = new Codon("atg", 1, 'M', false);
        assertEquals("atg", codon.getCodon());
        assertEquals(1, codon.getPosition());
        assertEquals('M', codon.getAminoAcid());
        assertFalse(codon.isTranslationException());
    }

    @Test
    public void testConstructorWithTranslationException() {
        Codon codon = new Codon("tga", 4, 'U', true);
        assertEquals("tga", codon.getCodon());
        assertEquals(4, codon.getPosition());
        assertEquals('U', codon.getAminoAcid());
        assertTrue(codon.isTranslationException());
    }

    @Test
    public void testStopCodon() {
        Codon codon = new Codon("taa", 100, '*', false);
        assertEquals("taa", codon.getCodon());
        assertEquals(100, codon.getPosition());
        assertEquals('*', codon.getAminoAcid());
    }
}
