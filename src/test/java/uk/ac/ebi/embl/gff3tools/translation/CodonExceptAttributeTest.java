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
import uk.ac.ebi.embl.gff3tools.exception.TranslationException;
import uk.ac.ebi.embl.gff3tools.translation.except.CodonExceptAttribute;

public class CodonExceptAttributeTest {

    // ========== Basic Format Tests ==========

    @Test
    public void testBasicFormatQuoted() throws TranslationException {
        CodonExceptAttribute attr = new CodonExceptAttribute("(seq:\"tga\",aa:Trp)");
        assertEquals("TGA", attr.getCodon());
        assertEquals(Character.valueOf('W'), attr.getAminoAcid());
    }

    @Test
    public void testBasicFormatUnquoted() throws TranslationException {
        CodonExceptAttribute attr = new CodonExceptAttribute("(seq:tga,aa:Trp)");
        assertEquals("TGA", attr.getCodon());
        assertEquals(Character.valueOf('W'), attr.getAminoAcid());
    }

    @Test
    public void testUppercaseCodonNormalized() throws TranslationException {
        CodonExceptAttribute attr = new CodonExceptAttribute("(seq:TGA,aa:Trp)");
        assertEquals("TGA", attr.getCodon()); // Should be normalized to uppercase
    }

    // ========== Flexible Whitespace Tests ==========

    @Test
    public void testFlexibleWhitespace() throws TranslationException {
        CodonExceptAttribute attr = new CodonExceptAttribute("( seq : \"tga\" , aa : Trp )");
        assertEquals("TGA", attr.getCodon());
        assertEquals(Character.valueOf('W'), attr.getAminoAcid());
    }

    @Test
    public void testLeadingTrailingWhitespace() throws TranslationException {
        CodonExceptAttribute attr = new CodonExceptAttribute("  (seq:\"aga\",aa:Arg)  ");
        assertEquals("AGA", attr.getCodon());
        assertEquals(Character.valueOf('R'), attr.getAminoAcid());
    }

    // ========== Case Insensitivity Tests ==========

    @Test
    public void testLowercaseAminoAcid() throws TranslationException {
        CodonExceptAttribute attr = new CodonExceptAttribute("(seq:\"tga\",aa:trp)");
        assertEquals(Character.valueOf('W'), attr.getAminoAcid());
    }

    @Test
    public void testMixedCaseAminoAcid() throws TranslationException {
        CodonExceptAttribute attr = new CodonExceptAttribute("(seq:\"tga\",aa:TrP)");
        assertEquals(Character.valueOf('W'), attr.getAminoAcid());
    }

    // ========== Various Amino Acids Tests ==========

    @Test
    public void testAminoAcidSec() throws TranslationException {
        // Selenocysteine
        assertEquals(Character.valueOf('U'), new CodonExceptAttribute("(seq:\"tga\",aa:Sec)").getAminoAcid());
    }

    @Test
    public void testAminoAcidPyl() throws TranslationException {
        // Pyrrolysine
        assertEquals(Character.valueOf('O'), new CodonExceptAttribute("(seq:\"tag\",aa:Pyl)").getAminoAcid());
    }

    @Test
    public void testAminoAcidTerm() throws TranslationException {
        // Stop codon
        assertEquals(Character.valueOf('*'), new CodonExceptAttribute("(seq:\"aaa\",aa:TERM)").getAminoAcid());
    }

    @Test
    public void testAminoAcidMet() throws TranslationException {
        assertEquals(Character.valueOf('M'), new CodonExceptAttribute("(seq:\"ctg\",aa:Met)").getAminoAcid());
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testNullValue() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute(null));
    }

    @Test
    public void testEmptyValue() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute(""));
    }

    @Test
    public void testBlankValue() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute("   "));
    }

    @Test
    public void testInvalidFormat() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute("invalid_format"));
    }

    @Test
    public void testMissingParentheses() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute("seq:tga,aa:Trp"));
    }

    @Test
    public void testUnknownAminoAcid() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute("(seq:\"tga\",aa:Unknown)"));
    }

    @Test
    public void testCodonTooShort() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute("(seq:\"tg\",aa:Trp)"));
    }

    @Test
    public void testCodonTooLong() {
        assertThrows(TranslationException.class, () -> new CodonExceptAttribute("(seq:\"tgaa\",aa:Trp)"));
    }
}
