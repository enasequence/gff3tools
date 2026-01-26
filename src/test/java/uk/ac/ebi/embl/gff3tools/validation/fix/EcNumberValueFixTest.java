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
package uk.ac.ebi.embl.gff3tools.validation.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class EcNumberValueFixTest {

    GFF3Feature feature;

    private EcNumberValueFix ecNumberValueFix;

    @BeforeEach
    public void setUp() {
        ecNumberValueFix = new EcNumberValueFix();
    }

    @Test
    public void testEcNumberValueFixWithoutEcNumber() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PROTEIN_ID, List.of("protein_id"));
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(1, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithoutEcNumberDeleted() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("deleted"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(0, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithEcNumberInvalid() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("123124"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(0, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithEcNumberValidNumeric() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("1.2.3.4"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(1, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithEcNumberValidWithHyphen() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("1.2.3.-"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(1, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithEcNumberInValidWithHyphen() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("1.-.3.-"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(0, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithAlphaNumeric() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("0.2.3.n1"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(1, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithInvalidAlphabets() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("0.2.3.x"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(0, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithInvalidLength() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("0.2.3.1.2."));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(0, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithLeadingDot() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("..0.2.3.1.."));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcNumber(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(0, feature.getAttributeKeys().size());
    }

    @Test
    public void testEcNumberValueFixWithUnknownProduct() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PRODUCT, List.of("unknown", "transfer RNA-leucine", "tRNA-Thr"));
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("..0.2.3.1.."));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcFromProduct(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(1, feature.getAttributeKeys().size());
        assertFalse(feature.hasAttribute(GFF3Attributes.EC_NUMBER));
    }

    @Test
    public void testEcNumberValueFixOnProductWithECNumber() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PRODUCT, List.of("DNA polymerase III, beta subunit; ec=2.7.7.7"));
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcFromProduct(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(2, feature.getAttributeKeys().size());
        assertTrue(feature.hasAttribute(GFF3Attributes.EC_NUMBER));
    }

    @Test
    public void testEcNumberValueFixOnProductWithEcNumber() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PRODUCT, List.of("transfer RNA-leucine", "product [Ec:1.1.1.1]", "tRNA-Thr"));
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcFromProduct(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(2, feature.getAttributeKeys().size());
        assertTrue(feature.hasAttribute(GFF3Attributes.EC_NUMBER));
    }

    @Test
    public void testEcNumberValueFixOnProductWithecNumber() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PRODUCT, List.of("product (ec:1.1.1.1)", "protein"));
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcFromProduct(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(2, feature.getAttributeKeys().size());
        assertTrue(feature.hasAttribute(GFF3Attributes.EC_NUMBER));
    }

    @Test
    public void testEcNumberValueFixRemoveEc() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PRODUCT, List.of("product", "protein"));
        attributes.put(GFF3Attributes.EC_NUMBER, List.of("invalidEc"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        ecNumberValueFix.fixEcFromProduct(feature, 1);
        Assertions.assertNotNull(feature);
        assertEquals(1, feature.getAttributeKeys().size());
        assertFalse(feature.hasAttribute(GFF3Attributes.EC_NUMBER));
    }
}
