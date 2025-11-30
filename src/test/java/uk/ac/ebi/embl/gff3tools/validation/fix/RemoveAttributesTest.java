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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class RemoveAttributesTest {

    GFF3Feature feature;

    private RemoveAttributes removeAttributes;

    @BeforeEach
    public void setUp() {
        removeAttributes = new RemoveAttributes();
    }

    @Test
    public void testRemovesCitationAndCompareWhenNotOldSequence() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.CITATION, List.of("PubMed:12345"));
        attributes.put(GFF3Attributes.COMPARE, List.of("comp1"));
        attributes.put(GFF3Attributes.NOTE, List.of("example"));

        feature = TestUtils.createGFF3Feature("gene", "gene", attributes);

        removeAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertNull(feature.getAttributeByName(GFF3Attributes.CITATION));
        Assertions.assertNull(feature.getAttributeByName(GFF3Attributes.COMPARE));
        Assertions.assertEquals("example", feature.getAttributeByName("note"));
    }

    @Test
    public void testKeepsAttributesForOldSequenceFeature() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.CITATION, List.of("PubMed:12345"));
        attributes.put(GFF3Attributes.COMPARE, List.of("comp1"));

        feature = TestUtils.createGFF3Feature("old_sequence", "old_sequence", attributes);

        removeAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertEquals("PubMed:12345", feature.getAttributeByName(GFF3Attributes.CITATION));
        Assertions.assertEquals("comp1", feature.getAttributeByName(GFF3Attributes.COMPARE));
    }

    @Test
    public void testOldSequenceCaseInsensitive() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.CITATION, List.of("PubMed:9999"));
        attributes.put(GFF3Attributes.COMPARE, List.of("cmp"));

        feature = TestUtils.createGFF3Feature("Old_Sequence", "Old_Sequence", attributes);

        removeAttributes.fixFeature(feature, 1);

        Assertions.assertEquals("PubMed:9999", feature.getAttributeByName(GFF3Attributes.CITATION));
        Assertions.assertEquals("cmp", feature.getAttributeByName(GFF3Attributes.COMPARE));
    }

    @Test
    public void testFeatureWithoutCitationOrCompare() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.NOTE, List.of("no citation or compare"));

        feature = TestUtils.createGFF3Feature("gene", "gene", attributes);

        removeAttributes.fixFeature(feature, 1);

        Assertions.assertEquals("no citation or compare", feature.getAttributeByName("note"));
    }

    @Test
    public void testFeatureWithEmptyAttributes() {
        Map<String, List<String>> attributes = new HashMap<>();
        feature = TestUtils.createGFF3Feature("gene", "gene", attributes);

        removeAttributes.fixFeature(feature, 1);

        Assertions.assertTrue(feature.getAttributes().isEmpty());
    }
}
