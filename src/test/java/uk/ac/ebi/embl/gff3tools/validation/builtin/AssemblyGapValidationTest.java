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
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class AssemblyGapValidationTest {

    private AssemblyGapValidation assemblyGapValidation;

    private GFF3Annotation gff3Annotation;

    private GFF3Feature feature;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        assemblyGapValidation = new AssemblyGapValidation();
    }

    @Test
    public void testValidateAssemblyGapInvalidAttributes() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.PRODUCT, "product"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.PRODUCT, "product1"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.PRODUCT, "product2"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains("\"%s\" qualifier is not allowed in \"assembly_gap\" feature."
                        .formatted(GFF3Attributes.PRODUCT)));
    }

    @Test
    public void testValidateAssemblyGapInvalidAttributeValue() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "ap12"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "123"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "gaptpe"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"gap_type\" value \"ap12\" is invalid"));
    }

    @Test
    public void testValidateAssemblyGapFeatureWithoutLinkage() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.GENE_FEATURE_NAME,
                GFF3Anthology.GENE_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.GENE_FEATURE_NAME,
                GFF3Anthology.GENE_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds"));
        gff3Annotation.setFeatures(List.of(cds, sig));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "\"gap_type\" and  \"linkage_evidence\" qualifiers are only allowed in assembly_gap feature"));
    }

    @Test
    public void testValidateAssemblyGapFeatureWithoutGap() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.GAP_FEATURE_NAME,
                GFF3Anthology.GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds"));
        gff3Annotation.setFeatures(List.of(cds, sig));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"assembly_gap\" and  \"gap\" feature are mutually exclusive"));
    }

    @Test
    public void testValidateAssemblyGapFeatureSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.ESTIMATED_LENGTH, "12"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.ESTIMATED_LENGTH, "20"));
        gff3Annotation.setFeatures(List.of(cds, sig));

        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));
    }

    @Test
    public void testValidateLinkageEvidenceNotAllowed() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.LINKAGE_EVIDENCE, "paired ends"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.ESTIMATED_LENGTH, "12"));
        gff3Annotation.setFeatures(List.of(cds, sig));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));

        Assertions.assertTrue(
                ex.getMessage().contains("\"linkage_evidence\" qualifier is  allowed in \"assembly_gap\""));
    }

    @Test
    public void testValidateLinkageEvidenceMissing() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "within scaffold", GFF3Attributes.ESTIMATED_LENGTH, "10"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "between scaffolds", GFF3Attributes.ESTIMATED_LENGTH, "12"));
        gff3Annotation.setFeatures(List.of(cds, sig));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));

        Assertions.assertTrue(
                ex.getMessage()
                        .contains(
                                "\"linkage_evidence\" qualifier must exists in feature \"assembly_gap\",if qualifier \"gap_type\" value equals to \"within scaffold\""));
    }

    @Test
    public void testValidateLinkageEvidenceSuccess() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(GFF3Attributes.GAP_TYPE, "within scaffold", GFF3Attributes.LINKAGE_EVIDENCE, "paired ends"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                Map.of(
                        GFF3Attributes.GAP_TYPE,
                        "repeat within scaffold",
                        GFF3Attributes.LINKAGE_EVIDENCE,
                        "paired ends",
                        GFF3Attributes.ESTIMATED_LENGTH,
                        "12"));
        gff3Annotation.setFeatures(List.of(cds, sig));

        Assertions.assertDoesNotThrow(() -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));
    }

    @Test
    public void testValidateAssembleGapInvalidLocation() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                0,
                0,
                Map.of(GFF3Attributes.GAP_TYPE, "within scaffold", GFF3Attributes.LINKAGE_EVIDENCE, "paired ends"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                GFF3Anthology.ASSEMBLY_GAP_FEATURE_NAME,
                0,
                0,
                Map.of(
                        GFF3Attributes.GAP_TYPE,
                        "repeat within scaffold",
                        GFF3Attributes.LINKAGE_EVIDENCE,
                        "paired ends",
                        GFF3Attributes.ESTIMATED_LENGTH,
                        "12"));
        gff3Annotation.setFeatures(List.of(cds, sig));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> assemblyGapValidation.validateAnnotation(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage().contains("\"assembly_gap\" location is invalid"));
    }
}
