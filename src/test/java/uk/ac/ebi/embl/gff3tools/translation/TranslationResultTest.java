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

import java.util.ArrayList;
import java.util.List;

public class TranslationResultTest {

    @Test
    public void testGetTranslation() {
        TranslationResult result = new TranslationResult();
        List<Codon> codons = new ArrayList<>();

        Codon codon1 = new Codon("atg", 1, 'M', false);
        codons.add(codon1);

        Codon codon2 = new Codon("aaa", 4, 'K', false);
        codons.add(codon2);

        Codon codon3 = new Codon("tag", 7, '*', false);
        codons.add(codon3);

        result.setCodons(codons);
        result.setConceptualTranslationCodons(2);
        result.setTrailingBases("");

        assertEquals("MK*", result.getTranslation());
        assertEquals("MK", result.getConceptualTranslation());
        assertEquals("atgaaatag", result.getSequence());
    }

    @Test
    public void testEmptyResult() {
        TranslationResult result = new TranslationResult();
        assertEquals("", result.getTranslation());
        assertEquals("", result.getConceptualTranslation());
        assertEquals("", result.getSequence());
    }

    @Test
    public void testTrailingBases() {
        TranslationResult result = new TranslationResult();
        List<Codon> codons = new ArrayList<>();

        Codon codon = new Codon("atg", 1, 'M', false);
        codons.add(codon);

        result.setCodons(codons);
        result.setConceptualTranslationCodons(1);
        result.setTrailingBases("aa");

        assertEquals("atgaa", result.getSequence());
    }

    @Test
    public void testErrors() {
        TranslationResult result = new TranslationResult();
        assertFalse(result.hasErrors());
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());

        result.addError("test-error");
        assertTrue(result.hasErrors());
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertEquals("test-error", result.getErrors().get(0));
    }

    @Test
    public void testMultipleErrors() {
        TranslationResult result = new TranslationResult();
        result.addError("error-1");
        result.addError("error-2");

        assertEquals(2, result.getErrors().size());
        assertTrue(result.getErrors().contains("error-1"));
        assertTrue(result.getErrors().contains("error-2"));
    }

    @Test
    public void testAddNullOrBlankError() {
        TranslationResult result = new TranslationResult();
        result.addError(null);
        result.addError("");
        result.addError("   ");

        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.isValid());
    }

    @Test
    public void testFixedFlags() {
        TranslationResult result = new TranslationResult();

        assertFalse(result.isFixedFivePrimePartial());
        assertFalse(result.isFixedThreePrimePartial());
        assertFalse(result.isFixedPseudo());
        assertFalse(result.isFixedDegenerateStartCodon());

        result.setFixedFivePrimePartial(true);
        result.setFixedThreePrimePartial(true);
        result.setFixedPseudo(true);
        result.setFixedDegenerateStartCodon(true);

        assertTrue(result.isFixedFivePrimePartial());
        assertTrue(result.isFixedThreePrimePartial());
        assertTrue(result.isFixedPseudo());
        assertTrue(result.isFixedDegenerateStartCodon());
    }

    @Test
    public void testTranslationLengthAndBaseCount() {
        TranslationResult result = new TranslationResult();
        result.setTranslationLength(100);
        result.setTranslationBaseCount(300);

        assertEquals(100, result.getTranslationLength());
        assertEquals(300, result.getBaseCount());
    }
}
