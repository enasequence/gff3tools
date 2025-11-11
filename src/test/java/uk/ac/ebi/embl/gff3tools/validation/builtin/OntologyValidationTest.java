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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.ConversionUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyClient;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
public class OntologyValidationTest {

    private OntologyValidation ontologyValidation;
    private GFF3Feature mockFeature;

    @BeforeEach
    public void setup() {
        ontologyValidation = new OntologyValidation();
        mockFeature = Mockito.mock(GFF3Feature.class);
    }

    @Test
    public void testValidateFeature_withValidFeature_shouldPass() throws ValidationException {
        // Arrange
        Mockito.when(mockFeature.getName()).thenReturn("gene");

        OntologyClient ontologyClient = Mockito.mock(OntologyClient.class);
        Mockito.when(ontologyClient.isFeatureSoTerm("gene")).thenReturn(true);

        try (MockedStatic<ConversionUtils> mockedUtils = Mockito.mockStatic(ConversionUtils.class)) {
            mockedUtils.when(ConversionUtils::getOntologyClient).thenReturn(ontologyClient);

            // Act & Assert (no exception expected)
            assertDoesNotThrow(() -> ontologyValidation.validateFeature(mockFeature, 10));
        }
    }

    @Test
    public void testValidateFeature_withInvalidFeature_shouldThrowException() {
        // Arrange
        Mockito.when(mockFeature.getName()).thenReturn("invalidFeature");

        OntologyClient ontologyClient = Mockito.mock(OntologyClient.class);
        Mockito.when(ontologyClient.isFeatureSoTerm("invalidFeature")).thenReturn(false);

        try (MockedStatic<ConversionUtils> mockedUtils = Mockito.mockStatic(ConversionUtils.class)) {
            mockedUtils.when(ConversionUtils::getOntologyClient).thenReturn(ontologyClient);

            // Act & Assert
            ValidationException exception =
                    assertThrows(ValidationException.class, () -> ontologyValidation.validateFeature(mockFeature, 42));

            assertTrue(exception.getMessage().contains("invalidFeature"));
            assertTrue(exception.getMessage().contains("line 42"));
        }
    }
}
