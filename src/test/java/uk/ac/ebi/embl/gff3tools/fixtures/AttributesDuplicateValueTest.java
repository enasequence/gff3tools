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
    public void testAttributeDuplicateValueFixtureTest() {
        feature = TestUtils.createGFF3Feature(
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                GFF3Anthology.PROPETIDE_FEATURE_NAME,
                Map.of(GFF3Attributes.LOCUS_TAG, "123123", GFF3Attributes.OLD_LOCUS_TAG, "123123,123122,123121"));
        feature = attributesDuplicateValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        Assertions.assertNotNull(feature.getAttributes());
        assertEquals(2, feature.getAttributeByName(GFF3Attributes.OLD_LOCUS_TAG).split(",").length);
    }
}
