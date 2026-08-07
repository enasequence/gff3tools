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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.ANTI_CODON;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationRegistry;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;

class AnticodonAttributeFixTest {

    private static final String SEQ_ID = "CHR1";
    private static final String PLUS = "+";
    private static final String MINUS = "-";

    private AnticodonAttributeFix fix;
    private GFF3Annotation annotation;
    private SequenceLookup lookup;

    @BeforeEach
    void setUp() {
        fix = new AnticodonAttributeFix();
        annotation = new GFF3Annotation();
        lookup = mock(SequenceLookup.class);
        when(lookup.hasSequence(anyString())).thenReturn(true);
        ValidationRegistry.injectContext(fix, contextWith(lookup));
    }

    // --- amino acid case ------------------------------------------------

    @Test
    void correctsMixedCaseAminoAcid() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:SeC)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Sec)"), anticodon(feature));
    }

    @Test
    void correctsLowerCaseAminoAcid() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:glu)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu)"), anticodon(feature));
    }

    @Test
    void leavesCorrectlyCasedAminoAcidAlone() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Lys)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Lys)"), anticodon(feature));
    }

    @Test
    void leavesUnknownAminoAcidAlone() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:MOO)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:MOO)"), anticodon(feature));
    }

    @Test
    void keepsUpperCaseStopCodonAbbreviations() {
        GFF3Feature term = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:TERM)");
        GFF3Feature ter = tRna("t2", 1, 100, PLUS, "(pos:20..22,aa:TER)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:TERM)"), anticodon(term));
        assertEquals(List.of("(pos:20..22,aa:TER)"), anticodon(ter));
    }

    @Test
    void preservesSpacingWhenCorrectingAminoAcid() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "( pos: 10..12 , aa : sEc )");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("( pos: 10..12 , aa : Sec )"), anticodon(feature));
    }

    @Test
    void preservesExistingSequenceWhenCorrectingAminoAcid() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:lYs,seq:ttt)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Lys,seq:ttt)"), anticodon(feature));
    }

    @Test
    void leavesUnparseableValueAlone() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:join(10..12,20..22),aa:leu)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:join(10..12,20..22),aa:leu)"), anticodon(feature));
    }

    @Test
    void correctsEveryValueOnAFeature() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:glu)", "(pos:20..22,aa:lys)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu)", "(pos:20..22,aa:Lys)"), anticodon(feature));
    }

    @Test
    void leavesJoinedRowsWithIdenticalText() {
        GFF3Feature first = tRna("t1", 1, 50, PLUS, "(pos:10..12,aa:glu)");
        GFF3Feature second = tRna("t1", 60, 100, PLUS, "(pos:10..12,aa:glu)");

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(anticodon(first), anticodon(second));
        assertEquals(List.of("(pos:10..12,aa:Glu)"), anticodon(first));
    }

    @Test
    void correctsAminoAcidWithoutAnySequenceSource() {
        AnticodonAttributeFix noSequenceFix = new AnticodonAttributeFix();
        ValidationRegistry.injectContext(noSequenceFix, new ValidationContext());
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:SeC)");

        noSequenceFix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Sec)"), anticodon(feature));
    }

    @Test
    void ignoresFeaturesWithoutAnticodon() {
        GFF3Feature feature = tRna("t1", 1, 100, PLUS);

        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of(), anticodon(feature));
    }

    // --- sequence -------------------------------------------------------

    @Test
    void addsSequenceOnPlusStrand() throws Exception {
        stubSlice("TTA");
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu,seq:tta)"), anticodon(feature));
    }

    @Test
    void reverseComplementsWhenPositionCarriesComplement() throws Exception {
        stubSlice("CAA");
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:complement(10..12),aa:Gln)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:complement(10..12),aa:Gln,seq:ttg)"), anticodon(feature));
    }

    @Test
    void reverseComplementsOnMinusStrandWithoutComplement() throws Exception {
        stubSlice("CAA");
        GFF3Feature feature = tRna("t1", 1, 100, MINUS, "(pos:10..12,aa:Gln)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Gln,seq:ttg)"), anticodon(feature));
    }

    @Test
    void correctsWrongSequence() throws Exception {
        stubSlice("TTA");
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu,seq:xxx)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu,seq:tta)"), anticodon(feature));
    }

    @Test
    void lowercasesDerivedSequence() throws Exception {
        stubSlice("TTA");
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu,seq:TTA)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu,seq:tta)"), anticodon(feature));
    }

    @Test
    void leavesCorrectSequenceAlone() throws Exception {
        stubSlice("TTA");
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu,seq:tta)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu,seq:tta)"), anticodon(feature));
    }

    @Test
    void skipsPositionNotSpanningThreeBases() throws Exception {
        stubSlice("TTA");
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..60,aa:Lys)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..60,aa:Lys)"), anticodon(feature));
    }

    @Test
    void propagatesSequenceLookupFailure() throws Exception {
        when(lookup.getSequenceSlice(anyString(), anyLong(), anyLong(), any()))
                .thenThrow(new IllegalArgumentException("bad base range: 10..12"));
        tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu)");

        assertThrows(IllegalArgumentException.class, () -> fix.addSequence(annotation, 1));
    }

    @Test
    void looksUpTheAnnotationSequenceRegionAccession() throws Exception {
        stubSlice("TTA");
        GFF3Feature feature = new GFF3Feature(
                Optional.of("t1"),
                Optional.empty(),
                SEQ_ID,
                Optional.of(2),
                ".",
                OntologyTerm.TRNA.name(),
                1,
                100,
                ".",
                PLUS,
                "");
        feature.addAttributes(ANTI_CODON, List.of("(pos:10..12,aa:Glu)"));
        annotation.addFeature(feature);
        annotation.setSequenceRegion(new GFF3SequenceRegion(SEQ_ID, Optional.of(2), 1, 5000));

        fix.addSequence(annotation, 1);

        verify(lookup).hasSequence(SEQ_ID + ".2");
        verify(lookup).getSequenceSlice(SEQ_ID + ".2", 10L, 12L, SequenceRangeOption.WHOLE_SEQUENCE);
    }

    @Test
    void looksUpFeatureAccessionWhenAnnotationHasNoSequenceRegion() throws Exception {
        stubSlice("TTA");
        tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu)");

        fix.addSequence(annotation, 1);

        verify(lookup).hasSequence(SEQ_ID);
        verify(lookup).getSequenceSlice(SEQ_ID, 10L, 12L, SequenceRangeOption.WHOLE_SEQUENCE);
    }

    @Test
    void checksTheSequenceOnceForAllFeaturesInTheAnnotation() throws Exception {
        stubSlice("TTA");
        tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu)");
        tRna("t2", 200, 300, PLUS, "(pos:210..212,aa:Lys)");
        tRna("t3", 400, 500, PLUS, "(pos:410..412,aa:Phe)");

        fix.addSequence(annotation, 1);

        verify(lookup, times(1)).hasSequence(SEQ_ID);
    }

    @Test
    void skipsAnnotationWithNoAnticodonFeatures() {
        assertDoesNotThrow(() -> fix.addSequence(new GFF3Annotation(), 1));
        assertDoesNotThrow(() -> fix.addSequence(annotation, 1));
    }

    @Test
    void skipsWhenNoSourceHasTheSequence() throws Exception {
        when(lookup.hasSequence(anyString())).thenReturn(false);
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu)");

        fix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu)"), anticodon(feature));
    }

    @Test
    void skipsWhenNoProviderRegistered() throws Exception {
        AnticodonAttributeFix noSequenceFix = new AnticodonAttributeFix();
        ValidationRegistry.injectContext(noSequenceFix, new ValidationContext());
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu)");

        noSequenceFix.addSequence(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Glu)"), anticodon(feature));
    }

    @Test
    void skipsWhenProviderRegisteredButHasNoSources() {
        AnticodonAttributeFix emptySourceFix = new AnticodonAttributeFix();
        ValidationContext context = new ValidationContext();
        context.register(SequenceLookup.class, new CompositeSequenceProvider());
        ValidationRegistry.injectContext(emptySourceFix, context);
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:Glu)");

        assertDoesNotThrow(() -> emptySourceFix.addSequence(annotation, 1));

        assertEquals(List.of("(pos:10..12,aa:Glu)"), anticodon(feature));
    }

    @Test
    void isIdempotentAcrossBothFixes() throws Exception {
        stubSlice("CAA");
        GFF3Feature feature = tRna("t1", 1, 100, MINUS, "(pos:10..12,aa:glN)");

        fix.fixAminoAcidCase(annotation, 1);
        fix.addSequence(annotation, 1);
        List<String> afterFirstPass = List.copyOf(anticodon(feature));

        fix.fixAminoAcidCase(annotation, 1);
        fix.addSequence(annotation, 1);

        assertEquals(afterFirstPass, anticodon(feature));
        assertEquals(List.of("(pos:10..12,aa:Gln,seq:ttg)"), anticodon(feature));
    }

    @Test
    void bothFixesGiveSameResultInEitherOrder() throws Exception {
        stubSlice("TTA");
        GFF3Feature feature = tRna("t1", 1, 100, PLUS, "(pos:10..12,aa:sEc)");

        fix.addSequence(annotation, 1);
        fix.fixAminoAcidCase(annotation, 1);

        assertEquals(List.of("(pos:10..12,aa:Sec,seq:tta)"), anticodon(feature));
    }

    // --- helpers --------------------------------------------------------

    private ValidationContext contextWith(SequenceLookup sequenceLookup) {
        ValidationContext context = new ValidationContext();
        context.register(SequenceLookup.class, new ContextProvider<SequenceLookup>() {
            @Override
            public SequenceLookup get(ValidationContext ctx) {
                return sequenceLookup;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
        return context;
    }

    private void stubSlice(String bases) throws Exception {
        when(lookup.getSequenceSlice(anyString(), anyLong(), anyLong(), any(SequenceRangeOption.class)))
                .thenReturn(bases);
    }

    private GFF3Feature tRna(String id, long start, long end, String strand, String... anticodons) {
        GFF3Feature feature = new GFF3Feature(
                Optional.ofNullable(id),
                Optional.empty(),
                SEQ_ID,
                Optional.empty(),
                ".",
                OntologyTerm.TRNA.name(),
                start,
                end,
                ".",
                strand,
                "");
        if (anticodons.length > 0) {
            feature.addAttributes(ANTI_CODON, List.of(anticodons));
        }
        annotation.addFeature(feature);
        return feature;
    }

    private List<String> anticodon(GFF3Feature feature) {
        return feature.getAttributeList(ANTI_CODON).orElse(List.of());
    }
}
