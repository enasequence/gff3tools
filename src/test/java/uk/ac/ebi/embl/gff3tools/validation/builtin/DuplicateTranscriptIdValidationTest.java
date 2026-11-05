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

class DuplicateTranscriptIdValidationTest {

    private static final String MRNA_FEATURE = "mRNA";
    private static final String CDS_FEATURE = OntologyTerm.CDS.name();
    private static final String ACCESSION = "ACC123";
    private static final String OTHER_ACCESSION = "ACC999";

    private DuplicateTranscriptIdValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new DuplicateTranscriptIdValidation();
        TestUtils.injectContext(validation);
        annotation = new GFF3Annotation();
    }

    @Test
    void mrnaAndCdsSharingATranscriptIdAreValid() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", 150, 700, "T1");

        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));
    }

    @Test
    void featureWrittenOverSeveralLinesIsCountedOnce() {
        feature(MRNA_FEATURE, "mrna1", 100, 300, "T1");
        feature(MRNA_FEATURE, "mrna1", 500, 800, "T1");
        feature(CDS_FEATURE, "cds1", 150, 300, "T1");
        feature(CDS_FEATURE, "cds1", 500, 700, "T1");

        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));
    }

    @Test
    void isoformsWithTheirOwnTranscriptIdsAreValid() {
        feature(MRNA_FEATURE, "mrnaA", 100, 800, "T1");
        feature(CDS_FEATURE, "cdsA", 150, 700, "T1");
        feature(MRNA_FEATURE, "mrnaB", 100, 900, "T2");
        feature(CDS_FEATURE, "cdsB", 150, 850, "T2");

        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));
    }

    @Test
    void featuresWithoutTranscriptIdAreIgnored() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, null);
        feature(MRNA_FEATURE, "mrna2", 100, 900, null);
        feature(CDS_FEATURE, "cds1", 150, 700, null);

        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));
    }

    @Test
    void otherFeatureTypesCarryingTheTranscriptIdAreIgnored() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1");
        feature("exon", "exon1", 100, 300, "T1");
        feature("exon", "exon2", 500, 800, "T1");
        feature(CDS_FEATURE, "cds1", 150, 700, "T1");

        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));
    }

    @Test
    void twoMrnasSharingATranscriptIdAreReported() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1");
        feature(MRNA_FEATURE, "mrna2", 100, 900, "T1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 1));
        assertTrue(exception.getMessage().contains("transcript_id \"T1\" is used by mRNA \"mrna1\""));
        assertTrue(exception.getMessage().contains("and by mRNA \"mrna2\" at ACC123:100..900"));
    }

    @Test
    void twoCdsSharingATranscriptIdAreReported() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", 150, 700, "T1");
        feature(CDS_FEATURE, "cds2", 150, 750, "T1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 1));
        assertTrue(exception.getMessage().contains("is used by CDS \"cds1\""));
        assertTrue(exception.getMessage().contains("and by CDS \"cds2\""));
    }

    @Test
    void mrnaTypedBySoAccessionIsCounted() {
        feature(OntologyTerm.MRNA.ID, "mrna1", 100, 800, "T1");
        feature(MRNA_FEATURE, "mrna2", 100, 900, "T1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 1));
        assertTrue(exception.getMessage().contains("and by mRNA \"mrna2\""));
    }

    @Test
    void everyDuplicateInTheAnnotationIsReportedTogether() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1");
        feature(MRNA_FEATURE, "mrna2", 100, 900, "T1");
        feature(CDS_FEATURE, "cds1", 150, 700, "T2");
        feature(CDS_FEATURE, "cds2", 150, 750, "T2");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 1));
        assertTrue(exception.getMessage().contains("transcript_id \"T1\""));
        assertTrue(exception.getMessage().contains("transcript_id \"T2\""));
    }

    @Test
    void lineOfEachFeatureIsReportedWhereKnown() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1").setLine(4);
        feature(MRNA_FEATURE, "mrna2", 100, 900, "T1").setLine(9);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 1));
        assertTrue(exception.getMessage().contains("\"mrna1\" at ACC123:100..800 (line 4)"));
        assertTrue(exception.getMessage().contains("\"mrna2\" at ACC123:100..900 (line 9)"));
    }

    @Test
    void duplicateOnAnotherSequenceIsReported() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1");
        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));

        annotation = new GFF3Annotation();
        featureOnAccession(MRNA_FEATURE, "mrna2", OTHER_ACCESSION, 100, 800, "T1");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 2));
        assertTrue(exception.getMessage().contains("and by mRNA \"mrna2\" at ACC999:100..800"));
    }

    @Test
    void featureIdReusedOnAnotherSequenceIsADifferentFeature() {
        feature(CDS_FEATURE, "cds1", 150, 700, "T1");
        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));

        annotation = new GFF3Annotation();
        featureOnAccession(CDS_FEATURE, "cds1", OTHER_ACCESSION, 150, 700, "T1");

        assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 2));
    }

    @Test
    void duplicateInALaterAnnotationOfTheSameSequenceIsReported() {
        feature(CDS_FEATURE, "cds1", 150, 700, "T1");
        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));

        annotation = new GFF3Annotation();
        feature(CDS_FEATURE, "cds2", 950, 1200, "T1");

        assertThrows(ValidationException.class, () -> validation.validateTranscriptIdUniqueness(annotation, 2));
    }

    @Test
    void featureSplitAcrossAnnotationsIsNotADuplicateOfItself() {
        feature(MRNA_FEATURE, "mrna1", 100, 300, "T1");
        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));

        annotation = new GFF3Annotation();
        feature(MRNA_FEATURE, "mrna1", 500, 800, "T1");

        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 2));
    }

    @Test
    void transcriptIdsAreForgottenOnceTheFileIsFinished() {
        feature(MRNA_FEATURE, "mrna1", 100, 800, "T1");
        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));

        validation.clear();
        annotation = new GFF3Annotation();
        feature(MRNA_FEATURE, "mrna2", 100, 900, "T1");

        assertDoesNotThrow(() -> validation.validateTranscriptIdUniqueness(annotation, 1));
    }

    private GFF3Feature feature(String featureName, String id, long start, long end, String transcriptId) {
        return featureOnAccession(featureName, id, ACCESSION, start, end, transcriptId);
    }

    private GFF3Feature featureOnAccession(
            String featureName, String id, String seqId, long start, long end, String transcriptId) {
        GFF3Feature feature = new GFF3Feature(
                Optional.of(id), Optional.empty(), seqId, Optional.empty(), ".", featureName, start, end, ".", "+", "");
        if (transcriptId != null) {
            feature.addAttribute(GFF3Attributes.TRANSCRIPT_ID, transcriptId);
        }
        annotation.addFeature(feature);
        return feature;
    }
}
