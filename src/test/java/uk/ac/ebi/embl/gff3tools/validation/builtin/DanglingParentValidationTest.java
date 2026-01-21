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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

class DanglingParentValidationTest {

    private DanglingParentValidation validation;

    @BeforeEach
    void setUp() {
        validation = new DanglingParentValidation();
    }

    @Test
    void validateAnnotation_withValidParentReference_shouldPass() throws ValidationException {
        // Arrange
        GFF3Annotation annotation = new GFF3Annotation();

        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "ACC123", 100, 500);
        GFF3Feature mrna = TestUtils.createGFF3Feature("mrna1", "gene1", "mRNA", "ACC123", 100, 500);
        GFF3Feature exon = TestUtils.createGFF3Feature("exon1", "mrna1", "exon", "ACC123", 100, 200);

        annotation.addFeature(gene);
        annotation.addFeature(mrna);
        annotation.addFeature(exon);

        // Act & Assert - no exception expected
        assertDoesNotThrow(() -> validation.validateAnnotation(annotation, 1));
    }

    @Test
    void validateAnnotation_withDanglingParent_shouldThrowException() {
        // Arrange
        GFF3Annotation annotation = new GFF3Annotation();

        // exon references mrna1, but mrna1 is not in this annotation block
        GFF3Feature exon = TestUtils.createGFF3Feature("exon2", "mrna1", "exon", "ACC123", 300, 400);

        annotation.addFeature(exon);

        // Act & Assert
        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation, 1));

        assertTrue(exception.getMessage().contains("exon2"));
        assertTrue(exception.getMessage().contains("mrna1"));
        assertTrue(exception.getMessage().contains("not present in the current annotation block"));
    }

    @Test
    void validateAnnotation_withFeatureWithoutParent_shouldPass() throws ValidationException {
        // Arrange
        GFF3Annotation annotation = new GFF3Annotation();

        GFF3Feature gene = TestUtils.createGFF3Feature("gene1", null, "gene", "ACC123", 100, 500);
        annotation.addFeature(gene);

        // Act & Assert - no exception expected
        assertDoesNotThrow(() -> validation.validateAnnotation(annotation, 1));
    }

    @Test
    void validateAnnotation_withEmptyAnnotation_shouldPass() throws ValidationException {
        // Arrange
        GFF3Annotation annotation = new GFF3Annotation();

        // Act & Assert - no exception expected
        assertDoesNotThrow(() -> validation.validateAnnotation(annotation, 1));
    }

    @Test
    void validateAnnotation_errorMessageContainsHelpfulInformation() {
        // Arrange
        GFF3Annotation annotation = new GFF3Annotation();
        GFF3Feature exon = TestUtils.createGFF3Feature("exon2", "missing_parent", "exon", "Chr1", 300, 400);

        annotation.addFeature(exon);

        // Act
        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation, 1));

        // Assert - check error message contains helpful information
        String message = exception.getMessage();
        assertTrue(message.contains("exon2"), "Should contain feature ID");
        assertTrue(message.contains("exon"), "Should contain feature type");
        assertTrue(message.contains("Chr1"), "Should contain sequence ID");
        assertTrue(message.contains("300"), "Should contain start position");
        assertTrue(message.contains("400"), "Should contain end position");
        assertTrue(message.contains("missing_parent"), "Should contain parent ID");
        assertTrue(message.contains("###"), "Should mention ### directive");
    }

    @Test
    void validateAnnotation_withFeatureWithoutId_shouldHandleGracefully() {
        // Arrange
        GFF3Annotation annotation = new GFF3Annotation();

        // Feature without ID but with dangling parent
        GFF3Feature exon = TestUtils.createGFF3Feature(null, "missing_parent", "exon", "ACC123", 300, 400);
        annotation.addFeature(exon);

        // Act
        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateAnnotation(annotation, 1));

        // Assert - should show "<no ID>" for feature without ID
        assertTrue(exception.getMessage().contains("<no ID>"));
    }
}
