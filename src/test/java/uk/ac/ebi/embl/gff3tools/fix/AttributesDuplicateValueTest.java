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
package uk.ac.ebi.embl.gff3tools.fix;

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

public class AttributesDuplicateValueTest {

    GFF3Feature feature;

    private AttributesDuplicateValue attributesDuplicateValueFix;

    @BeforeEach
    public void setUp() {
        attributesDuplicateValueFix = new AttributesDuplicateValue();
    }

    @Test
    public void testFeatureOrAttributesNull() {
        assertNull(attributesDuplicateValueFix.fixFeature(null));

        GFF3Feature f = TestUtils.createGFF3Feature("propeptide", "propeptide", null);
        assertNotNull(attributesDuplicateValueFix.fixFeature(f));
    }

    @Test
    public void testAttributeDuplicateValueNoOldLocusTag() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, "123123");

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertEquals(1, feature.getAttributes().size());
    }

    @Test
    public void testAttributeDuplicateValueNoDuplicates() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, "123123");
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, "123122,123121");

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertEquals(2, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }

    @Test
    public void testAttributeDuplicateValueWithOldLocusTagDuplicates() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, "123123");
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, "123123,123123");

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertNull(feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG));
    }

    @Test
    public void testAttributeDuplicateValueWithLocusAndOldLocusTagDuplicates() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, "LOCUS123");
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, "LOCUS123,123122,123121");

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals(2, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }

    @Test
    public void testOldLocusTagAsList() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, "LOCUS999");
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, List.of("LOCUS999", "LOCUS888", "LOCUS888"));

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature);

        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals(1, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }

    @Test
    public void testOldLocusTagWithSpacesAndEmptyValues() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, "LOC123");
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, " , ,LOC123, ,OLD1,,OLD2, ");

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature);

        Assertions.assertNotNull(feature);
        String oldLocusTag = feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG);
        Assertions.assertEquals("OLD1,OLD2", oldLocusTag);
    }

    @Test
    public void testOldLocusTagSingleValue() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, "LOC123");
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, "LOC124");

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature);

        Assertions.assertNotNull(feature);
        String oldLocusTag = feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG);
        Assertions.assertEquals("LOC124", oldLocusTag);
    }
}
