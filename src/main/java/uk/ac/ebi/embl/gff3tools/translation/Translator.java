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

import java.util.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.translation.except.CodonExceptAttribute;
import uk.ac.ebi.embl.gff3tools.translation.except.TranslExceptAttribute;
import uk.ac.ebi.embl.gff3tools.translation.tables.TranslationTable;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

/**
 * Translates DNA/RNA sequences to amino acid sequences with full validation.
 * The bases are encoded using lower case single letter JCBN abbreviations
 * and the amino acids are encoded using upper case single letter JCBN abbreviations.
 *
 */
public class Translator {

    private final CodonTranslator codonTranslator;

    // Map of exception amino acid for a specific position
    private final Map<Integer, PositionExceptionData> positionExceptionMap = new HashMap<>();

    @Getter
    @Setter
    private int codonStart = 1;

    @Getter
    @Setter
    private boolean nonTranslating = false; // feature has /pseudo

    @Getter
    private boolean isComplement = false;

    @Getter
    private GFF3Feature feature;


    // The purpose of 'exception' is to relaxes several validations.
    // This will be used in the future to support more flexible translation.
    @Getter
    @Setter
    private boolean exception = false;

    @Getter
    @Setter
    private boolean threePrimePartial = false; // 3' partial

    @Getter
    @Setter
    private boolean fivePrimePartial = false; // 5' partial

    @Getter
    @Setter
    private boolean peptideFeature = false;

    // Fix options
    @Setter
    private boolean fixDegenerateStartCodon = false;

    @Setter
    private boolean fixNoStartCodonMake5Partial = false;

    @Setter
    private boolean fixCodonStartNotOneMake5Partial = false;

    @Setter
    private boolean fixNoStopCodonMake3Partial = false;

    @Setter
    private boolean fixValidStopCodonRemove3Partial = false;

    @Setter
    private boolean fixNonMultipleOfThreeMake3And5Partial = false;

    @Setter
    private boolean fixInternalStopCodonMakePseudo = false;

    @Setter
    private boolean fixDeleteTrailingBasesAfterStopCodon = false;

    @Getter
    private final Set<String> fixes = new HashSet<>();


    /**
     * Creates a new Translator with the specified translation table and GFF3 feature.
     * If the feature has a "pseudo" or "pseudogene" attribute, the translator is set to non-translating mode.
     *
     * @param feature the GFF3 feature associated with this translation
     * @throws TranslationException if the translation table is invalid
     */
    public Translator(GFF3Feature feature) throws TranslationException {

        //TODO: Get translation table from taxon.
        int translationTable = feature.getAttribute("transl_table")
                .map(Integer::parseInt)
                .orElse(TranslationTable.DEFAULT_TRANSLATION_TABLE);

        this.codonTranslator = new CodonTranslator(translationTable);
        this.feature = feature;
        if (feature.hasAttribute(GFF3Attributes.PSEUDO) || feature.hasAttribute(GFF3Attributes.PSEUDOGENE)) {
            this.nonTranslating = true;
        }

        if (feature.getStrand().equals("-")) {
            isComplement = true;
        }

        fivePrimePartial = feature.isFivePrimePartial();

        threePrimePartial = feature.isThreePrimePartial();

        // Parse transl_except attributes and add position exceptions
        handleTranslExceptAttributes(feature);

        // Parse codon attributes and add codon exceptions
        handleCodonExceptAttributes(feature);

        setPeptideFeature();
    }

    private void setPeptideFeature() {
        OntologyClient client = ConversionUtils.getOntologyClient();
        Optional<String> soIdOpt = client.findTermByNameOrSynonym(feature.getName());
        peptideFeature =  soIdOpt.isPresent() && client.isSelfOrDescendantOf(soIdOpt.get(), OntologyTerm.PROPEPTIDE.ID);
    }

    /**
     * Parses transl_except attributes from a GFF3 feature and adds position exceptions.
     * Format: transl_except=(pos:213..215,aa:Trp)
     *
     * @param feature the GFF3 feature containing transl_except attributes
     */
    private void handleTranslExceptAttributes(GFF3Feature feature) throws TranslationException {
        if (!feature.hasAttribute(GFF3Attributes.TRANSL_EXCEPT)) {
            return;
        }

        List<String> translExceptValues =
                feature.getAttributeList(GFF3Attributes.TRANSL_EXCEPT).orElse(List.of());
        for (String translExceptValue : translExceptValues) {
            TranslExceptAttribute attribute = new TranslExceptAttribute(translExceptValue);
            addPositionException(
                    attribute.getStartPosition(),
                    attribute.getEndPosition(),
                    attribute.getAminoAcid());
        }
    }

    /**
     * Parses codon attributes from a GFF3 feature and adds codon exceptions.
     * Format: codon=(seq:"tga",aa:Trp)
     *
     * @param feature the GFF3 feature containing codon attributes
     */
    private void handleCodonExceptAttributes(GFF3Feature feature) throws TranslationException {
        if (!feature.hasAttribute(GFF3Attributes.CODON)) {
            return;
        }

        List<String> codonValues =
                feature.getAttributeList(GFF3Attributes.CODON).orElse(List.of());
        for (String codonValue : codonValues) {
            CodonExceptAttribute attribute = new CodonExceptAttribute(codonValue);
            addCodonException(attribute.getCodon(), attribute.getAminoAcid());
        }
    }

    /**
     * Enables all auto-fix options.
     */
    public void enableAllFixes() {
        fixNoStartCodonMake5Partial = true;
        fixCodonStartNotOneMake5Partial = true;
        fixNoStopCodonMake3Partial = true;
        fixValidStopCodonRemove3Partial = true;
        fixNonMultipleOfThreeMake3And5Partial = true;
        fixInternalStopCodonMakePseudo = true;
    }

    // Exception handling

    /**
     * Adds a position-specific exception where a codon at a specific position translates differently.
     * Used for /transl_except qualifiers.
     *
     * @param beginPosition the start position (1-based)
     * @param endPosition the end position (1-based)
     * @param aminoAcid the amino acid to use instead of the standard translation
     */
    public void addPositionException(Integer beginPosition, Integer endPosition, Character aminoAcid) {
        PositionExceptionData translationPosException = new PositionExceptionData();
        translationPosException.beginPosition = beginPosition;
        translationPosException.endPosition = endPosition;
        translationPosException.aminoAcid = aminoAcid;
        positionExceptionMap.put(beginPosition, translationPosException);
    }

    /**
     * Adds a codon exception where a specific codon always translates to a specific amino acid.
     * Used for /codon qualifiers.
     *
     * @param codon the codon (lowercase, e.g., "tga")
     * @param aminoAcid the amino acid to use
     */
    public void addCodonException(String codon, Character aminoAcid) {
        codonTranslator.addCodonException(codon, aminoAcid);
    }

    public TranslationResult translate(byte[] sequence) {
        TranslationResult translationResult = new TranslationResult();

        if (nonTranslating) {
            translationResult.setConceptualTranslationCodons(0);
            return translationResult;
        }

        if (sequence == null) {
            translationResult.addError("Sequence is null");
            return translationResult;
        }

        if (isComplement) {
            sequence = reverseComplement(sequence);
        }

        // Validate sequence bases
        if (!validateSequenceBases(sequence, translationResult)) {
            return translationResult;
        }

        try {
            validateCodonStart(sequence.length, translationResult);
            sequence = processTranslationExceptions(sequence);
            validateCodons(sequence.length, translationResult);
            translateCodons(sequence, translationResult);

            if (translationResult.getCodons().isEmpty()) {
                if (exception) {
                    translationResult.setConceptualTranslationCodons(0);
                    return translationResult;
                } else {
                    TranslationException.throwError("No translation produced");
                }
            }

            validateTranslation(translationResult);

            if (translationResult.isFixedFivePrimePartial()) {
                feature.setFivePrimePartial();
            }

            if (translationResult.isFixedThreePrimePartial()) {
                feature.setThreePrimePartial();
            }

            if (translationResult.isFixedPseudo()) {
                feature.addAttribute("pseudo", "true");
                feature.removeAttributeList("translation");
            }

        } catch (TranslationException ex) {
            translationResult.addError(ex.getMessage());
        }

        return translationResult;
    }

    private void translateCodons(byte[] sequence, TranslationResult translationResult) throws TranslationException {
        int countX = 0;
        int bases = sequence.length;
        List<Codon> codons = new ArrayList<>(bases / 3);

        // Complete codons
        int i = codonStart - 1;
        for (; i + 3 <= bases; i += 3) {
            String codonStr = new String(Arrays.copyOfRange(sequence, i, i + 3));
            Codon codon = translateCodonAt(codonStr, i, translationResult);
            codons.add(codon);

            if (codon.getAminoAcid() == 'X') {
                countX++;
            }
        }

        // Check if more than 50% unknown amino acids
        if (countX > (codons.size() / 2)) {
            TranslationException.throwError("Translation has more than 50% unknown amino acids (X)");
        }

        // Handle trailing bases
        int trailingBases = bases - i;
        if (trailingBases > 0) {
            String codonStr = extendCodon(new String(Arrays.copyOfRange(sequence, i, sequence.length)));
            Codon codon = translateCodonAt(codonStr, i, translationResult);

            // Discard partial codon translations X
            if (codon.getAminoAcid() != 'X') {
                trailingBases = 0;
                codons.add(codon);
            }
        }

        translationResult.setCodons(codons);
        if (trailingBases > 0) {
            translationResult.setTrailingBases(
                    new String(Arrays.copyOfRange(sequence, sequence.length - trailingBases, sequence.length)));
        } else {
            translationResult.setTrailingBases("");
        }
    }


    private Codon translateCodonAt(String codonStr, int index, TranslationResult translationResult)
            throws TranslationException {
        int position = index + 1;
        boolean isStartCodon = (index == codonStart - 1) && !fivePrimePartial;
        return isStartCodon
                ? translateStartCodon(codonStr, position, translationResult)
                : translateOtherCodon(codonStr, position);
    }

    private Codon translateStartCodon(String codonStr, int position, TranslationResult translationResult)
            throws TranslationException {
        char translatedAminoAcid = codonTranslator.translateStartCodon(codonStr);
        Character exceptionAminoAcid = getPositionExceptionAminoAcid(position);
        boolean isTranslationException = exceptionAminoAcid != null;

        char aminoAcid;
        if (isTranslationException) {
            aminoAcid = exceptionAminoAcid;
        } else if (fixDegenerateStartCodon
                && !fivePrimePartial
                && translatedAminoAcid != 'M'
                && codonTranslator.isDegenerateStartCodon(codonStr)) {
            aminoAcid = 'M';
            isTranslationException = true;
            translationResult.setFixedDegenerateStartCodon(true);
        } else {
            aminoAcid = translatedAminoAcid;
        }

        return new Codon(codonStr, position, aminoAcid, isTranslationException);
    }

    private Codon translateOtherCodon(String codonStr, int position) throws TranslationException {
        char translatedAminoAcid = codonTranslator.translateOtherCodon(codonStr);
        Character exceptionAminoAcid = getPositionExceptionAminoAcid(position);
        boolean isTranslationException = exceptionAminoAcid != null;

        char aminoAcid = isTranslationException ? exceptionAminoAcid : translatedAminoAcid;

        return new Codon(codonStr, position, aminoAcid, isTranslationException);
    }

    // Extra validation though done in FASTA reader
    private boolean validateSequenceBases(byte[] sequence, TranslationResult result) {
        for (byte b : sequence) {
            char c = Character.toLowerCase((char) b);
            if (c != 'a' && c != 't' && c != 'c' && c != 'g' && c != 'r' && c != 'y' && c != 'm' && c != 'k'
                    && c != 's' && c != 'w' && c != 'h' && c != 'b' && c != 'v' && c != 'd' && c != 'n') {
                result.addError("Invalid base character in sequence");
                return false;
            }
        }
        return true;
    }

    private String extendCodon(String codon) {
        // Adds 'n' when codon length is < 3
        return (codon + "nnn").substring(0, 3);
    }

    private Character getPositionExceptionAminoAcid(int position) {
        PositionExceptionData translationException = positionExceptionMap.get(position);
        if (translationException != null) {
            return translationException.aminoAcid;
        }
        return null;
    }

    private void validateCodonStart(int bases, TranslationResult translationResult) throws TranslationException {
        if (codonStart < 1 || codonStart > 3) {
            TranslationException.throwError("Invalid codon start: " + codonStart + ". Must be 1, 2, or 3");
        }

        if (codonStart != 1) {
            if (!fivePrimePartial && !nonTranslating) {
                if (fixCodonStartNotOneMake5Partial) {
                    fivePrimePartial = true;
                    translationResult.setFixedFivePrimePartial(true);
                    fixes.add("fixCodonStartNotOneMake5Partial");
                } else {
                    TranslationException.throwError("Codon start is " + codonStart + " but feature is not 5' partial");
                }
            }
        }

        if (bases < 3) {
            if (codonStart != 1) {
                TranslationException.throwError("Sequence too short for translation with current codon start");
            }
        }
    }

    private byte[] processTranslationExceptions(byte[] sequence) throws TranslationException {
        int sequenceLength = sequence.length;

        for (PositionExceptionData exception : positionExceptionMap.values()) {
            int beginPos = exception.beginPosition;
            int endPos = exception.endPosition != null ? exception.endPosition : beginPos;
            char aminoAcid = exception.aminoAcid;

            validateExceptionBounds(beginPos, endPos, sequenceLength);
            validateExceptionSpansCodon(beginPos, endPos, sequenceLength, aminoAcid);
            validateExceptionInReadingFrame(beginPos);

            sequence = extendSequenceForPartialStopCodon(sequence, beginPos, endPos, aminoAcid);
        }
        return sequence;
    }

    private void validateExceptionBounds(int beginPos, int endPos, int sequenceLength) throws TranslationException {
        if (beginPos < codonStart) {
            TranslationException.throwError("Translation exception outside frame on the 5' end");
        }
        if (beginPos > sequenceLength || endPos > sequenceLength) {
            TranslationException.throwError("Translation exception outside frame on the 3' end");
        }
        if (endPos < beginPos) {
            TranslationException.throwError("Invalid translation exception range");
        }
    }

    private void validateExceptionSpansCodon(int beginPos, int endPos, int sequenceLength, char aminoAcid)
            throws TranslationException {
        int span = endPos - beginPos;
        boolean isFullCodon = (span == 2);
        boolean isPartialStopAtEnd = (aminoAcid == '*') && (endPos == sequenceLength) && (span == 0 || span == 1);

        if (!isFullCodon && !isPartialStopAtEnd) {
            TranslationException.throwError(
                    "Translation exception must span 3 bases or be a partial stop codon at 3' end");
        }
    }

    private void validateExceptionInReadingFrame(int beginPos) throws TranslationException {
        int frame = (beginPos % 3 == 0) ? 3 : beginPos % 3;
        if (frame != codonStart) {
            TranslationException.throwError("Translation exception at position " + beginPos + " is in frame " + frame
                    + " but codon start is " + codonStart);
        }
    }

    private byte[] extendSequenceForPartialStopCodon(byte[] sequence, int beginPos, int endPos, char aminoAcid) {
        if (aminoAcid != '*') {
            return sequence;
        }

        int missingBases = 2 - (endPos - beginPos);
        if (missingBases > 0 && endPos == sequence.length) {
            byte[] extended = Arrays.copyOf(sequence, sequence.length + missingBases);
            for (int i = 0; i < missingBases; i++) {
                extended[sequence.length + i] = 'n';
            }
            return extended;
        }
        return sequence;
    }

    private void validateCodons(int bases, TranslationResult translationResult) throws TranslationException {
        if (bases < 3) {
            translationResult.setTranslationBaseCount(bases);
            if (!fivePrimePartial && !threePrimePartial) {
                TranslationException.throwError("CDS feature with less than 3 bases must be 3' or 5' partial");
            }
        } else if ((bases - codonStart + 1) % 3 != 0) {
            int length = bases - codonStart + 1;
            translationResult.setTranslationLength(length);

            if (!peptideFeature && !fivePrimePartial && !threePrimePartial && !nonTranslating && !exception) {
                if (fixNonMultipleOfThreeMake3And5Partial) {
                    fivePrimePartial = true;
                    threePrimePartial = true;
                    translationResult.setFixedThreePrimePartial(true);
                    translationResult.setFixedFivePrimePartial(true);
                    fixes.add("fixNonMultipleOfThreeMake3And5Partial");
                } else {
                    TranslationException.throwError(
                            "CDS feature length must be a multiple of 3. Consider 5' or 3' partial location");
                }
            }
        }
    }

    private void validateTranslation(TranslationResult translationResult) throws TranslationException {
        int trailingStopCodons = 0;
        int internalStopCodons = 0;
        List<Codon> codons = translationResult.getCodons();
        int i = codons.size();

        // Count trailing stop codons
        while (i > 0 && codons.get(i - 1).getAminoAcid() == '*') {
            --i;
            ++trailingStopCodons;
        }

        int conceptualTranslationCodons = codons.size() - trailingStopCodons;
        translationResult.setConceptualTranslationCodons(conceptualTranslationCodons);

        if (conceptualTranslationCodons == 0) {
            validateStopCodonOnly(translationResult);
            validateTrailingStopCodons(trailingStopCodons, translationResult);
        } else {
            // Count internal stop codons
            while (i > 0) {
                if (codons.get(i - 1).getAminoAcid() == '*') {
                    ++internalStopCodons;
                }
                --i;
            }

            boolean conceptualTranslation = validateInternalStopCodons(internalStopCodons, translationResult);
            if (!validateStartCodon(translationResult)) {
                conceptualTranslation = false;
            }
            if (!validateTrailingStopCodons(trailingStopCodons, translationResult)) {
                conceptualTranslation = false;
            }
            if (!conceptualTranslation) {
                translationResult.setConceptualTranslationCodons(0);
            }
        }
    }

    private void validateStopCodonOnly(TranslationResult translationResult) throws TranslationException {
        if (exception || nonTranslating) {
            return;
        }
        if (!(translationResult.getCodons().size() == 1
                && translationResult.getTrailingBases().length() == 0
                && fivePrimePartial)) {
            TranslationException.throwError(
                    "CDS feature can have a single stop codon only if it has 3 bases and is 5' partial");
        }
    }

    private boolean validateTrailingStopCodons(int trailingStopCodons, TranslationResult translationResult)
            throws TranslationException {
        if (!exception) {
            if (trailingStopCodons > 1) {
                if (nonTranslating) {
                    return false;
                } else {
                    TranslationException.throwError("More than one stop codon at the 3' end");
                }
            }

            if (trailingStopCodons == 1 && threePrimePartial) {
                if (nonTranslating) {
                    return false;
                } else {
                    if (fixValidStopCodonRemove3Partial) {
                        translationResult.setFixedThreePrimePartial(true);
                        threePrimePartial = false;
                        fixes.add("fixValidStopCodonRemove3Partial");
                    } else {
                        TranslationException.throwError(
                                "Stop codon found at 3' partial end. Consider removing 3' partial location");
                    }
                }
            }

            if (trailingStopCodons == 0 && !threePrimePartial) {
                if (nonTranslating) {
                    return false;
                } else if (!peptideFeature) {
                    if (fixNoStopCodonMake3Partial) {
                        threePrimePartial = true;
                        translationResult.setFixedThreePrimePartial(true);
                        fixes.add("fixNoStopCodonMake3Partial");
                    } else {
                        TranslationException.throwError("No stop codon at the 3' end");
                    }
                }
            }

            if (trailingStopCodons == 1 && translationResult.getTrailingBases().length() > 0) {
                if (nonTranslating) {
                    return false;
                } else {
                    if (!fixDeleteTrailingBasesAfterStopCodon) {
                        TranslationException.throwError("A partial codon appears after the stop codon");
                    }
                }
            }
        }
        return true;
    }

    private boolean validateInternalStopCodons(int internalStopCodons, TranslationResult translationResult)
            throws TranslationException {
        if (internalStopCodons > 0) {
            if (exception || nonTranslating) {
                return false;
            } else {
                if (fixInternalStopCodonMakePseudo) {
                    translationResult.setFixedPseudo(true);
                    nonTranslating = true;
                    fixes.add("fixInternalStopCodonMakePseudo");
                    return false;
                } else {
                    TranslationException.throwError("The protein translation contains internal stop codons");
                }
            }
        }
        return true;
    }

    private boolean validateStartCodon(TranslationResult translationResult) throws TranslationException {
        if (!fivePrimePartial && !exception && !peptideFeature) {
            if (translationResult.getCodons().get(0).getAminoAcid() != 'M') {
                if (nonTranslating) {
                    return false;
                } else {
                    if (fixNoStartCodonMake5Partial) {
                        translationResult.setFixedFivePrimePartial(true);
                        fivePrimePartial = true;
                        fixes.add("fixNoStartCodonMake5Partial");
                    } else {
                        TranslationException.throwError("The protein translation does not start with methionine");
                    }
                }
            }
        }
        return true;
    }

    /**
     * Compares expected translation with conceptual translation.
     *
     * @param expectedTranslation the expected translation
     * @param conceptualTranslation the conceptual translation
     * @return a pair of (matches, xMismatchCount)
     */
    public TranslationComparison equalsTranslation(String expectedTranslation, String conceptualTranslation) {
        int xMismatch = 0;

        if (expectedTranslation.length() < conceptualTranslation.length()) {
            return new TranslationComparison(false, 0);
        }

        for (int i = 0; i < conceptualTranslation.length(); i++) {
            if (expectedTranslation.charAt(i) != conceptualTranslation.charAt(i)) {
                if (expectedTranslation.charAt(i) == 'X') {
                    xMismatch++;
                } else {
                    return new TranslationComparison(false, 0);
                }
            }
        }

        // Ignore trailing X
        if (expectedTranslation.length() > conceptualTranslation.length()) {
            for (int i = conceptualTranslation.length(); i < expectedTranslation.length(); i++) {
                if (expectedTranslation.charAt(i) == 'X') {
                    xMismatch++;
                } else {
                    return new TranslationComparison(false, 0);
                }
            }
        }

        return new TranslationComparison(xMismatch == 0, xMismatch);
    }

    public record TranslationComparison(boolean matches, int xMismatchCount) {}




    public static byte[] reverseComplement(byte[] seq) {

        final byte[] COMPLEMENT = new byte[128];
        COMPLEMENT['a'] = 't';
        COMPLEMENT['t'] = 'a';
        COMPLEMENT['u'] = 'a';
        COMPLEMENT['c'] = 'g';
        COMPLEMENT['g'] = 'c';
        COMPLEMENT['r'] = 'y';
        COMPLEMENT['y'] = 'r';
        COMPLEMENT['s'] = 's';
        COMPLEMENT['w'] = 'w';
        COMPLEMENT['k'] = 'm';
        COMPLEMENT['m'] = 'k';
        COMPLEMENT['b'] = 'v';
        COMPLEMENT['d'] = 'h';
        COMPLEMENT['h'] = 'd';
        COMPLEMENT['v'] = 'b';
        COMPLEMENT['n'] = 'n';

        int len = seq.length;
        byte[] rc = new byte[len];

        for (int i = 0; i < len; i++) {
            rc[len - 1 - i] = COMPLEMENT[seq[i]];
        }
        return rc;
    }

    private static class PositionExceptionData {
        Character aminoAcid;
        Integer beginPosition;
        Integer endPosition;
    }
}
