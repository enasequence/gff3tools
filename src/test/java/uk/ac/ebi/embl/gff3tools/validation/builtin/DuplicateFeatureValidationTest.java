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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.feature.Feature;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class DuplicateFeatureValidationTest {

    private DuplicateFeatureValidation duplicateFeatureValidation;

    @BeforeEach
    public void setUp() {
        duplicateFeatureValidation = new DuplicateFeatureValidation();
    }

    @Test
    public void testAnnotationDuplicateFeatureSuccessNoDuplicates() {
        List<GFF3Feature> features = List.of(
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227461"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("CDS"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227462"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("RNA"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227463"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("CDS"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227464"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("CDS"))));
        int i = 0;
        Assertions.assertAll(features.stream().map((f) -> () -> duplicateFeatureValidation.validateFeature(f, i + 1)));
    }

    @Test
    public void testAnnotationDuplicateFeatureSuccessWithDuplicateProteinId() {
        List<GFF3Feature> features = List.of(
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227461"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("CDS"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227462"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("RNA"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227463"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("CDS"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227463"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("CDS"))));

        int i = 0;
        Assertions.assertAll(features.stream().map((f) -> () -> duplicateFeatureValidation.validateFeature(f, i + 1)));
    }

    @Test
    public void testAnnotationPropetideCDSFeatureFailure() throws ValidationException {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227461"), GFF3Attributes.ATTRIBUTE_ID, List.of("CDS")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227462"), GFF3Attributes.ATTRIBUTE_ID, List.of("RNA")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227463"), GFF3Attributes.ATTRIBUTE_ID, List.of("CDS")));
        GFF3Feature f4 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227463"), GFF3Attributes.ATTRIBUTE_ID, List.of("RNA")));

        duplicateFeatureValidation.validateFeature(f1, 1);
        duplicateFeatureValidation.validateFeature(f2, 2);
        duplicateFeatureValidation.validateFeature(f3, 3);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> duplicateFeatureValidation.validateFeature(f4, 4));

        Assertions.assertAll(
                () -> Assertions.assertTrue(ex.getMessage().contains("Duplicate Protein Id \"CAL1227463\"")),
                () -> Assertions.assertTrue(ex.getMessage().contains("First occurrence at line 3")),
                () -> Assertions.assertTrue(ex.getMessage().contains("conflicting occurrence at line 4")));
    }

    @Test
    public void testAnnotation_PropetideCDSFeature_WithAllDuplicates() throws ValidationException {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227461"), GFF3Attributes.ATTRIBUTE_ID, List.of("CDS")));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227462"), GFF3Attributes.ATTRIBUTE_ID, List.of("RNA")));
        GFF3Feature f3 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227463"), GFF3Attributes.ATTRIBUTE_ID, List.of("CDS")));
        GFF3Feature f4 = TestUtils.createGFF3Feature(
                Feature.CDS_FEATURE_NAME,
                "CDS",
                Map.of(GFF3Attributes.PROTEIN_ID, List.of("CAL1227463"), GFF3Attributes.ATTRIBUTE_ID, List.of("RNA")));

        duplicateFeatureValidation.validateFeature(f1, 1);
        duplicateFeatureValidation.validateFeature(f2, 2);
        duplicateFeatureValidation.validateFeature(f3, 3);

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> duplicateFeatureValidation.validateFeature(f4, 4));

        Assertions.assertAll(
                () -> Assertions.assertTrue(ex.getMessage().contains("Duplicate Protein Id \"CAL1227463\"")),
                () -> Assertions.assertTrue(ex.getMessage().contains("First occurrence at line 3")),
                () -> Assertions.assertTrue(ex.getMessage().contains("conflicting occurrence at line 4")));
    }
}
