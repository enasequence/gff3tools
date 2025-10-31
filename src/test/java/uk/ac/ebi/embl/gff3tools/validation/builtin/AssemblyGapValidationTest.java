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

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class AssemblyGapValidationTest {

    private AssemblyGapValidation assemblyGapValidation;

    private GFF3Annotation gff3Annotation;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        assemblyGapValidation = new AssemblyGapValidation();
    }

    @Test
    public void testValidateAssemblyGapInvalidAttributes() {
        GFF3Feature f1 = TestUtils.createGFF3Feature("gap", "gap", Map.of(GFF3Attributes.ATTRIBUTE_ID, "id"));
        GFF3Feature f2 = TestUtils.createGFF3Feature("gap", "gap", Map.of(GFF3Attributes.PRODUCT, "product1"));

        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f1, 1));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateGapFeature(f2, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("\"%s\" attributes is not allowed in \"gap\" feature.".formatted(GFF3Attributes.PRODUCT)));
    }

    @Test
    public void testValidateAssemblyGapInvalidAttributeValue() {
        GFF3Feature f1 = TestUtils.createGFF3Feature("gap", "gap", Map.of(GFF3Attributes.GAP_TYPE, "ap12"));
        GFF3Feature f2 = TestUtils.createGFF3Feature("gap", "gap", Map.of(GFF3Attributes.GAP_TYPE, "centromere"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateGapFeature(f1, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"gap_type\" value \"ap12\" is invalid"));
        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f2, 1));
    }

    @Test
    public void testValidateAssemblyGapFeatureWithoutLinkage() {
        GFF3Feature f1 =
                TestUtils.createGFF3Feature("gap", "gap", Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds"));
        GFF3Feature f2 =
                TestUtils.createGFF3Feature("gene", "gene", Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds"));

        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f1, 1));
        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateGapFeature(f2, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("\"gap_type\" and  \"linkage_evidence\" attributes are only allowed in gap feature"));
    }

    @Test
    public void testValidateAssemblyGapFeatureSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "gap",
                "gap",
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.ESTIMATED_LENGTH, "12"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "gap",
                "gap",
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.ESTIMATED_LENGTH, "20"));

        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f1, 1));
        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f2, 1));
    }

    @Test
    public void testValidateLinkageEvidenceNotAllowed() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "gap",
                "gap",
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.LINKAGE_EVIDENCE, "paired ends"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateGapFeature(f1, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"linkage_evidence\" attributes is  allowed in \"gap\""));
    }

    @Test
    public void testValidateLinkageEvidenceMissing() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "gap",
                "gap",
                Map.of(GFF3Attributes.GAP_TYPE, "within scaffold", GFF3Attributes.ESTIMATED_LENGTH, "10"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateGapFeature(f1, 1));

        Assertions.assertTrue(
                ex.getMessage()
                        .contains(
                                "\"linkage_evidence\" attributes must exists in feature \"gap\",if attributes \"gap_type\" value equals to \"within scaffold\""));
    }

    @Test
    public void testValidateLinkageEvidenceSuccess() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "gap",
                "gap",
                Map.of(GFF3Attributes.GAP_TYPE, "within scaffold", GFF3Attributes.LINKAGE_EVIDENCE, "paired ends"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "gap",
                "gap",
                Map.of(
                        GFF3Attributes.GAP_TYPE,
                        "repeat within scaffold",
                        GFF3Attributes.LINKAGE_EVIDENCE,
                        "paired ends",
                        GFF3Attributes.ESTIMATED_LENGTH,
                        "12"));

        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f1, 1));
        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f2, 1));
    }

    @Test
    public void testValidateAssembleGapInvalidLocation() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                "gap",
                1,
                10,
                Map.of(GFF3Attributes.GAP_TYPE, "within scaffold", GFF3Attributes.LINKAGE_EVIDENCE, "paired ends"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                "gap",
                0,
                0,
                Map.of(
                        GFF3Attributes.GAP_TYPE,
                        "repeat within scaffold",
                        GFF3Attributes.LINKAGE_EVIDENCE,
                        "paired ends",
                        GFF3Attributes.ESTIMATED_LENGTH,
                        "12"));
        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateGapFeature(f1, 1));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateGapFeature(f2, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"gap\" location is invalid"));
    }
}
