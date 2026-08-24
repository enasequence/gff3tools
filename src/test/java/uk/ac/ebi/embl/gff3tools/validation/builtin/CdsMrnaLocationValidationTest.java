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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

class CdsMrnaLocationValidationTest {

    private static final String MRNA_FEATURE = "mRNA";
    private static final String CDS_FEATURE = OntologyTerm.CDS.name();
    private static final String ACCESSION = "ACC123";
    private static final String OTHER_ACCESSION = "ACC999";
    private static final String PLUS = "+";
    private static final String MINUS = "-";

    private CdsMrnaLocationValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new CdsMrnaLocationValidation();
        TestUtils.injectContext(validation);
        annotation = new GFF3Annotation();
    }

    @Test
    void splicedCdsMatchingJoinedMrnaIsValid() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cds("cds1", 150, 300, "mrna1");
        cds("cds1", 500, 700, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void singleSegmentCdsInsideSingleIntervalMrnaIsValid() {
        mrna("mrna1", 100, 800);
        cds("cds1", 150, 700, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void splicedCdsInsideSingleIntervalMrnaIsValid() {
        mrna("mrna1", 100, 900);
        cds("cds1", 150, 300, "mrna1");
        cds("cds1", 500, 800, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void minusStrandSplicedCdsMatchingJoinedMrnaIsValid() {
        mrnaOnStrand("mrna1", 100, 300, MINUS);
        mrnaOnStrand("mrna1", 500, 800, MINUS);
        cdsOnStrand("cds1", 150, 300, MINUS, "mrna1");
        cdsOnStrand("cds1", 500, 700, MINUS, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void alternativeSplicingIsolatesEachIsoform() {
        mrna("mrnaA", 100, 300);
        mrna("mrnaA", 500, 800);
        cds("cdsA", 150, 300, "mrnaA");
        cds("cdsA", 500, 700, "mrnaA");
        mrna("mrnaB", 100, 800);
        cds("cdsB", 150, 700, "mrnaB");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void cdsPairedByTranscriptIdMatchingMrnaIsValid() {
        mrna("mrna1", 100, 300).addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");
        mrna("mrna1", 500, 800).addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");
        cds("cds1", 150, 300, "gene1").addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");
        cds("cds1", 500, 700, "gene1").addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void multiValuedParentNamingGeneBeforeMrnaIsPaired() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cds("cds1", 150, 280, "gene1", "mrna1");
        cds("cds1", 500, 700, "gene1", "mrna1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("does not end where the mRNA segment 100..300 ends"));
    }

    @Test
    void cdsWithoutAnyPairingIsSkipped() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cds("cds1", 150, 900, "gene1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void annotationWithoutMrnaIsSkipped() {
        cds("cds1", 150, 300, "gene1");
        cds("cds1", 900, 950, "gene1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void pairingAcrossDifferentAccessionsIsSkipped() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cdsOnAccession("cds1", OTHER_ACCESSION, 150, 900, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void transSplicedCdsIsExempt() {
        mrna("mrna1", 100, 800);
        cds("cds1", 150, 900, "mrna1").addAttribute(GFF3Attributes.TRANS_SPLICING, "true");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void ribosomalSlippageCdsIsExempt() {
        mrna("mrna1", 100, 800);
        cds("cds1", 150, 900, "mrna1").addAttribute(GFF3Attributes.RIBOSOMAL_SLIPPAGE, "true");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void exemptionOnTheMrnaSideIsHonoured() {
        mrna("mrna1", 100, 800).addAttribute(GFF3Attributes.ARTIFICIAL_LOCATION, "true");
        cds("cds1", 150, 900, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void internalBoundaryMismatchIsReported() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cds("cds1", 150, 280, "mrna1");
        cds("cds1", 500, 700, "mrna1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("does not end where the mRNA segment 100..300 ends"));
    }

    @Test
    void internalStartBoundaryMismatchIsReported() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cds("cds1", 150, 300, "mrna1");
        cds("cds1", 520, 700, "mrna1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("does not start where the mRNA segment 500..800 starts"));
    }

    @Test
    void cdsExtendingBeyondSingleIntervalMrnaIsReported() {
        mrna("mrna1", 100, 800);
        cds("cds1", 150, 900, "mrna1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("is not contained within the mRNA 100..800"));
    }

    @Test
    void cdsSegmentOutsideCorrespondingMrnaSegmentIsReported() {
        mrna("mrna1", 100, 300).addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");
        mrna("mrna1", 500, 800).addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");
        cds("cds1", 150, 300, "gene1").addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");
        cds("cds1", 450, 700, "gene1").addAttribute(GFF3Attributes.TRANSCRIPT_ID, "T1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("is not contained within the mRNA segment 500..800"));
    }

    @Test
    void unsplicedCdsSpanningAnIntronOfASplicedMrnaIsReported() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cds("cds1", 150, 750, "mrna1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("is not contained within any segment of the mRNA"));
    }

    @Test
    void cdsWithMoreSegmentsThanTheMrnaIsReported() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        cds("cds1", 150, 300, "mrna1");
        cds("cds1", 500, 600, "mrna1");
        cds("cds1", 700, 800, "mrna1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("more than the 2 the mRNA has there"));
    }

    @Test
    void cdsDescendantTermIsNotTreatedAsCds() {
        mrna("mrna1", 100, 300);
        mrna("mrna1", 500, 800);
        typed("edited_CDS", "cds1", 150, 280, "mrna1");
        typed("edited_CDS", "cds1", 500, 700, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void mrnaDescendantTermIsNotTreatedAsMrna() {
        typed("polyadenylated_mRNA", "mrna1", 100, 300);
        typed("polyadenylated_mRNA", "mrna1", 500, 800);
        cds("cds1", 150, 280, "mrna1");
        cds("cds1", 500, 700, "mrna1");

        assertDoesNotThrow(() -> validation.validateCdsMrnaLocation(annotation, 1));
    }

    @Test
    void everyIncompatibleCdsIsReportedTogether() {
        mrna("mrnaA", 100, 800);
        cds("cdsA", 150, 900, "mrnaA");
        mrna("mrnaB", 1000, 1800);
        cds("cdsB", 1050, 1900, "mrnaB");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateCdsMrnaLocation(annotation, 1));
        assertTrue(exception.getMessage().contains("150..900"));
        assertTrue(exception.getMessage().contains("1050..1900"));
    }

    private GFF3Feature mrna(String id, long start, long end) {
        return mrnaOnStrand(id, start, end, PLUS);
    }

    private GFF3Feature mrnaOnStrand(String id, long start, long end, String strand) {
        return add(build(MRNA_FEATURE, id, ACCESSION, start, end, strand));
    }

    private GFF3Feature cds(String id, long start, long end, String... parents) {
        return cdsOnStrand(id, start, end, PLUS, parents);
    }

    private GFF3Feature cdsOnStrand(String id, long start, long end, String strand, String... parents) {
        return add(withParents(build(CDS_FEATURE, id, ACCESSION, start, end, strand), parents));
    }

    private GFF3Feature typed(String featureName, String id, long start, long end, String... parents) {
        return add(withParents(build(featureName, id, ACCESSION, start, end, PLUS), parents));
    }

    private GFF3Feature cdsOnAccession(String id, String seqId, long start, long end, String... parents) {
        return add(withParents(build(CDS_FEATURE, id, seqId, start, end, PLUS), parents));
    }

    private GFF3Feature add(GFF3Feature feature) {
        annotation.addFeature(feature);
        return feature;
    }

    private static GFF3Feature withParents(GFF3Feature feature, String... parents) {
        for (String parent : parents) {
            feature.addAttribute(GFF3Attributes.ATTRIBUTE_PARENT, parent);
        }
        return feature;
    }

    private static GFF3Feature build(String name, String id, String seqId, long start, long end, String strand) {
        return new GFF3Feature(
                Optional.of(id), Optional.empty(), seqId, Optional.empty(), ".", name, start, end, ".", strand, "");
    }
}
