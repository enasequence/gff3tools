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

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;

public class AccessionReplacementFixTest {

    private AccessionReplacementFix fix;

    @BeforeEach
    public void setUp() {
        fix = new AccessionReplacementFix();
    }

    @AfterEach
    public void tearDown() {
        AccessionReplacementFix.clearAccessionMap();
    }

    // -- Feature-level tests --

    @Test
    public void testFeatureReplacementWithVersion() {
        AccessionReplacementFix.setAccessionMap(Map.of("seq1.1", "CB0001.1"));

        GFF3Feature feature = createFeature("seq1", Optional.of(1));
        fix.replaceFeatureAccession(feature, 1);

        assertEquals("CB0001", feature.getSeqId());
        assertEquals(Optional.of(1), feature.getSeqIdVersion());
    }

    @Test
    public void testFeatureReplacementWithoutVersion() {
        AccessionReplacementFix.setAccessionMap(Map.of("seq1", "CB0001"));

        GFF3Feature feature = createFeature("seq1", Optional.empty());
        fix.replaceFeatureAccession(feature, 1);

        assertEquals("CB0001", feature.getSeqId());
        assertEquals(Optional.empty(), feature.getSeqIdVersion());
    }

    @Test
    public void testFeatureReplacementAddsVersion() {
        AccessionReplacementFix.setAccessionMap(Map.of("seq1", "CB0001.2"));

        GFF3Feature feature = createFeature("seq1", Optional.empty());
        fix.replaceFeatureAccession(feature, 1);

        assertEquals("CB0001", feature.getSeqId());
        assertEquals(Optional.of(2), feature.getSeqIdVersion());
    }

    @Test
    public void testFeatureNoOpWhenNoMap() {
        // accessionMap is null (not set)
        GFF3Feature feature = createFeature("seq1", Optional.of(1));
        fix.replaceFeatureAccession(feature, 1);

        assertEquals("seq1", feature.getSeqId());
        assertEquals(Optional.of(1), feature.getSeqIdVersion());
    }

    @Test
    public void testFeatureNoOpWhenAccessionNotInMap() {
        AccessionReplacementFix.setAccessionMap(Map.of("other.1", "CB0001.1"));

        GFF3Feature feature = createFeature("seq1", Optional.of(1));
        fix.replaceFeatureAccession(feature, 1);

        assertEquals("seq1", feature.getSeqId());
        assertEquals(Optional.of(1), feature.getSeqIdVersion());
    }

    @Test
    public void testFeatureAttributesPreservedAfterReplacement() {
        AccessionReplacementFix.setAccessionMap(Map.of("seq1.1", "CB0001.1"));

        GFF3Feature feature = createFeature("seq1", Optional.of(1));
        feature.addAttribute("locus_tag", "GENE001");
        feature.addAttribute("product", "hypothetical protein");

        fix.replaceFeatureAccession(feature, 1);

        assertEquals("CB0001", feature.getSeqId());
        assertEquals(Optional.of("GENE001"), feature.getAttribute("locus_tag"));
        assertEquals(Optional.of("hypothetical protein"), feature.getAttribute("product"));
    }

    // -- Annotation-level tests --

    @Test
    public void testAnnotationSequenceRegionReplacement() {
        AccessionReplacementFix.setAccessionMap(Map.of("seq1.1", "CB0001.1"));

        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion("seq1", Optional.of(1), 1, 1000));

        fix.replaceSequenceRegion(annotation, 1);

        GFF3SequenceRegion sr = annotation.getSequenceRegion();
        assertEquals("CB0001", sr.accessionId());
        assertEquals(Optional.of(1), sr.accessionVersion());
        assertEquals(1, sr.start());
        assertEquals(1000, sr.end());
    }

    @Test
    public void testAnnotationNoOpWhenNoMap() {
        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion("seq1", Optional.of(1), 1, 1000));

        fix.replaceSequenceRegion(annotation, 1);

        assertEquals("seq1", annotation.getSequenceRegion().accessionId());
    }

    @Test
    public void testAnnotationNoOpWhenNoSequenceRegion() {
        AccessionReplacementFix.setAccessionMap(Map.of("seq1.1", "CB0001.1"));

        GFF3Annotation annotation = new GFF3Annotation();
        // No sequence region set
        fix.replaceSequenceRegion(annotation, 1);

        assertNull(annotation.getSequenceRegion());
    }

    @Test
    public void testAnnotationNoOpWhenAccessionNotInMap() {
        AccessionReplacementFix.setAccessionMap(Map.of("other.1", "CB0001.1"));

        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion("seq1", Optional.of(1), 1, 1000));

        fix.replaceSequenceRegion(annotation, 1);

        assertEquals("seq1", annotation.getSequenceRegion().accessionId());
    }

    @Test
    public void testAnnotationStartEndPreserved() {
        AccessionReplacementFix.setAccessionMap(Map.of("seq1.1", "CB0001.1"));

        GFF3Annotation annotation = new GFF3Annotation();
        annotation.setSequenceRegion(new GFF3SequenceRegion("seq1", Optional.of(1), 42, 9999));

        fix.replaceSequenceRegion(annotation, 1);

        GFF3SequenceRegion sr = annotation.getSequenceRegion();
        assertEquals(42, sr.start());
        assertEquals(9999, sr.end());
    }

    // -- Accession parsing tests --

    @Test
    public void testParseAccessionWithVersion() {
        String[] result = AccessionReplacementFix.parseAccession("CB0001.1");
        assertEquals("CB0001", result[0]);
        assertEquals("1", result[1]);
    }

    @Test
    public void testParseAccessionWithoutVersion() {
        String[] result = AccessionReplacementFix.parseAccession("CB0001");
        assertEquals("CB0001", result[0]);
        assertNull(result[1]);
    }

    @Test
    public void testParseAccessionWithHighVersion() {
        String[] result = AccessionReplacementFix.parseAccession("CB0001.42");
        assertEquals("CB0001", result[0]);
        assertEquals("42", result[1]);
    }

    @Test
    public void testParseAccessionWithDotInId() {
        // e.g., "CACUVO010000001.1" - last dot separates version
        String[] result = AccessionReplacementFix.parseAccession("CACUVO010000001.1");
        assertEquals("CACUVO010000001", result[0]);
        assertEquals("1", result[1]);
    }

    // -- Helper methods --

    private GFF3Feature createFeature(String seqId, Optional<Integer> version) {
        return new GFF3Feature(
                Optional.of("feat1"), Optional.empty(), seqId, version, "ENA", "gene", 1, 100, ".", "+", ".");
    }
}
