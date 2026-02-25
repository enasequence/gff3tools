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
import uk.ac.ebi.embl.gff3tools.translation.except.TranslExceptAttribute;

public class TranslExceptAttributeTest {

    // ========== Basic Format Tests ==========

    @Test
    public void testBasicFormat() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:213..215,aa:Trp)");
        assertEquals(Long.valueOf(213), attr.getStartPosition());
        assertEquals(Long.valueOf(215), attr.getEndPosition());
        assertEquals(Character.valueOf('W'), attr.getAminoAcidCode());
    }

    @Test
    public void testSinglePosition() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:1,aa:Met)");
        assertEquals(Long.valueOf(1), attr.getStartPosition());
        assertEquals(Long.valueOf(1), attr.getEndPosition()); // end equals start for single position
        assertEquals(Character.valueOf('M'), attr.getAminoAcidCode());
    }

    // ========== Flexible Whitespace Tests ==========

    @Test
    public void testFlexibleWhitespace() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("( pos : 213..215 , aa : Trp )");
        assertEquals(Long.valueOf(213), attr.getStartPosition());
        assertEquals(Long.valueOf(215), attr.getEndPosition());
        assertEquals(Character.valueOf('W'), attr.getAminoAcidCode());
    }

    @Test
    public void testLeadingTrailingWhitespace() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("  (pos:1..3,aa:Met)  ");
        assertEquals(Long.valueOf(1), attr.getStartPosition());
        assertEquals(Long.valueOf(3), attr.getEndPosition());
        assertEquals(Character.valueOf('M'), attr.getAminoAcidCode());
    }

    @Test
    public void testWhitespaceInPositionRange() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:213 .. 215,aa:Trp)");
        assertEquals(Long.valueOf(213), attr.getStartPosition());
        assertEquals(Long.valueOf(215), attr.getEndPosition());
        assertEquals(Character.valueOf('W'), attr.getAminoAcidCode());
    }

    // ========== Case Insensitivity Tests ==========

    @Test
    public void testLowercaseAminoAcid() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:1..3,aa:trp)");
        assertEquals(Character.valueOf('W'), attr.getAminoAcidCode());
    }

    @Test
    public void testMixedCaseAminoAcid() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:1..3,aa:TrP)");
        assertEquals(Character.valueOf('W'), attr.getAminoAcidCode());
    }

    @Test
    public void testUppercaseKeywords() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("(POS:1..3,AA:Met)");
        assertEquals(Long.valueOf(1), attr.getStartPosition());
        assertEquals(Character.valueOf('M'), attr.getAminoAcidCode());
    }

    // ========== All Amino Acid Types Tests ==========

    @Test
    public void testAminoAcidAla() throws TranslationException {
        assertEquals(Character.valueOf('A'), new TranslExceptAttribute("(pos:1..3,aa:Ala)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidArg() throws TranslationException {
        assertEquals(Character.valueOf('R'), new TranslExceptAttribute("(pos:1..3,aa:Arg)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidAsn() throws TranslationException {
        assertEquals(Character.valueOf('N'), new TranslExceptAttribute("(pos:1..3,aa:Asn)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidAsp() throws TranslationException {
        assertEquals(Character.valueOf('D'), new TranslExceptAttribute("(pos:1..3,aa:Asp)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidCys() throws TranslationException {
        assertEquals(Character.valueOf('C'), new TranslExceptAttribute("(pos:1..3,aa:Cys)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidGln() throws TranslationException {
        assertEquals(Character.valueOf('Q'), new TranslExceptAttribute("(pos:1..3,aa:Gln)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidGlu() throws TranslationException {
        assertEquals(Character.valueOf('E'), new TranslExceptAttribute("(pos:1..3,aa:Glu)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidGly() throws TranslationException {
        assertEquals(Character.valueOf('G'), new TranslExceptAttribute("(pos:1..3,aa:Gly)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidHis() throws TranslationException {
        assertEquals(Character.valueOf('H'), new TranslExceptAttribute("(pos:1..3,aa:His)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidIle() throws TranslationException {
        assertEquals(Character.valueOf('I'), new TranslExceptAttribute("(pos:1..3,aa:Ile)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidLeu() throws TranslationException {
        assertEquals(Character.valueOf('L'), new TranslExceptAttribute("(pos:1..3,aa:Leu)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidLys() throws TranslationException {
        assertEquals(Character.valueOf('K'), new TranslExceptAttribute("(pos:1..3,aa:Lys)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidMet() throws TranslationException {
        assertEquals(Character.valueOf('M'), new TranslExceptAttribute("(pos:1..3,aa:Met)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidPhe() throws TranslationException {
        assertEquals(Character.valueOf('F'), new TranslExceptAttribute("(pos:1..3,aa:Phe)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidPro() throws TranslationException {
        assertEquals(Character.valueOf('P'), new TranslExceptAttribute("(pos:1..3,aa:Pro)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidSer() throws TranslationException {
        assertEquals(Character.valueOf('S'), new TranslExceptAttribute("(pos:1..3,aa:Ser)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidThr() throws TranslationException {
        assertEquals(Character.valueOf('T'), new TranslExceptAttribute("(pos:1..3,aa:Thr)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidTrp() throws TranslationException {
        assertEquals(Character.valueOf('W'), new TranslExceptAttribute("(pos:1..3,aa:Trp)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidTyr() throws TranslationException {
        assertEquals(Character.valueOf('Y'), new TranslExceptAttribute("(pos:1..3,aa:Tyr)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidVal() throws TranslationException {
        assertEquals(Character.valueOf('V'), new TranslExceptAttribute("(pos:1..3,aa:Val)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidSec() throws TranslationException {
        // Selenocysteine
        assertEquals(Character.valueOf('U'), new TranslExceptAttribute("(pos:1..3,aa:Sec)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidPyl() throws TranslationException {
        // Pyrrolysine
        assertEquals(Character.valueOf('O'), new TranslExceptAttribute("(pos:1..3,aa:Pyl)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidTerm() throws TranslationException {
        // Stop codon (TERM)
        assertEquals(Character.valueOf('*'), new TranslExceptAttribute("(pos:1..3,aa:TERM)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidTer() throws TranslationException {
        // Stop codon (TER - alternative)
        assertEquals(Character.valueOf('*'), new TranslExceptAttribute("(pos:1..3,aa:Ter)").getAminoAcidCode());
    }

    @Test
    public void testAminoAcidOther() throws TranslationException {
        // Unknown amino acid
        assertEquals(Character.valueOf('X'), new TranslExceptAttribute("(pos:1..3,aa:OTHER)").getAminoAcidCode());
    }

    // ========== Error Handling Tests ==========

    @Test
    public void testNullValue() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute(null));
    }

    @Test
    public void testEmptyValue() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute(""));
    }

    @Test
    public void testBlankValue() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("   "));
    }

    @Test
    public void testInvalidFormat() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("invalid_format"));
    }

    @Test
    public void testMissingParentheses() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("pos:1..3,aa:Met"));
    }

    @Test
    public void testMissingPos() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("(1..3,aa:Met)"));
    }

    @Test
    public void testMissingAa() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("(pos:1..3,Met)"));
    }

    @Test
    public void testUnknownAminoAcid() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("(pos:1..3,aa:Unknown)"));
    }

    @Test
    public void testInvalidPositionFormat() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("(pos:abc..def,aa:Met)"));
    }

    @Test
    public void testInvalidPositionNegative() {
        assertThrows(TranslationException.class, () -> new TranslExceptAttribute("(pos:-1..3,aa:Met)"));
    }

    // ========== Edge Cases ==========

    @Test
    public void testLargePositionValues() throws TranslationException {
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:1000000..1000002,aa:Met)");
        assertEquals(Long.valueOf(1000000), attr.getStartPosition());
        assertEquals(Long.valueOf(1000002), attr.getEndPosition());
    }

    @Test
    public void testPositionRangeOneBase() throws TranslationException {
        // Range where start equals end
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:5..5,aa:Trp)");
        assertEquals(Long.valueOf(5), attr.getStartPosition());
        assertEquals(Long.valueOf(5), attr.getEndPosition());
    }
}
