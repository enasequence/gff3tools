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
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.ANTI_CODON;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.TRANSL_EXCEPT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

/**
 * The fix drops a {@code complement(...)} wrapper from {@code transl_except} attribute values, so
 * {@code (pos:complement(4370..4372),aa:Sec)} becomes {@code (pos:4370..4372,aa:Sec)} — but only
 * when the row's strand column confirms that is safe. These tests cover both halves: what gets
 * rewritten, and the much larger set of cases that must be left exactly as they are.
 */
public class TranslExceptComplementFixTest {

    private static final String MINUS = "-";
    private static final String PLUS = "+";

    private TranslExceptComplementFix fix;
    private GFF3Annotation annotation;

    @BeforeEach
    public void setUp() {
        fix = new TranslExceptComplementFix();
        annotation = new GFF3Annotation();
    }

    // ---------------------------------------------------------------------
    // Values that should be rewritten
    // ---------------------------------------------------------------------

    @Test
    public void testStripsComplementOnMinusStrandFeature() {
        GFF3Feature cds = cds("c1", 4000, 5000, MINUS, "(pos:complement(4370..4372),aa:Sec)");
        annotation.addFeature(cds);

        fix.fixAnnotation(annotation, 1);

        assertEquals(List.of("(pos:4370..4372,aa:Sec)"), translExcept(cds));
    }

    @Test
    public void testStripsSinglePositionComplement() {
        GFF3Feature cds = cds("c1", 1, 800, MINUS, "(pos:complement(213),aa:Met)");
        annotation.addFeature(cds);

        fix.fixAnnotation(annotation, 1);

        assertEquals(List.of("(pos:213,aa:Met)"), translExcept(cds));
    }

    @Test
    public void testPreservesSpacingAndCaseOutsideTheWrapper() {
        GFF3Feature cds = cds("c1", 4000, 5000, MINUS, "( pos : COMPLEMENT( 4370 .. 4372 ) , aa : Sec )");
        annotation.addFeature(cds);

        fix.fixAnnotation(annotation, 1);

        // Only the wrapper disappears; spacing, casing and the aa: token survive untouched.
        assertEquals(List.of("( pos : 4370 .. 4372 , aa : Sec )"), translExcept(cds));
    }

    @Test
    public void testRewritesOnlyTheWrappedValueAndPreservesOrder() {
        GFF3Feature cds = cds(
                "c1",
                4000,
                5000,
                MINUS,
                "(pos:4100..4102,aa:Trp)",
                "(pos:complement(4370..4372),aa:Sec)",
                "(pos:4500..4502,aa:Trp)");
        annotation.addFeature(cds);

        fix.fixAnnotation(annotation, 1);

        assertEquals(
                List.of("(pos:4100..4102,aa:Trp)", "(pos:4370..4372,aa:Sec)", "(pos:4500..4502,aa:Trp)"),
                translExcept(cds));
    }

    @Test
    public void testIsIdempotent() {
        GFF3Feature cds = cds("c1", 4000, 5000, MINUS, "(pos:complement(4370..4372),aa:Sec)");
        annotation.addFeature(cds);

        fix.fixAnnotation(annotation, 1);
        List<String> afterFirstPass = translExcept(cds);
        fix.fixAnnotation(annotation, 1);

        assertEquals(afterFirstPass, translExcept(cds));
        assertEquals(List.of("(pos:4370..4372,aa:Sec)"), translExcept(cds));
    }

    // ---------------------------------------------------------------------
    // Values that must be left exactly as they are
    // ---------------------------------------------------------------------

    @Test
    public void testLeavesComplementOnPlusStrandFeature() {
        String value = "(pos:complement(4370..4372),aa:Sec)";
        GFF3Feature cds = cds("c1", 4000, 5000, PLUS, value);
        annotation.addFeature(cds);

        fix.fixAnnotation(annotation, 1);

        // The wrapper says "backwards" but the strand column says "forwards", so one of them is
        // wrong. Rewriting would hide that, so the value stays put and TRANSL_EXCEPT_STRAND_CONFLICT
        // reports it instead.
        assertEquals(List.of(value), translExcept(cds));
    }

    @Test
    public void testLeavesComplementOnUnknownStrand() {
        String value = "(pos:complement(4370..4372),aa:Sec)";
        GFF3Feature unscored = cds("c1", 4000, 5000, ".", value);
        GFF3Feature unknown = cds("c2", 4000, 5000, "?", value);
        annotation.addFeature(unscored);
        annotation.addFeature(unknown);

        fix.fixAnnotation(annotation, 1);

        assertEquals(List.of(value), translExcept(unscored));
        assertEquals(List.of(value), translExcept(unknown));
    }

    @Test
    public void testLeavesValueWhenCodonFallsOutsideEveryFragment() {
        String value = "(pos:complement(9000..9002),aa:Sec)";
        GFF3Feature cds = cds("c1", 4000, 5000, MINUS, value);
        annotation.addFeature(cds);

        assertDoesNotThrow(() -> fix.fixAnnotation(annotation, 1));
        assertEquals(List.of(value), translExcept(cds));
    }

    @Test
    public void testLeavesCompoundFuzzyAndRemoteLocations() {
        String compound = "(pos:complement(join(4370..4372,4380..4382)),aa:Sec)";
        String fuzzy = "(pos:complement(<4370..4372),aa:Sec)";
        String remote = "(pos:complement(X12345.1:4370..4372),aa:Sec)";

        GFF3Feature a = cds("c1", 4000, 5000, MINUS, compound);
        GFF3Feature b = cds("c2", 4000, 5000, MINUS, fuzzy);
        GFF3Feature c = cds("c3", 4000, 5000, MINUS, remote);
        annotation.addFeature(a);
        annotation.addFeature(b);
        annotation.addFeature(c);

        assertDoesNotThrow(() -> fix.fixAnnotation(annotation, 1));

        // Only a simple range is ever rewritten; anything else is left for validation to judge.
        assertEquals(List.of(compound), translExcept(a));
        assertEquals(List.of(fuzzy), translExcept(b));
        assertEquals(List.of(remote), translExcept(c));
    }

    @Test
    public void testMalformedAndOutOfRangeValuesDoNotThrow() {
        String malformed = "(pos:complement(abc),aa:Sec)";
        String overflow = "(pos:complement(99999999999999999999..99999999999999999999),aa:Sec)";

        GFF3Feature a = cds("c1", 4000, 5000, MINUS, malformed);
        GFF3Feature b = cds("c2", 4000, 5000, MINUS, overflow);
        annotation.addFeature(a);
        annotation.addFeature(b);

        // Values the fix cannot parse are skipped, so one malformed row can never abort the run.
        assertDoesNotThrow(() -> fix.fixAnnotation(annotation, 1));

        assertEquals(List.of(malformed), translExcept(a));
        assertEquals(List.of(overflow), translExcept(b));
    }

    // ---------------------------------------------------------------------
    // One feature spread over several rows (a join)
    // ---------------------------------------------------------------------

    @Test
    public void testUniformMinusStrandJoinRewritesEveryRowIdentically() {
        // Rows sharing an ID are one feature, and each row carries its own copy of the attribute.
        String value = "(pos:complement(4370..4372),aa:Sec)";
        GFF3Feature first = cds("c1", 4000, 5000, MINUS, value);
        GFF3Feature second = cds("c1", 6000, 7000, MINUS, value);
        annotation.addFeature(first);
        annotation.addFeature(second);

        fix.fixAnnotation(annotation, 1);

        assertEquals(List.of("(pos:4370..4372,aa:Sec)"), translExcept(first));
        assertEquals(translExcept(first), translExcept(second));
    }

    @Test
    public void testMixedStrandJoinStripsWhenCodonSitsInMinusSegment() {
        String value = "(pos:complement(4370..4372),aa:Sec)";
        GFF3Feature minusSegment = cds("c1", 4000, 5000, MINUS, value);
        GFF3Feature plusSegment = cds("c1", 6000, 7000, PLUS, value);
        annotation.addFeature(minusSegment);
        annotation.addFeature(plusSegment);

        fix.fixAnnotation(annotation, 1);

        // Only the row whose start/end span the position decides, so the other row's strand is
        // irrelevant here.
        assertEquals(List.of("(pos:4370..4372,aa:Sec)"), translExcept(minusSegment));
        assertEquals(translExcept(minusSegment), translExcept(plusSegment));
    }

    @Test
    public void testMixedStrandJoinLeavesValueWhenCodonSitsInPlusSegment() {
        String value = "(pos:complement(6100..6102),aa:Sec)";
        GFF3Feature minusSegment = cds("c1", 4000, 5000, MINUS, value);
        GFF3Feature plusSegment = cds("c1", 6000, 7000, PLUS, value);
        annotation.addFeature(minusSegment);
        annotation.addFeature(plusSegment);

        fix.fixAnnotation(annotation, 1);

        assertEquals(List.of(value), translExcept(minusSegment));
        assertEquals(List.of(value), translExcept(plusSegment));
    }

    // ---------------------------------------------------------------------
    // Guard: the lookalike anticodon attribute is never touched
    // ---------------------------------------------------------------------

    @Test
    public void testNeverTouchesAntiCodon() {
        // The anticodon attribute looks almost identical but must never be rewritten: it has an
        // extra seq: part that other EBI tooling recomputes from the wrapper, so dropping the
        // wrapper would change what the value says.
        String value = "(pos:complement(4229..4231),aa:Lys,seq:ttt)";
        GFF3Feature tRna = new GFF3Feature(
                Optional.of("t1"),
                Optional.empty(),
                TestUtils.DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                OntologyTerm.TRNA.name(),
                4200,
                4300,
                ".",
                MINUS,
                ".");
        tRna.setAttributeList(ANTI_CODON, new ArrayList<>(List.of(value)));
        annotation.addFeature(tRna);

        fix.fixAnnotation(annotation, 1);

        assertEquals(List.of(value), tRna.getAttributeList(ANTI_CODON).orElseThrow());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private GFF3Feature cds(String id, long start, long end, String strand, String... translExceptValues) {
        GFF3Feature feature = new GFF3Feature(
                Optional.of(id),
                Optional.empty(),
                TestUtils.DEFAULT_ACCESSION,
                Optional.empty(),
                ".",
                OntologyTerm.CDS.name(),
                start,
                end,
                ".",
                strand,
                "0");
        feature.setAttributeList(TRANSL_EXCEPT, new ArrayList<>(List.of(translExceptValues)));
        return feature;
    }

    private List<String> translExcept(GFF3Feature feature) {
        return feature.getAttributeList(TRANSL_EXCEPT).orElseThrow();
    }
}
