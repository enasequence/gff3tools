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
        feature = TestUtils.createGFF3Feature(
                "gene",
                "gene",
                Map.of(GFF3Attributes.CITATION, "PubMed:12345", GFF3Attributes.COMPARE, "comp1", "note", "example"));

        GFF3Feature result = removeAttributes.fixFeature(feature);

        Assertions.assertNotNull(result);
        Assertions.assertNull(result.getAttributeByName(GFF3Attributes.CITATION));
        Assertions.assertNull(result.getAttributeByName(GFF3Attributes.COMPARE));
        Assertions.assertEquals("example", result.getAttributeByName("note"));
    }

    @Test
    public void testKeepsAttributesForOldSequenceFeature() {
        feature = TestUtils.createGFF3Feature(
                "old_sequence",
                "old_sequence",
                Map.of(
                        GFF3Attributes.CITATION, "PubMed:12345",
                        GFF3Attributes.COMPARE, "comp1"));

        GFF3Feature result = removeAttributes.fixFeature(feature);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("PubMed:12345", result.getAttributeByName(GFF3Attributes.CITATION));
        Assertions.assertEquals("comp1", result.getAttributeByName(GFF3Attributes.COMPARE));
    }

    @Test
    public void testOldSequenceCaseInsensitive() {
        feature = TestUtils.createGFF3Feature(
                "Old_Sequence",
                "Old_Sequence",
                Map.of(
                        GFF3Attributes.CITATION, "PubMed:9999",
                        GFF3Attributes.COMPARE, "cmp"));

        GFF3Feature result = removeAttributes.fixFeature(feature);

        Assertions.assertEquals("PubMed:9999", result.getAttributeByName(GFF3Attributes.CITATION));
        Assertions.assertEquals("cmp", result.getAttributeByName(GFF3Attributes.COMPARE));
    }

    @Test
    public void testFeatureWithoutCitationOrCompare() {
        feature = TestUtils.createGFF3Feature("gene", "gene", Map.of("note", "no citation or compare"));

        GFF3Feature result = removeAttributes.fixFeature(feature);

        Assertions.assertEquals("no citation or compare", result.getAttributeByName("note"));
    }

    @Test
    public void testFeatureWithEmptyAttributes() {
        feature = TestUtils.createGFF3Feature("gene", "gene", Map.of());

        GFF3Feature result = removeAttributes.fixFeature(feature);

        Assertions.assertTrue(result.getAttributes().isEmpty());
    }
}
