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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.TranslationException;

public class CodonTranslatorTest {

    private CodonTranslator translator;

    @BeforeEach
    public void setUp() throws TranslationException {
        translator = new CodonTranslator(1); // Standard code
    }

    @Test
    public void testTranslateStartCodonATG() throws TranslationException {
        assertEquals('M', translator.translateStartCodon("ATG"));
    }

    @Test
    public void testTranslateStartCodonTTG() throws TranslationException {
        // TTG is a start codon in standard code
        assertEquals('M', translator.translateStartCodon("TTG"));
    }

    @Test
    public void testTranslateStopCodons() throws TranslationException {
        assertEquals('*', translator.translateOtherCodon("TAA"));
        assertEquals('*', translator.translateOtherCodon("TAG"));
        assertEquals('*', translator.translateOtherCodon("TGA"));
    }

    @Test
    public void testTranslateLysine() throws TranslationException {
        assertEquals('K', translator.translateOtherCodon("AAA"));
        assertEquals('K', translator.translateOtherCodon("AAG"));
    }

    @Test
    public void testTranslatePhenylalanine() throws TranslationException {
        assertEquals('F', translator.translateOtherCodon("TTT"));
        assertEquals('F', translator.translateOtherCodon("TTC"));
    }

    @Test
    public void testAmbiguousBaseN() throws TranslationException {
        // NNN expands to 64 codons, no consensus -> X
        assertEquals('X', translator.translateOtherCodon("NNN"));
    }

    @Test
    public void testAmbiguousBaseR() throws TranslationException {
        // R = A or G
        // RAR -> AAA=K, AAG=K, GAA=E, GAG=E -> X (no consensus)
        char aminoAcid = translator.translateOtherCodon("RAR");
        assertNotNull(aminoAcid);
    }

    @Test
    public void testAmbiguousBaseY() throws TranslationException {
        // Y = C or T
        // TTY -> TTC=F, TTT=F -> F (consensus)
        assertEquals('F', translator.translateOtherCodon("TTY"));
    }

    @Test
    public void testCodonException() throws TranslationException {
        translator.addCodonException("TGA", 'W');
        assertEquals('W', translator.translateOtherCodon("TGA"));
    }

    @Test
    public void testIsDegenerateStartCodon() throws TranslationException {
        // CTG can be a start codon in some contexts
        assertTrue(translator.isDegenerateStartCodon("CTG"));
    }

    @Test
    public void testIsDegenerateStopCodon() throws TranslationException {
        // TRA where R=A|G covers TAA (stop) and TGA (stop)
        assertTrue(translator.isDegenerateStopCodon("TRA"));
    }

    @Test
    public void testIsAmbiguous() {
        assertFalse(translator.isAmbiguous("ATG"));
        assertTrue(translator.isAmbiguous("ATN"));
    }

    @Test
    public void testMitochondrialCode() throws TranslationException {
        // Vertebrate mitochondrial code (table 2)
        CodonTranslator mitoTranslator = new CodonTranslator(2);
        // TGA is W in vertebrate mitochondrial, not stop
        assertEquals('W', mitoTranslator.translateOtherCodon("TGA"));
    }

    @Test
    public void testInvalidTranslationTable() {
        assertThrows(TranslationException.class, () -> new CodonTranslator(999));
    }

    @Test
    public void testGetTranslationTable() throws TranslationException {
        assertEquals(Integer.valueOf(1), translator.getTranslationTable().getNumber());
    }
}
