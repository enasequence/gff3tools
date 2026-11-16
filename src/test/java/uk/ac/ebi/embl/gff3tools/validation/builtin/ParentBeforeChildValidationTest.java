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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

class ParentBeforeChildValidationTest {

    private static final String ACCESSION = "ACC123";

    private ParentBeforeChildValidation validation;
    private GFF3Annotation annotation;

    @BeforeEach
    void setUp() {
        validation = new ParentBeforeChildValidation();
        annotation = new GFF3Annotation();
    }

    @Test
    void parentsListedBeforeChildrenAreValid() {
        feature("gene1", null, "gene", 1, 1000);
        feature("mrna1", "gene1", "mRNA", 1, 1000);
        feature("cds1", "mrna1", "CDS", 51, 950);

        assertDoesNotThrow(() -> validation.validateParentBeforeChild(annotation, 1));
    }

    @Test
    void childListedBeforeItsParentIsReported() {
        feature("mrna1", "gene1", "mRNA", 1, 1000);
        feature("gene1", null, "gene", 1, 1000);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateParentBeforeChild(annotation, 1));
        assertTrue(exception.getMessage().contains("Parent features must be listed before their children"));
        assertTrue(exception
                .getMessage()
                .contains("Feature mRNA \"mrna1\" at " + ACCESSION + ":1..1000 is listed before its Parent \"gene1\""));
    }

    @Test
    void everyChildBeforeItsParentIsReportedTogether() {
        feature("cds1", "mrna1", "CDS", 51, 950);
        feature("mrna1", "gene1", "mRNA", 1, 1000);
        feature("gene1", null, "gene", 1, 1000);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateParentBeforeChild(annotation, 1));
        assertTrue(exception.getMessage().contains("Feature CDS \"cds1\""));
        assertTrue(exception.getMessage().contains("Feature mRNA \"mrna1\""));
    }

    @Test
    void sourceLinesAreReportedWhenKnown() {
        feature("mrna1", "gene1", "mRNA", 1, 1000).setLine(3);
        feature("gene1", null, "gene", 1, 1000).setLine(4);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateParentBeforeChild(annotation, 1));
        assertTrue(exception.getMessage().contains("(line 3) is listed before its Parent \"gene1\" (line 4)"));
    }

    /** Features converted from a flat file have no source line, so none is reported. */
    @Test
    void unknownSourceLinesAreLeftOut() {
        feature("mrna1", "gene1", "mRNA", 1, 1000);
        feature("gene1", null, "gene", 1, 1000);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateParentBeforeChild(annotation, 1));
        assertFalse(exception.getMessage().contains("(line"));
    }

    @Test
    void childSplitOverSeveralLinesIsReportedOnce() {
        feature("cds1", "mrna1", "CDS", 51, 200);
        feature("cds1", "mrna1", "CDS", 401, 950);
        feature("mrna1", null, "mRNA", 1, 1000);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateParentBeforeChild(annotation, 1));
        assertEquals(1, exception.getMessage().split("Feature CDS \"cds1\"", -1).length - 1);
    }

    /** The parent's first line is enough; the child need not follow every line of it. */
    @Test
    void childBetweenTheLinesOfASplitParentIsValid() {
        feature("mrna1", null, "mRNA", 1, 200);
        feature("cds1", "mrna1", "CDS", 51, 950);
        feature("mrna1", null, "mRNA", 401, 1000);

        assertDoesNotThrow(() -> validation.validateParentBeforeChild(annotation, 1));
    }

    @Test
    void childWithoutAnIdIsReported() {
        feature(null, "mrna1", "exon", 1, 200);
        feature("mrna1", null, "mRNA", 1, 1000);

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateParentBeforeChild(annotation, 1));
        assertTrue(exception.getMessage().contains("Feature exon \"<no ID>\" at " + ACCESSION + ":1..200"));
    }

    /** A Parent the annotation does not hold at all is the business of DANGLING_PARENT. */
    @Test
    void missingParentIsLeftToDanglingParent() {
        feature("exon1", "mrna1", "exon", 1, 200);

        assertDoesNotThrow(() -> validation.validateParentBeforeChild(annotation, 1));
    }

    private GFF3Feature feature(String id, String parentId, String type, long start, long end) {
        GFF3Feature feature = TestUtils.createGFF3Feature(id, parentId, type, ACCESSION, start, end);
        annotation.addFeature(feature);
        return feature;
    }
}
