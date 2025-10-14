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
package uk.ac.ebi.embl.gff3tools.fixtures;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.fixture.AttributesDuplicateValue;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Anthology;
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
    public void testAttributeDuplicateValueNoOldLocusTag() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(GFF3Attributes.LOCUS_TAG, "123123"));
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertEquals(1, feature.getAttributes().size());
    }

    @Test
    public void testAttributeDuplicateValueNoDuplicates() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(GFF3Attributes.LOCUS_TAG, "123123", GFF3Attributes.OLD_LOCUS_TAG, "123122,123121"));
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertEquals(2, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }

    @Test
    public void testAttributeDuplicateValueWithOldLocusTagDuplicates() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(GFF3Attributes.LOCUS_TAG, "123123", GFF3Attributes.OLD_LOCUS_TAG, "123123,123123"));
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals("123123", feature.getAttributeByName(GFF3Attributes.LOCUS_TAG));
        assertEquals(1, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }

    @Test
    public void testAttributeDuplicateValueWithLocusAndOldLocusTagDuplicates() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(GFF3Attributes.LOCUS_TAG, "LOCUS123", GFF3Attributes.OLD_LOCUS_TAG, "LOCUS123,123122,123121"));
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals(2, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }

    @Test
    public void testOldLocusTagAsList() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(
                        GFF3Attributes.LOCUS_TAG,
                        "LOCUS999",
                        GFF3Attributes.OLD_LOCUS_TAG,
                        List.of("LOCUS999", "LOCUS888", "LOCUS888")));
        feature = attributesDuplicateValueFix.fixFeature(feature);

        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals(1, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }

    @Test
    public void testOldLocusTagWithSpacesAndEmptyValues() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(
                        GFF3Attributes.LOCUS_TAG, "LOC123",
                        GFF3Attributes.OLD_LOCUS_TAG, " , ,LOC123, ,OLD1,,OLD2, "));
        feature = attributesDuplicateValueFix.fixFeature(feature);

        Assertions.assertNotNull(feature);
        String oldLocusTag = feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG);
        Assertions.assertEquals("OLD1,OLD2", oldLocusTag);
    }

    @Test
    public void testOldLocusTagSingleValue() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(
                        GFF3Attributes.LOCUS_TAG, "LOC123",
                        GFF3Attributes.OLD_LOCUS_TAG, "LOC124"));
        feature = attributesDuplicateValueFix.fixFeature(feature);

        Assertions.assertNotNull(feature);
        String oldLocusTag = feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG);
        Assertions.assertEquals("LOC124", oldLocusTag);
    }
}
