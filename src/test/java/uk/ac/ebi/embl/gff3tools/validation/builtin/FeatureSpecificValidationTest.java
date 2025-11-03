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
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class FeatureSpecificValidationTest {

    private FeatureSpecificValidation attributeSpecificValidation;

    private GFF3Annotation gff3Annotation;

    private GFF3Feature feature;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        attributeSpecificValidation = new FeatureSpecificValidation();
    }

    @Test
    public void testValidateOperonFeaturesWithNoOperon() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.PRODUCT, "16S ribosomalRNA "));

        Assertions.assertDoesNotThrow(() -> attributeSpecificValidation.validateOperonFeatures(feature, 1));
    }

    @Test
    public void testValidateOperonFeaturesWithOperonQualifier() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.OPERON.name(), OntologyTerm.OPERON.name(), Map.of(GFF3Attributes.OPERON, "operon"));

        Assertions.assertDoesNotThrow(() -> attributeSpecificValidation.validateOperonFeatures(feature, 1));
    }

    @Test
    public void testValidateOperonQualifierWithoutOperonFeature() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.OPERON, "operon"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributeSpecificValidation.validateOperonFeatures(feature, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Feature \"%s\" belongs to operon \"%s\", but no other features share this operon. Expected at least one additional member."
                                .formatted(OntologyTerm.CDS.name(), GFF3Attributes.OPERON)));
    }

    @Test
    public void testValidateMultipleOperonQualifierWithoutOperonFeature() {
        GFF3Feature f1 = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.OPERON, "operon1"));
        GFF3Feature f2 = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.OPERON, "operon1"));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributeSpecificValidation.validateOperonFeatures(f1, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Feature \"%s\" belongs to operon \"%s\", but no other features share this operon. Expected at least one additional member."
                                .formatted(OntologyTerm.CDS.name(), "operon1")));

        ValidationException ex1 = Assertions.assertThrows(
                ValidationException.class, () -> attributeSpecificValidation.validateOperonFeatures(f2, 1));

        Assertions.assertTrue(ex1.getMessage()
                .contains(
                        "Feature \"%s\" belongs to operon \"%s\", but no other features share this operon. Expected at least one additional member."
                                .formatted(OntologyTerm.SIGNAL_PEPTIDE.name(), "operon1")));
    }
}
