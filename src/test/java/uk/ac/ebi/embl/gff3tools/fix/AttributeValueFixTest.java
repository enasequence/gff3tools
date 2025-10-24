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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.MOD_BASE;
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.PROTEIN_ID;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class AttributeValueFixTest {

    GFF3Feature feature;

    private AttributeValueFix attributeValueFix;

    @BeforeEach
    public void setUp() {
        attributeValueFix = new AttributeValueFix();
    }

    @Test
    public void testFixFeatureWithNoModBase() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(PROTEIN_ID, "protein_id"));
        feature = attributeValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        assertEquals(1, feature.getAttributes().size());
        assertEquals("protein_id", feature.getAttributeByName(PROTEIN_ID));
    }

    @Test
    public void testFixFeatureWithModBase() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(PROTEIN_ID, "protein_id", MOD_BASE, "ac4c"));
        feature = attributeValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        assertEquals(2, feature.getAttributes().size());
        assertEquals("protein_id", feature.getAttributeByName(PROTEIN_ID));
        assertEquals("ac4c", feature.getAttributeByName(MOD_BASE));
    }

    @Test
    public void testFixFeatureWithModBaseDihydrouridine() {
        feature = TestUtils.createGFF3Feature(
                OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), Map.of(PROTEIN_ID, "protein_id", MOD_BASE, "d"));
        feature = attributeValueFix.fixFeature(feature);
        Assertions.assertNotNull(feature);
        assertEquals(2, feature.getAttributes().size());
        assertEquals("protein_id", feature.getAttributeByName(PROTEIN_ID));
        assertEquals("dhu", feature.getAttributeByName(MOD_BASE));
    }
}
