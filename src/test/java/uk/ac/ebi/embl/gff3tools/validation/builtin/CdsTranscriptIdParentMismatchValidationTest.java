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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

class CdsTranscriptIdParentMismatchValidationTest {

    private static final String GENE_FEATURE = "gene";
    private static final String MRNA_FEATURE = "mRNA";
    private static final String CDS_FEATURE = OntologyTerm.CDS.name();
    private static final String ACCESSION = "ACC123";

    private CdsTranscriptIdParentMismatchValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new CdsTranscriptIdParentMismatchValidation();
        TestUtils.injectContext(validation);
        annotation = new GFF3Annotation();
    }

    @Test
    void cdsWithTheSameTranscriptIdAsItsParentMrnaIsValid() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 700, "T1");

        assertDoesNotThrow(() -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
    }

    @Test
    void cdsWithoutTranscriptIdIsNotCompared() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 700, null);

        assertDoesNotThrow(() -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
    }

    @Test
    void parentMrnaWithoutTranscriptIdIsNotCompared() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 800, null);
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 700, "T1");

        assertDoesNotThrow(() -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
    }

    @Test
    void cdsWhoseParentIsTheGeneIsNotCompared() {
        feature(GENE_FEATURE, "gene1", null, 1, 1000, "T2");
        feature(MRNA_FEATURE, "mrna1", "gene1", 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "gene1", 150, 700, "T3");

        assertDoesNotThrow(() -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
    }

    @Test
    void cdsWhoseParentIsMissingIsLeftToDanglingParent() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna_missing", 150, 700, "T2");

        assertDoesNotThrow(() -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
    }

    @Test
    void cdsWithADifferentTranscriptIdFromItsParentMrnaIsReported() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 700, "T2");

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
        assertTrue(exception
                .getMessage()
                .contains("CDS \"cds1\" at ACC123:150..700 has transcript_id \"T2\", but its parent mRNA \"mrna1\" "
                        + "has \"T1\""));
    }

    @Test
    void featuresWrittenOverSeveralLinesAreReportedOnce() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 300, "T1");
        feature(MRNA_FEATURE, "mrna1", null, 500, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 300, "T2");
        feature(CDS_FEATURE, "cds1", "mrna1", 500, 700, "T2");

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
        assertTrue(exception.getMessage().contains("CDS \"cds1\" at ACC123:150..700"));
        assertEquals(1, exception.getMessage().split("CDS \"cds1\"", -1).length - 1);
    }

    @Test
    void parentMrnaTypedBySoAccessionIsCompared() {
        feature(OntologyTerm.MRNA.ID, "mrna1", null, 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 700, "T2");

        assertThrows(ValidationException.class, () -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
    }

    @Test
    void everyMismatchInTheAnnotationIsReportedTogether() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 700, "T2");
        feature(MRNA_FEATURE, "mrna2", null, 100, 900, "T3");
        feature(CDS_FEATURE, "cds2", "mrna2", 150, 750, "T4");

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
        assertTrue(exception.getMessage().contains("CDS \"cds1\""));
        assertTrue(exception.getMessage().contains("CDS \"cds2\""));
    }

    @Test
    void lineOfTheCdsIsReportedWhereKnown() {
        feature(MRNA_FEATURE, "mrna1", null, 100, 800, "T1");
        feature(CDS_FEATURE, "cds1", "mrna1", 150, 700, "T2").setLine(7);

        ValidationException exception = assertThrows(
                ValidationException.class, () -> validation.validateCdsTranscriptIdMatchesParent(annotation, 1));
        assertTrue(exception.getMessage().contains("CDS \"cds1\" at ACC123:150..700 (line 7)"));
    }

    private GFF3Feature feature(
            String featureName, String id, String parentId, long start, long end, String transcriptId) {
        GFF3Feature feature = TestUtils.createGFF3Feature(id, parentId, featureName, ACCESSION, start, end);
        if (parentId != null) {
            feature.addAttribute(GFF3Attributes.ATTRIBUTE_PARENT, parentId);
        }
        if (transcriptId != null) {
            feature.addAttribute(GFF3Attributes.TRANSCRIPT_ID, transcriptId);
        }
        annotation.addFeature(feature);
        return feature;
    }
}
