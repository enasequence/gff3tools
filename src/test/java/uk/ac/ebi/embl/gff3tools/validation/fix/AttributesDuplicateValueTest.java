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

import java.util.ArrayList;
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
        assertNull(attributesDuplicateValueFix.fixFeature(null, 0));

        GFF3Feature f = TestUtils.createGFF3Feature("propeptide", "propeptide", null);
        assertNotNull(attributesDuplicateValueFix.fixFeature(f, 0));
    }

    @Test
    public void testAttributeDuplicateValueNoOldLocusTag() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, List.of("123123"));

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertEquals(1, feature.getAttributes().size());
    }

    @Test
    public void testAttributeDuplicateValueNoDuplicates() {
        List<String> oldLocusTag = new ArrayList<>();
        oldLocusTag.add("123122");
        oldLocusTag.add("123121");
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, List.of("123123"));
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, oldLocusTag);

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertEquals(
                2,
                feature.getAttributeListByName(GFF3Attributes.OLD_LOCUS_TAG)
                        .get()
                        .size());
    }

    @Test
    public void testAttributeDuplicateValueWithOldLocusTagDuplicates() {
        List<String> oldLocusTag = new ArrayList<>();
        oldLocusTag.add("123123");
        oldLocusTag.add("123123");

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, List.of("123123"));
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, oldLocusTag);

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertTrue(feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).isEmpty());
    }

    @Test
    public void testAttributeDuplicateValueWithLocusAndOldLocusTagDuplicates() {
        List<String> oldLocusTag = new ArrayList<>();
        oldLocusTag.add("LOCUS123");
        oldLocusTag.add("123122");
        oldLocusTag.add("123121");

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, List.of("LOCUS123"));
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, oldLocusTag);

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals(
                2,
                feature.getAttributeListByName(GFF3Attributes.OLD_LOCUS_TAG)
                        .get()
                        .size());
    }

    @Test
    public void testOldLocusTagAsList() {
        List<String> oldLocusTag = new ArrayList<>();
        oldLocusTag.add("LOCUS999");
        oldLocusTag.add("LOCUS888");
        oldLocusTag.add("LOCUS888");

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, List.of("LOCUS999"));
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, oldLocusTag);

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals(
                1,
                feature.getAttributeListByName(GFF3Attributes.OLD_LOCUS_TAG)
                        .get()
                        .size());
    }

    @Test
    public void testOldLocusTagWithSpacesAndEmptyValues() {
        List<String> oldLocusTag = new ArrayList<>();
        oldLocusTag.add(" ");
        oldLocusTag.add(" ");
        oldLocusTag.add("LOC123");
        oldLocusTag.add("");
        oldLocusTag.add("OLD1");
        oldLocusTag.add("OLD2");

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, List.of("LOC123"));
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, oldLocusTag);

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        List<String> updatedOldLocusTag =
                feature.getAttributeListByName(GFF3Attributes.OLD_LOCUS_TAG).get();
        assertEquals(2, updatedOldLocusTag.size());
        Assertions.assertTrue(updatedOldLocusTag.contains("OLD1") && updatedOldLocusTag.contains("OLD2"));
    }

    @Test
    public void testOldLocusTagSingleValue() {
        List<String> oldLocusTag = new ArrayList<>();
        oldLocusTag.add("LOC124");

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.LOCUS_TAG, List.of("LOC123"));
        attributes.put(GFF3Attributes.OLD_LOCUS_TAG, oldLocusTag);

        feature = TestUtils.createGFF3Feature("propeptide", "propeptide", attributes);
        feature = attributesDuplicateValueFix.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        List<String> updatedOldLocusTag =
                feature.getAttributeListByName(GFF3Attributes.OLD_LOCUS_TAG).get();
        assertEquals(1, updatedOldLocusTag.size());
        Assertions.assertTrue(updatedOldLocusTag.contains("LOC124"));
    }
}
