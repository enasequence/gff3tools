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
        gff3Annotation.setFeatures(List.of(feature));

        Assertions.assertDoesNotThrow(() -> attributeSpecificValidation.validateOperonFeatures(gff3Annotation, 1));
    }

    @Test
    public void testValidateOperonFeaturesWithOperonQualifier() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.OPERON.name(), OntologyTerm.OPERON.name(), Map.of(GFF3Attributes.OPERON, "operon"));
        gff3Annotation.setFeatures(List.of(feature));

        Assertions.assertDoesNotThrow(() -> attributeSpecificValidation.validateOperonFeatures(gff3Annotation, 1));
    }

    @Test
    public void testValidateOperonQualifierWithoutOperonFeature() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.OPERON, "operon"));
        gff3Annotation.setFeatures(List.of(feature));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributeSpecificValidation.validateOperonFeatures(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "Feature \"%s\" refers to operon \"%s\". Please provide an operon feature which spans the entire operon region"
                                .formatted(GFF3Anthology.CDS_FEATURE_NAME, GFF3Attributes.OPERON)));
    }

    @Test
    public void testValidateMultiOperonQualifierWithoutOperonFeature() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(GFF3Attributes.OPERON, "operon1"));
        GFF3Feature sig = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.OPERON, "operon1"));
        GFF3Feature prop = TestUtils.createGFF3Feature(
                OntologyTerm.PSEUDOGENIC_CDS.name(),
                OntologyTerm.PSEUDOGENIC_CDS.name(),
                Map.of(GFF3Attributes.OPERON, "operon"));
        gff3Annotation.setFeatures(List.of(cds, sig, prop));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributeSpecificValidation.validateOperonFeatures(gff3Annotation, 1));

        Assertions.assertTrue(ex.getMessage()
                .contains(
                        "\"%s\" number of features refer to operon \"%s\". Please provide an operon feature which spans the entire operon"
                                .formatted(2, "operon1")));
    }

    @Test
    public void testValidatePeptideFeatureWithMatchingPseudo() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG, "L1",
                        GFF3Attributes.PSEUDO, ""));

        GFF3Feature peptide = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG, "L1",
                        GFF3Attributes.PSEUDO, "true"));

        gff3Annotation.setFeatures(List.of(cds, peptide));

        Assertions.assertDoesNotThrow(() -> attributeSpecificValidation.validatePeptideFeature(gff3Annotation, 1));
    }

    @Test
    public void testValidatePeptideFeatureMissingPseudoThrowsException() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG, "L1",
                        GFF3Attributes.PSEUDO, "true"));

        GFF3Feature peptide = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "L1"));

        gff3Annotation.setFeatures(List.of(cds, peptide));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributeSpecificValidation.validatePeptideFeature(gff3Annotation, 2));

        Assertions.assertTrue(ex.getMessage().contains("Pseudo qualifier missing in peptide feature"));
    }

    @Test
    public void testValidatePeptideFeatureDifferentLocusTagShouldPass() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.LOCUS_TAG, "L1",
                        GFF3Attributes.PSEUDO, "true"));

        GFF3Feature peptide = TestUtils.createGFF3Feature(
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                OntologyTerm.SIGNAL_PEPTIDE.name(),
                Map.of(GFF3Attributes.LOCUS_TAG, "L2"));

        gff3Annotation.setFeatures(List.of(cds, peptide));

        Assertions.assertDoesNotThrow(() -> attributeSpecificValidation.validatePeptideFeature(gff3Annotation, 3));
    }

    @Test
    public void testValidatePeptideFeatureSameGeneOverlapShouldBeValidated() {
        GFF3Feature cds = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(),
                OntologyTerm.CDS.name(),
                Map.of(
                        GFF3Attributes.GENE, "geneA",
                        GFF3Attributes.PSEUDO, "true"));

        GFF3Feature peptide = TestUtils.createGFF3Feature(
                OntologyTerm.TRANSIT_PEPTIDE.name(),
                OntologyTerm.TRANSIT_PEPTIDE.name(),
                Map.of(GFF3Attributes.GENE, "geneA"));

        gff3Annotation.setFeatures(List.of(cds, peptide));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class, () -> attributeSpecificValidation.validatePeptideFeature(gff3Annotation, 4));

        Assertions.assertTrue(ex.getMessage().contains("Peptide feature"));
    }
}
