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
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.converter.TestUtils;
import uk.ac.ebi.embl.converter.exception.ValidationException;
import uk.ac.ebi.embl.converter.gff3.GFF3Annotation;
import uk.ac.ebi.embl.converter.gff3.GFF3Anthology;
import uk.ac.ebi.embl.converter.gff3.GFF3Feature;

public class LocationValidationTest {

    private GFF3Feature feature;

    private LocationValidation locationValidation;

    @BeforeEach
    public void setUp() {
        locationValidation = new LocationValidation();
    }

    @Test
    public void testLocationValidationSuccess() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.CDS_FEATURE_NAME, 1L, 18L);
        Assertions.assertDoesNotThrow(() -> locationValidation.validateFeature(feature, 1));
    }

    @Test
    public void testLocationValidationFailure_StartEndZero() {
        feature = TestUtils.createGFF3Feature(GFF3Anthology.CDS_FEATURE_NAME, 0L, 0L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> locationValidation.validateFeature(feature, 1));
        assertTrue(exception.getMessage().contains("Invalid start/end for accession"));
    }

    @Test
    public void testLocationValidationFailure_EndGreaterThanStart() {
        feature = TestUtils.createGFF3Feature(Feature.CDS_FEATURE_NAME, 34L, 13L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> locationValidation.validateFeature(feature, 1));
        assertTrue(exception.getMessage().contains("Invalid start/end for accession"));
    }

    @Test
    public void testAnnotation_PropetideCDSFeature_Success() throws ValidationException {
        GFF3Feature cds = TestUtils.createGFF3Feature(GFF3Anthology.CDS_FEATURE_NAME, 100L, 500L);
        GFF3Feature prop = TestUtils.createGFF3Feature(GFF3Anthology.PROPETIDE_FEATURE_NAME, 200L, 350L);

        locationValidation.validateFeature(cds, 1);
        locationValidation.validateFeature(prop, 2);

        Assertions.assertDoesNotThrow(() -> locationValidation.validateAnnotation(new GFF3Annotation(), 3));
    }

    @Test
    public void testAnnotation_PropetideCDSFeature_Failure() throws ValidationException {
        GFF3Feature cds = TestUtils.createGFF3Feature(GFF3Anthology.CDS_FEATURE_NAME, 300L, 500L);
        GFF3Feature prop = TestUtils.createGFF3Feature(GFF3Anthology.PROPETIDE_FEATURE_NAME, 100L, 200L);

        locationValidation.validateFeature(cds, 1);
        locationValidation.validateFeature(prop, 2);
        ValidationException exception = assertThrows(
                ValidationException.class, () -> locationValidation.validateAnnotation(new GFF3Annotation(), 3));

        assertTrue(exception.getMessage().contains("not inside any CDS"));
    }

    @Test
    public void testAnnotation_PropetidePeptideFeature_Success() throws ValidationException {
        GFF3Feature cds = TestUtils.createGFF3Feature(GFF3Anthology.CDS_FEATURE_NAME, 1L, 500L);
        GFF3Feature sig = TestUtils.createGFF3Feature(GFF3Anthology.SIG_PEPTIDE_FEATURE_NAME, 300L, 350L);
        GFF3Feature prop = TestUtils.createGFF3Feature(GFF3Anthology.PROPETIDE_FEATURE_NAME, 100L, 200L);

        locationValidation.validateFeature(cds, 1);
        locationValidation.validateFeature(sig, 2);
        locationValidation.validateFeature(prop, 3);

        Assertions.assertDoesNotThrow(() -> locationValidation.validateAnnotation(new GFF3Annotation(), 4));
    }

    @Test
    public void testAnnotation_PropetidePeptideFeature_Failure() throws ValidationException {
        GFF3Feature cds = TestUtils.createGFF3Feature(GFF3Anthology.CDS_FEATURE_NAME, 1L, 500L);
        GFF3Feature sig = TestUtils.createGFF3Feature(GFF3Anthology.SIG_PEPTIDE_FEATURE_NAME, 100L, 150L);
        GFF3Feature prop = TestUtils.createGFF3Feature(GFF3Anthology.PROPETIDE_FEATURE_NAME, 120L, 200L);

        locationValidation.validateFeature(cds, 1);
        locationValidation.validateFeature(sig, 2);
        locationValidation.validateFeature(prop, 3);

        ValidationException ex = assertThrows(
                ValidationException.class, () -> locationValidation.validateAnnotation(new GFF3Annotation(), 4));

        assertTrue(ex.getMessage().contains("overlaps with peptide features"));
    }
}
