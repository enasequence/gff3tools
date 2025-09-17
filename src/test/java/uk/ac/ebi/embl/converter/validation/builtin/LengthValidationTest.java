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
package uk.ac.ebi.embl.converter.validation.builtin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.TestUtils;
import uk.ac.ebi.embl.converter.exception.ValidationException;
import uk.ac.ebi.embl.converter.gff3.GFF3Anthology;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;

public class LengthValidationTest {

    GFF3Feature feature;

    private LengthValidation lengthValidation;

    @BeforeEach
    public void setUp() {
        lengthValidation = new LengthValidation();
    }

    @Test
    public void testPropetideValidationSuccess() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.PROPETIDE_FEATURE_NAME, 1L, 180L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateFeature(feature, 1));
    }

    @Test
    public void testPropetideValidationFailure() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.PROPETIDE_FEATURE_NAME, 1L, 31L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> lengthValidation.validateFeature(feature, 1));
        assertTrue(exception.getMessage().contains("Propeptide feature length must be multiple of 3 for accession"));
    }

    @Test
    public void testIntronValidationSuccess() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.INTRON_FEATURE_NAME, 1L, 20L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateFeature(feature, 1));
    }

    @Test
    public void testIntronValidationFailure() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.INTRON_FEATURE_NAME, 1L, 9L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> lengthValidation.validateFeature(feature, 1));
        assertTrue(exception.getMessage().contains("Intron feature length is invalid for accession"));
    }

    @Test
    public void testExonValidationSuccess() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.EXON_FEATURE_NAME, 1L, 30L);
        Assertions.assertDoesNotThrow(() -> lengthValidation.validateFeature(feature, 1));
    }

    @Test
    public void testExonValidationFailure() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.EXON_FEATURE_NAME, 1L, 14L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> lengthValidation.validateFeature(feature, 1));
        assertTrue(exception.getMessage().contains("Exon feature length is invalid for accession"));
    }
}
