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
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class PseudogeneFixTest {

    GFF3Feature feature;

    private PseudogeneFix pseudogeneFix;

    @BeforeEach
    public void setUp() {
        pseudogeneFix = new PseudogeneFix();
    }

    @Test
    public void testFixFeatureRemovesQuotesFromPseudogene() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PSEUDOGENE, "'processed_pseudogene'");

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertEquals("processed_pseudogene", feature.getAttributeByName(GFF3Attributes.PSEUDOGENE));
    }

    @Test
    public void testFixFeatureRemovesQuotesFromPseudogeneAtPrefix() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PSEUDOGENE, "'processed_pseudogene");

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertEquals("processed_pseudogene", feature.getAttributeByName(GFF3Attributes.PSEUDOGENE));
    }

    @Test
    public void testFixFeatureRemovesQuotesFromPseudogeneAtSuffix() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PSEUDOGENE, "processed_pseudogene'");

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertEquals("processed_pseudogene", feature.getAttributeByName(GFF3Attributes.PSEUDOGENE));
    }

    @Test
    public void testFixFeatureRemovesMultipleQuotesFromPseudogene() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PSEUDOGENE, "'''''processed_pseudogene'''");

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertEquals("processed_pseudogene", feature.getAttributeByName(GFF3Attributes.PSEUDOGENE));
    }

    @Test
    public void testFixFeatureNoQuotesFromPseudogene() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PSEUDOGENE, "processed_pseudogene");

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Assertions.assertNotNull(feature);
        Assertions.assertEquals("processed_pseudogene", feature.getAttributeByName(GFF3Attributes.PSEUDOGENE));
    }
}
