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
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class DuplicateFeatureValidationTest {

    private DuplicateFeatureValidation duplicateFeatureValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        duplicateFeatureValidation = new DuplicateFeatureValidation();
        gff3Annotation = new GFF3Annotation();
    }

    @Test
    public void testAnnotationDuplicateFeatureSuccess() {
        List<GFF3Feature> features = List.of(
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227461"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("ID_1"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227462"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("ID_2"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227463"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("ID_3"))),
                TestUtils.createGFF3Feature(
                        Feature.CDS_FEATURE_NAME,
                        "CDS",
                        Map.of(
                                GFF3Attributes.PROTEIN_ID,
                                List.of("CAL1227464"),
                                GFF3Attributes.ATTRIBUTE_ID,
                                List.of("ID_4"))));
        gff3Annotation.setFeatures(features);

        Assertions.assertDoesNotThrow(() -> duplicateFeatureValidation.validateDuplicateProtein(gff3Annotation, 1));
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
        gff3Annotation.setFeatures(features);

        Assertions.assertDoesNotThrow(() -> duplicateFeatureValidation.validateDuplicateProtein(gff3Annotation, 1));
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

        gff3Annotation.setFeatures(features);
        Assertions.assertDoesNotThrow(() -> duplicateFeatureValidation.validateDuplicateProtein(gff3Annotation, 1));
    }

    @Test
    public void testAnnotationPropetideCDSFeatureFailure() {
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

        gff3Annotation.setFeatures(List.of(f1, f2, f3, f4));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> duplicateFeatureValidation.validateDuplicateProtein(gff3Annotation, 4));

        Assertions.assertTrue(ex.getMessage()
                .contains("Duplicate Protein Id \"CAL1227463\" found in the \"%s\" at location \"%s\""
                        .formatted(f4.getName(), f4.getStart() + " " + f4.getEnd())));
    }
}
