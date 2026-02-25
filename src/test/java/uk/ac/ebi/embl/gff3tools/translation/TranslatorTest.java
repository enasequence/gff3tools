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

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.FastaReader;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.exception.TranslationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.translation.except.TranslExceptAttribute;

public class TranslatorTest {

    private GFF3Feature createDefaultFeature() {
        return new GFF3Feature(
                Optional.of("id"), Optional.empty(), "seq", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
    }

    private Translator createTranslator(int table) throws TranslationException {
        GFF3Feature feature = createDefaultFeature();
        feature.addAttribute("transl_table", String.valueOf(table));
        return new Translator(feature);
    }

    // ========== GFF3Feature Constructor Tests ==========

    @Test
    public void testConstructorWithPseudoFeatureSetsNonTranslating() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        feature.addAttribute("pseudo", "true");
        feature.addAttribute("transl_table", "11");

        Translator translator = new Translator(feature);
        assertTrue(translator.isNonTranslating());
    }

    @Test
    public void testConstructorWithPseudogeneFeatureSetsNonTranslating() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        feature.addAttribute("pseudogene", "processed");
        feature.addAttribute("transl_table", "11");

        Translator translator = new Translator(feature);
        assertTrue(translator.isNonTranslating());
    }

    @Test
    public void testConstructorWithNonPseudoFeatureNotNonTranslating() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        feature.addAttribute("product", "some protein");
        feature.addAttribute("transl_table", "11");

        Translator translator = new Translator(feature);
        assertFalse(translator.isNonTranslating());
    }

    // ========== Basic Translation Tests ==========

    @Test
    public void testBasicTranslation() throws TranslationException {
        Translator translator = createTranslator(11);
        TranslationResult result = translator.translate("ATGAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MK", result.getConceptualTranslation());
        assertEquals("MK*", result.getTranslation());
    }

    @Test
    public void testNullSequence() throws TranslationException {
        Translator translator = createTranslator(11);
        TranslationResult result = translator.translate(null);
        assertTrue(result.hasErrors());
    }

    @Test
    public void testNonTranslating() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setNonTranslating(true);
        TranslationResult result = translator.translate("ATGAAATAG".getBytes());
        assertEquals(0, result.getConceptualTranslationCodons());
    }

    // ========== Codon Start Tests ==========

    @Test
    public void testCodonStart2WithPartial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setCodonStart(2);
        translator.setFivePrimePartial(true);
        translator.setThreePrimePartial(true);
        // 10 bases: skip 1, translate 9 bases = 3 codons (no stop codon for 3' partial)
        TranslationResult result = translator.translate("AATGAAAGGG".getBytes());
        assertTrue(result.isValid(), "Result should be valid: " + result.getErrors());
    }

    @Test
    public void testCodonStart3WithPartial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setCodonStart(3);
        translator.setFivePrimePartial(true);
        translator.setThreePrimePartial(true);
        // 11 bases: skip 2, translate 9 bases = 3 codons (no stop codon for 3' partial)
        TranslationResult result = translator.translate("AAATGAAAGGG".getBytes());
        assertTrue(result.isValid(), "Result should be valid: " + result.getErrors());
    }

    @Test
    public void testInvalidCodonStart() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setCodonStart(4); // Invalid
        TranslationResult result = translator.translate("ATGAAATAG".getBytes());
        assertTrue(result.hasErrors());
    }

    // ========== Partial Sequence Tests ==========

    @Test
    public void testFivePrimePartial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFivePrimePartial(true);
        // Without start codon
        TranslationResult result = translator.translate("AAAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("KK", result.getConceptualTranslation());
    }

    @Test
    public void testThreePrimePartial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setThreePrimePartial(true);
        // Without stop codon
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MK", result.getConceptualTranslation());
    }

    @Test
    public void testThreePrimePartialWithTrailingBases() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setCodonStart(1);
        translator.setThreePrimePartial(true);
        TranslationResult result = translator.translate("ATGGCTGAAGCCGAAACCCATCCTCCTATCGGTGAATC".getBytes());
        assertTrue(result.isValid());
        assertEquals("MAEAETHPPIGES", result.getConceptualTranslation());
    }

    @Test
    public void testShortSequenceFivePrimePartial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setThreePrimePartial(true);
        TranslationResult result = translator.translate("AT".getBytes());
        assertTrue(result.isValid());
        assertEquals("M", result.getConceptualTranslation());
    }

    // ========== parseTranslExceptAttributes Tests ==========

    @Test
    public void testParseTranslExceptFromFeature() throws TranslationException {
        // Create feature with transl_except attribute
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");

        Translator translator = new Translator(feature);
        // ATG AAA AGT AAA TAG = M K S K *
        TranslationResult result = translator.translate("ATGAAAAGTAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MKSK", result.getConceptualTranslation());

        // TAG is stop codon(*) at position 7-9 is replaced with -> W
        feature.addAttribute("transl_except", "(pos:7..9,aa:Trp)");
        Translator translatorWithException = new Translator(feature);
        // ATG AAA AGT AAA TAG = M K W K *
        TranslationResult resultWithExp = translatorWithException.translate("ATGAAAAGTAAATAG".getBytes());
        assertTrue(resultWithExp.isValid());
        assertEquals("MKWK", resultWithExp.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptSelenocysteineFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");

        // TAG is stop codon(*) at position 4-6 is replaced with -> U
        feature.addAttribute("transl_except", "(pos:4..6,aa:Sec)");

        Translator translator = new Translator(feature);
        // ATG TGA AAA TAG = M U K *
        TranslationResult result = translator.translate("ATGTGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MUK", result.getConceptualTranslation());
    }

    @Test
    public void testParseMultipleTranslExceptFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // TAG stop codons(*) at position 4-6 -> U and position 7-9 -> W
        feature.addAttribute("transl_except", "(pos:4..6,aa:Sec)");
        feature.addAttribute("transl_except", "(pos:7..9,aa:Trp)");

        Translator translator = new Translator(feature);
        // ATG TGA TGA AAA TAG = M U W K *
        TranslationResult result = translator.translate("ATGTGATGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MUWK", result.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptTermFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Make AAA at position 4-6 translate to stop (TERM)
        feature.addAttribute("transl_except", "(pos:4..6,aa:TERM)");

        Translator translator = new Translator(feature);
        // ATG AAA = M * (AAA becomes stop)
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("M", result.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptPyrrolysineFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // TAG is stop codon at position 4-6, override to Pyrrolysine (O)
        feature.addAttribute("transl_except", "(pos:4..6,aa:Pyl)");

        Translator translator = new Translator(feature);
        // ATG TAG AAA TAG = M O K *
        TranslationResult result = translator.translate("ATGTAGAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MOK", result.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptSinglePositionFromFeature() throws TranslationException {
        // Single position format (pos:1,aa:Met) - used for partial codons
        // This tests that the parsing works; single position (1..1) covers partial codon
        TranslExceptAttribute attr = new TranslExceptAttribute("(pos:1,aa:Met)");
        assertEquals(Integer.valueOf(1), attr.getStartPosition());
        assertEquals(Integer.valueOf(1), attr.getEndPosition()); // end equals start for single position
        assertEquals(Character.valueOf('M'), attr.getAminoAcidLetter());
    }

    @Test
    public void testParseTranslExceptCaseInsensitiveFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Lowercase amino acid name
        feature.addAttribute("transl_except", "(pos:4..6,aa:trp)");

        Translator translator = new Translator(feature);
        // ATG TGA AAA TAG = M W K * (TGA at 4-6 -> W)
        TranslationResult result = translator.translate("ATGTGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MWK", result.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptTerFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Use TER (alternative to TERM) for stop codon
        feature.addAttribute("transl_except", "(pos:4..6,aa:Ter)");

        Translator translator = new Translator(feature);
        // ATG AAA = M * (AAA at 4-6 becomes stop)
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("M", result.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptFlexibleWhitespaceFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Flexible whitespace format
        feature.addAttribute("transl_except", "( pos : 4..6 , aa : Sec )");

        Translator translator = new Translator(feature);
        // ATG TGA AAA TAG = M U K *
        TranslationResult result = translator.translate("ATGTGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MUK", result.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptOtherAminoAcidFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // OTHER amino acid -> X
        feature.addAttribute("transl_except", "(pos:4..6,aa:OTHER)");

        Translator translator = new Translator(feature);
        translator.setThreePrimePartial(true);
        // ATG AAA = M X (AAA at 4-6 becomes X)
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MX", result.getConceptualTranslation());
    }

    @Test
    public void testParseTranslExceptUnknownAminoAcidThrowsException() {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Unknown amino acid name
        feature.addAttribute("transl_except", "(pos:4..6,aa:Unknown)");

        assertThrows(TranslationException.class, () -> new Translator(feature));
    }

    @Test
    public void testParseTranslExceptInvalidFormatThrowsException() {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Invalid transl_except format
        feature.addAttribute("transl_except", "invalid_format");

        assertThrows(TranslationException.class, () -> new Translator(feature));
    }

    // ========== handleCodonExceptAttributes Tests ==========

    @Test
    public void testParseCodonExceptFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");

        // Test without codon exception
        Translator translator = new Translator(feature);
        TranslationResult result = translator.translate("ATGAAAAAATGA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MKK", result.getConceptualTranslation());

        // Test with codon exception
        // TGA normally is stop, make it tryptophan globally
        feature.addAttribute("codon", "(seq:\"aaa\",aa:Trp)");
        Translator translatorWithExp = new Translator(feature);
        // ATG AAA AAA TGA = M W W (all ATGs become W)
        TranslationResult resultWithExp = translatorWithExp.translate("ATGAAAAAATGA".getBytes());
        assertTrue(resultWithExp.isValid());
        assertEquals("MWW", resultWithExp.getConceptualTranslation());
    }

    @Test
    public void testParseCodonExceptSelenocysteineFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // TGA -> Selenocysteine globally
        feature.addAttribute("codon", "(seq:\"tga\",aa:Sec)");

        Translator translator = new Translator(feature);
        translator.setThreePrimePartial(true);
        // ATG TGA AAA = M U K
        TranslationResult result = translator.translate("ATGTGAAAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MUK", result.getConceptualTranslation());
    }

    @Test
    public void testParseMultipleCodonExceptFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Multiple codon exceptions
        feature.addAttribute("codon", "(seq:\"tga\",aa:Trp)");
        feature.addAttribute("codon", "(seq:\"tag\",aa:Pyl)");

        Translator translator = new Translator(feature);
        translator.setThreePrimePartial(true);
        // ATG TGA TAG = M W O (TGA->W, TAG->O)
        TranslationResult result = translator.translate("ATGTGATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MWO", result.getConceptualTranslation());
    }

    @Test
    public void testParseCodonExceptUnquotedFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Unquoted codon format
        feature.addAttribute("codon", "(seq:tga,aa:Trp)");

        Translator translator = new Translator(feature);
        translator.setThreePrimePartial(true);
        TranslationResult result = translator.translate("ATGTGA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MW", result.getConceptualTranslation());
    }

    @Test
    public void testParseCodonExceptCaseInsensitiveFromFeature() throws TranslationException {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Lowercase amino acid
        feature.addAttribute("codon", "(seq:\"tga\",aa:trp)");

        Translator translator = new Translator(feature);
        translator.setThreePrimePartial(true);
        TranslationResult result = translator.translate("ATGTGA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MW", result.getConceptualTranslation());
    }

    @Test
    public void testParseCodonExceptInvalidFormatThrowsException() {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Invalid codon format
        feature.addAttribute("codon", "invalid_format");

        assertThrows(TranslationException.class, () -> new Translator(feature));
    }

    @Test
    public void testParseCodonExceptUnknownAminoAcidThrowsException() {
        GFF3Feature feature = new GFF3Feature(
                Optional.of("id1"), Optional.empty(), "seq1", Optional.empty(), "source", "CDS", 1, 100, ".", "+", "0");
        // Unknown amino acid
        feature.addAttribute("codon", "(seq:\"tga\",aa:Unknown)");

        assertThrows(TranslationException.class, () -> new Translator(feature));
    }

    @Test
    public void testTranslationException() throws TranslationException {
        Translator translator = createTranslator(1);
        translator.setCodonStart(1);
        translator.setThreePrimePartial(true);
        translator.addPositionException(1, 3, 'M');
        TranslationResult result = translator.translate("NNN".getBytes());
        assertTrue(result.isValid());
        assertEquals("M", result.getConceptualTranslation());
    }

    @Test
    public void testCodonException() throws TranslationException {
        Translator translator = createTranslator(1);
        translator.setThreePrimePartial(true);
        // TGA normally is stop, make it tryptophan
        translator.addCodonException("TGA", 'W');
        TranslationResult result = translator.translate("ATGTGA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MW", result.getConceptualTranslation());
    }

    @Test
    public void testTranslExceptStopCodonReadThrough() throws TranslationException {
        // transl_except for stop codon read-through: TGA -> Trp (W)
        Translator translator = createTranslator(1);
        // ATG AAA TGA AAA TAG = positions 1-3, 4-6, 7-9, 10-12, 13-15
        // TGA at position 7-9 should be translated as W instead of stop
        translator.addPositionException(7, 9, 'W');
        TranslationResult result = translator.translate("ATGAAATGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MKWK", result.getConceptualTranslation());
    }

    @Test
    public void testTranslExceptSelenocysteine() throws TranslationException {
        // transl_except for selenocysteine: TGA -> Sec (U)
        Translator translator = createTranslator(1);
        // ATG TGA AAA TAG = TGA at position 4-6 should be U (selenocysteine)
        translator.addPositionException(4, 6, 'U');
        TranslationResult result = translator.translate("ATGTGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MUK", result.getConceptualTranslation());
    }

    @Test
    public void testTranslExceptMultipleExceptions() throws TranslationException {
        // Multiple transl_except in one sequence
        Translator translator = createTranslator(1);
        // ATG TGA TGA AAA TAG
        // First TGA (4-6) -> U, Second TGA (7-9) -> W
        translator.addPositionException(4, 6, 'U');
        translator.addPositionException(7, 9, 'W');
        TranslationResult result = translator.translate("ATGTGATGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MUWK", result.getConceptualTranslation());
    }

    @Test
    public void testTranslExceptAtStartCodon() throws TranslationException {
        // transl_except at start codon position
        Translator translator = createTranslator(1);
        translator.setThreePrimePartial(true);
        // Force first codon to be M even though it's NNN
        translator.addPositionException(1, 3, 'M');
        TranslationResult result = translator.translate("NNNAAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MK", result.getConceptualTranslation());
    }

    @Test
    public void testTranslExceptPartialStopCodonAtEnd() throws TranslationException {
        // transl_except for partial stop codon at 3' end (e.g., TA or T completing to TAA)
        Translator translator = createTranslator(1);
        // ATG AAA TA - partial stop codon at end
        // Position 7-8 is "ta", add exception for stop at this position
        translator.addPositionException(7, 8, '*');
        TranslationResult result = translator.translate("ATGAAATA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MK", result.getConceptualTranslation());
    }

    @Test
    public void testTranslExceptWithCodonStart2() throws TranslationException {
        // transl_except with codon_start=2
        Translator translator = createTranslator(1);
        translator.setCodonStart(2);
        translator.setFivePrimePartial(true);
        // Sequence: A ATG TGA AAA TAG (skip first base)
        // TGA at position 5-7 (relative to sequence start) -> W
        translator.addPositionException(5, 7, 'W');
        TranslationResult result = translator.translate("AATGTGAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MWK", result.getConceptualTranslation());
    }

    @Test
    public void testTranslExceptTerminatorAminoAcid() throws TranslationException {
        // TERM amino acid exception - using '*' explicitly to create a stop codon
        Translator translator = createTranslator(1);
        // Make AAA at position 4-6 translate to stop (no threePrimePartial needed)
        translator.addPositionException(4, 6, '*');
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("M", result.getConceptualTranslation());
        assertEquals("M*", result.getTranslation());
    }

    // ========== Validation Tests ==========

    @Test
    public void testNoStartCodon() throws TranslationException {
        Translator translator = createTranslator(11);
        // Sequence doesn't start with ATG
        TranslationResult result = translator.translate("GGGAAATAG".getBytes());
        assertTrue(result.hasErrors());
    }

    @Test
    public void testNoStopCodon() throws TranslationException {
        Translator translator = createTranslator(11);
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.hasErrors());
    }

    @Test
    public void testInternalStopCodon() throws TranslationException {
        Translator translator = createTranslator(11);
        // ATG TAA AAA TAG = M * K *
        TranslationResult result = translator.translate("ATGTAAAAATAG".getBytes());
        assertTrue(result.hasErrors());
    }

    @Test
    public void testMultipleTrailingStopCodons() throws TranslationException {
        Translator translator = createTranslator(11);
        // ATG AAA TAG TAG = M K * *
        TranslationResult result = translator.translate("ATGAAATAGTAG".getBytes());
        assertTrue(result.hasErrors());
    }

    // ========== Auto-Fix Tests ==========

    @Test
    public void testFixNoStartCodonMake5Partial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFixNoStartCodonMake5Partial(true);
        TranslationResult result = translator.translate("GGGAAATAG".getBytes());
        assertTrue(result.isValid());
        assertTrue(result.isFixedFivePrimePartial());
    }

    @Test
    public void testFixNoStopCodonMake3Partial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFixNoStopCodonMake3Partial(true);
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
        assertTrue(result.isFixedThreePrimePartial());
    }

    @Test
    public void testFixInternalStopCodonMakePseudo() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFixInternalStopCodonMakePseudo(true);
        TranslationResult result = translator.translate("ATGTAAAAATAG".getBytes());
        assertTrue(result.isValid());
        assertTrue(result.isFixedPseudo());
    }

    @Test
    public void testFixNonMultipleOfThree() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFixNonMultipleOfThreeMake3And5Partial(true);
        TranslationResult result = translator.translate("ATGAAAA".getBytes()); // 7 bases
        assertTrue(result.isValid());
        assertTrue(result.isFixedFivePrimePartial());
        assertTrue(result.isFixedThreePrimePartial());
    }

    @Test
    public void testFixCodonStartNotOneMake5Partial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setCodonStart(2); // codon_start=2 without 5' partial should fail
        translator.setFixCodonStartNotOneMake5Partial(true);
        translator.setThreePrimePartial(true); // no stop codon in sequence

        assertFalse(translator.getFeature().isFivePrimePartial());
        // Skip first base, translate remaining 9 bases = 3 codons
        TranslationResult result = translator.translate("AATGAAAGGG".getBytes());
        assertTrue(result.isValid());
        assertTrue(translator.isFivePrimePartial());
        assertTrue(translator.getFixes().contains("fixCodonStartNotOneMake5Partial"));
        assertTrue(translator.getFeature().isFivePrimePartial());
    }

    @Test
    public void testFixValidStopCodonRemove3Partial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setThreePrimePartial(true); // marked as 3' partial but has stop codon
        translator.setFixValidStopCodonRemove3Partial(true);
        TranslationResult result = translator.translate("ATGAAATAG".getBytes());
        assertTrue(result.isValid());
        assertTrue(result.isFixedThreePrimePartial());
        assertFalse(translator.isThreePrimePartial()); // should be removed
        assertTrue(translator.getFixes().contains("fixValidStopCodonRemove3Partial"));
    }

    @Test
    public void testFixDegenerateStartCodon() throws TranslationException {
        // Use table 1 (standard code) where GTG is NOT a start codon
        Translator translator = createTranslator(1);
        translator.setFixDegenerateStartCodon(true);
        // RTG where R = A or G expands to ATG (start -> M) and GTG (not start -> V)
        // Without fix: would translate to X (ambiguous)
        // With fix: recognizes it could be a start codon and forces M
        TranslationResult result = translator.translate("RTGAAATAG".getBytes());
        assertTrue(result.isValid());
        assertEquals("MK", result.getConceptualTranslation());
        assertTrue(result.isFixedDegenerateStartCodon());
    }

    @Test
    public void testFixDeleteTrailingBasesAfterStopCodon() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFixDeleteTrailingBasesAfterStopCodon(true);
        // Need threePrimePartial to allow non-multiple-of-3 length
        translator.setThreePrimePartial(true);
        // Need this fix because threePrimePartial + stop codon would otherwise error
        translator.setFixValidStopCodonRemove3Partial(true);
        // ATG AAA TAG AA - has 2 trailing bases after stop codon (not a complete codon)
        TranslationResult result = translator.translate("ATGAAATAGAA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MK", result.getConceptualTranslation());
        assertEquals("AA", result.getTrailingBases());
    }

    @Test
    public void testEnableAllFixes() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.enableAllFixes();

        // Sequence with internal stop, no proper start, no proper stop
        TranslationResult result = translator.translate("GGGTAGAAA".getBytes());
        assertTrue(result.isValid());
    }

    // ========== Different Translation Tables ==========

    @Test
    public void testVertebrateMitochondrial() throws TranslationException {
        Translator translator = createTranslator(2); // Vertebrate mitochondrial
        translator.setThreePrimePartial(true);
        // TGA is W in vertebrate mitochondria
        TranslationResult result = translator.translate("ATGTGA".getBytes());
        assertTrue(result.isValid());
        assertEquals("MW", result.getConceptualTranslation());
    }

    @Test
    public void testYeastMitochondrial() throws TranslationException {
        Translator translator = createTranslator(3); // Yeast mitochondrial
        translator.setThreePrimePartial(true);
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
    }

    // ========== Ambiguous Base Tests ==========

    @Test
    public void testAmbiguousBaseR() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setThreePrimePartial(true);
        // R = A or G
        TranslationResult result = translator.translate("ATGRRR".getBytes());
        assertTrue(result.isValid());
    }

    @Test
    public void testAmbiguousBaseY() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setThreePrimePartial(true);
        // Y = C or T
        TranslationResult result = translator.translate("ATGYYY".getBytes());
        assertTrue(result.isValid());
    }

    @Test
    public void testAmbiguousBaseN() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setThreePrimePartial(true);
        // N = any base
        TranslationResult result = translator.translate("ATGNNN".getBytes());
        assertTrue(result.isValid());
        // NNN -> X (unknown amino acid)
        assertTrue(result.getConceptualTranslation().contains("X"));
    }

    // ========== Translation Comparison Tests ==========

    @Test
    public void testEqualsTranslationMatch() throws TranslationException {
        Translator translator = createTranslator(11);
        Translator.TranslationComparison comparison = translator.equalsTranslation("MK", "MK");
        assertTrue(comparison.matches());
        assertEquals(0, comparison.xMismatchCount());
    }

    @Test
    public void testEqualsTranslationMismatch() throws TranslationException {
        Translator translator = createTranslator(11);
        Translator.TranslationComparison comparison = translator.equalsTranslation("MK", "ML");
        assertFalse(comparison.matches());
    }

    @Test
    public void testEqualsTranslationWithX() throws TranslationException {
        Translator translator = createTranslator(11);
        // When expected has X, it should match any character - but return false for exact match
        // X is a mismatch marker, so xMismatchCount should be 1 but exact match should be false
        Translator.TranslationComparison comparison = translator.equalsTranslation("MX", "MK");
        // The comparison considers X as "any" so it's a match with xMismatch > 0
        assertFalse(comparison.matches()); // Not an exact match because of X
        assertEquals(1, comparison.xMismatchCount());
    }

    // ========== Edge Cases ==========

    @Test
    public void testEmptySequence() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFivePrimePartial(true);
        translator.setThreePrimePartial(true);
        TranslationResult result = translator.translate("".getBytes());
        assertEquals(0, result.getCodons().size());
    }

    @Test
    public void testSingleStopCodonFivePartial() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setFivePrimePartial(true);
        TranslationResult result = translator.translate("TAG".getBytes());
        assertTrue(result.isValid());
        assertEquals(0, result.getConceptualTranslationCodons());
    }

    @Test
    public void testLongSequence() throws TranslationException {
        Translator translator = createTranslator(11);
        // Generate a long valid CDS
        StringBuilder sb = new StringBuilder("ATG");
        for (int i = 0; i < 100; i++) {
            sb.append("AAA"); // Lysine codons
        }
        sb.append("TAG"); // Stop codon

        TranslationResult result = translator.translate(sb.toString().getBytes());
        assertTrue(result.isValid());
        assertEquals(101, result.getConceptualTranslationCodons()); // M + 100 K
    }

    @Test
    public void testPeptideFeatureNoStopRequired() throws TranslationException {
        Translator translator = createTranslator(11);
        translator.setPeptideFeature(true);
        // Peptide features don't require stop codons
        TranslationResult result = translator.translate("ATGAAA".getBytes());
        assertTrue(result.isValid());
    }

    @Test
    public void testInvalidBase() throws TranslationException {
        Translator translator = createTranslator(11);
        // 'z' is not a valid base
        TranslationResult result = translator.translate("ATGZAATAG".getBytes());
        assertTrue(result.hasErrors());
    }

    // ========== Simple Translation Tests (migrated from SimpleTranslatorTest) ==========

    @Test
    public void testTranslationWithInternalStopCodons() throws TranslationException {
        // Sequence with internal stop codons - requires exception flag
        String sequence = "NACGTAAAACCCGGTTAACCGGTCACAAGTGCATCGATCGNN";
        Translator translator = createTranslator(1);
        translator.setFivePrimePartial(true);
        translator.setThreePrimePartial(true);
        translator.setException(true); // Allow internal stop codons
        TranslationResult result = translator.translate(sequence.getBytes());
        assertEquals("XVKPG*PVTSASIX", result.getTranslation());
    }

    @Test
    public void testTranslationWithoutStopCodon() throws TranslationException {
        String sequence = "CGTAAAACCCGGTTAACCGGTCACAAGTGCATCGATCGN";
        Translator translator = createTranslator(1);
        translator.setFivePrimePartial(true);
        translator.setThreePrimePartial(true);
        TranslationResult result = translator.translate(sequence.getBytes());
        assertEquals("RKTRLTGHKCIDR", result.getTranslation());
    }

    @Test
    public void testAmbiguousBasesOnlyReturnsError() throws TranslationException {
        Translator translator = createTranslator(1);
        translator.setFivePrimePartial(true);
        translator.setThreePrimePartial(true);
        // NNN translates to X (unknown) - Translator rejects sequences with >50% X
        TranslationResult result = translator.translate("NNN".getBytes());
        assertTrue(result.hasErrors(), "Sequence with 100% unknown amino acids should fail validation");
    }

    @Test
    public void testTrailingBasesIgnored() throws TranslationException {
        Translator translator = createTranslator(1);
        translator.setThreePrimePartial(true);
        // Only complete codons are translated, trailing "aa" is ignored
        TranslationResult result = translator.translate("ATGAA".getBytes());
        assertEquals("M", result.getTranslation());
    }

    @Test
    public void testWithFile() throws TranslationException, FastaFileException, IOException {

        GFF3Feature feature = new GFF3Feature(
                Optional.of("id"),
                Optional.empty(),
                "seq",
                Optional.empty(),
                "source",
                "CDS",
                4798,
                5460,
                ".",
                "-",
                "0");
        feature.addAttribute("codon_start", "1");
        feature.addAttribute("transl_table", "11");

        String expectedTranslation =
                "MATQSREIGIQAKNKPGHWVQTERKAHEAWAGLIARKPTAAMLLHHLVAQMGHQNAVVVSQKTLSKLIGRSLRTVQYAVKDLVAERWISVVKLNGPGTVSAYVVNDRVAWGQPRDQLRLSVFSAAVVVDHDDQDESLLGHGDLRRIPTLYPGEQQLPTGPGEEPPSQPGIPGMEPDLPALTETEEWERRGQQRLPMPDEPCFLDDGEPLEPPTRVTLPRR";
        FastaReader reader = new FastaReader(new File(
                getClass().getClassLoader().getResource("translation/fasta.txt").getFile()));

        Translator translator = new Translator(feature);
        translator.setThreePrimePartial(true);
        String sequence = reader.getSequenceSliceString(0L, 4798, 5460).toUpperCase(Locale.ROOT);

        TranslationResult result = translator.translate(sequence.getBytes());
        Translator.TranslationComparison comparison =
                translator.equalsTranslation(expectedTranslation, result.getConceptualTranslation());
        assertTrue(comparison.matches());
    }

    @Test
    public void testWithFileActual() throws TranslationException, FastaFileException, IOException {

        GFF3Feature feature = new GFF3Feature(
                Optional.of("id"),
                Optional.empty(),
                "seq",
                Optional.empty(),
                "source",
                "CDS",
                1,
                240758,
                ".",
                "-",
                "0");
        feature.addAttribute("codon_start", "1");
        feature.addAttribute("transl_table", "1");
        FastaReader reader = new FastaReader(new File(getClass()
                .getClassLoader()
                .getResource("translation/OY639224.seq")
                .getFile()));

        // String expectedTranslation =
        // "MAAAVGRRACPAGRRSDAVIRVARSERLEMRHRSSWYASRPRGVCRLRVLLLVCAAVVPIVSSAPLPGYYCYAKNATERRTPNIDRDYWRPGVSVFGCRMPKGVCIEGEWTIEWYIPSLQASVINQVFFKSQTWLGPSMQYIIPSYERGKEVTCRQGFCVDRAEGNLIITDNNTRKEEWARKPTRDVVCKLTACLRATSVSPYSRTTYEECNGTLEDYLSLPDFENIYDISKVVPYVPPPKAPAVPPKVPGKAEDAPPDESCIGCDNPGLNAAAIAVPVVTVIVLVSGIGYLCRSTESRQRTLELYRDLWSSLRRRLHRGDYARDG";
        String expectedTranslation =
                "MKRIGLERCFLSTSYRSTRFPSTALPRLTAERRSTFFTPDPKPRGGGCPANTPRVLYPPPHPGVRRAVKRLLPRNIRRRSRNARFTALRAGRRSSRKLHTLAGRLTPPSRGRGARPGG";
        Translator translator = new Translator(feature);
        translator.setThreePrimePartial(true);
        // String sequence = reader.getSequenceSliceString(0L,999,1979).toUpperCase(Locale.ROOT);
        String sequence = reader.getSequenceSliceString(0L, 480, 836).toUpperCase(Locale.ROOT);

        TranslationResult result = translator.translate(sequence.getBytes());
        assertEquals(expectedTranslation, result.getConceptualTranslation());
        // assertEquals("M", result.getTranslation());
    }
}
