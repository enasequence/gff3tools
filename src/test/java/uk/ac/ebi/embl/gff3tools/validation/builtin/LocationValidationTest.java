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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class LocationValidationTest {

    private GFF3Feature feature;

    private LocationValidation locationValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        locationValidation = new LocationValidation();
        gff3Annotation = new GFF3Annotation();
    }

    @Test
    public void testLocationValidationSuccessLinear() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 1L, 18L);
        Assertions.assertDoesNotThrow(() -> locationValidation.validateLocation(feature, 1));
    }

    @Test
    public void testLocationValidationFailureStartEndZero() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 0L, 0L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> locationValidation.validateLocation(feature, 1));
        assertTrue(exception.getMessage().contains("Invalid start/end for accession"));
    }

    @Test
    public void testLocationValidationFailureEndLessThanStartLinear() {
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 34L, 13L);
        ValidationException exception =
                assertThrows(ValidationException.class, () -> locationValidation.validateLocation(feature, 1));
        assertTrue(exception.getMessage().contains("Invalid start/end for accession"));
    }

    @Test
    public void testLocationValidationSuccessCircularEndLessThanStart() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS_REGION.name(), 4800L, 200L, Map.of(GFF3Attributes.CIRCULAR_RNA, "true"));
        Assertions.assertDoesNotThrow(() -> locationValidation.validateLocation(feature, 1));
    }

    @Test
    public void testLocationValidationSuccessCircularStartEndNormal() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS_REGION.name(), 10L, 100L, Map.of(GFF3Attributes.CIRCULAR_RNA, "true"));
        Assertions.assertDoesNotThrow(() -> locationValidation.validateLocation(feature, 1));
    }

    @Test
    public void testLocationValidationFailureCircularInvalidStart() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS_REGION.name(), 0L, 100L, Map.of(GFF3Attributes.CIRCULAR_RNA, "true"));
        ValidationException exception =
                assertThrows(ValidationException.class, () -> locationValidation.validateLocation(feature, 1));
        assertTrue(exception.getMessage().contains("Invalid start/end for accession"));
    }

    @Test
    public void testAnnotationPropetideCDSFeatureSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 100L, 500L);
        GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 200L, 350L);

        gff3Annotation.setFeatures(List.of(cds, prop));

        Assertions.assertDoesNotThrow(() -> locationValidation.validateCdsLocation(gff3Annotation, 1));
    }

    @Test
    public void testAnnotationPropetideCDSFeatureFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 300L, 500L);
        GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 100L, 200L);

        gff3Annotation.setFeatures(List.of(cds, prop));

        ValidationException exception = assertThrows(
                ValidationException.class, () -> locationValidation.validateCdsLocation(gff3Annotation, 3));

        assertTrue(exception.getMessage().contains("not inside any CDS"));
    }

    @Test
    public void testAnnotationPropetidePeptideFeatureSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 1L, 500L);
        GFF3Feature sig = TestUtils.createGFF3Feature(OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(), 300L, 350L);
        GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 100L, 200L);

        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        Assertions.assertDoesNotThrow(() -> locationValidation.validateCdsLocation(gff3Annotation, 4));
    }

    @Test
    public void testAnnotationPropetidePeptideFeatureFailure() {
        GFF3Feature cds = TestUtils.createGFF3Feature(OntologyTerm.CDS_REGION.name(), 1L, 500L);
        GFF3Feature sig = TestUtils.createGFF3Feature(OntologyTerm.SIGNAL_PEPTIDE_REGION_OF_CDS.name(), 100L, 150L);
        GFF3Feature prop = TestUtils.createGFF3Feature(OntologyTerm.PROPEPTIDE_REGION_OF_CDS.name(), 120L, 200L);

        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = assertThrows(
                ValidationException.class, () -> locationValidation.validateCdsLocation(gff3Annotation, 4));

        assertTrue(ex.getMessage().contains("overlaps with peptide features"));
    }
}
