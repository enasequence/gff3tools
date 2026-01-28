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

import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.PSEUDOGENE;

import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
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
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(PSEUDOGENE, List.of("'processed_pseudogene'"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Optional<List<String>> optValues = feature.getAttributeList(PSEUDOGENE);
        Assertions.assertTrue(optValues.isPresent());
        List<String> values = optValues.get();
        Assertions.assertEquals(1, values.size());
        Assertions.assertEquals("processed_pseudogene", values.get(0));
    }

    @Test
    public void testFixFeatureRemovesQuotesFromPseudogeneAtPrefix() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(PSEUDOGENE, List.of("'processed_pseudogene"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Optional<List<String>> optValues = feature.getAttributeList(PSEUDOGENE);
        Assertions.assertTrue(optValues.isPresent());
        List<String> values = optValues.get();
        Assertions.assertEquals(1, values.size());
        Assertions.assertEquals("processed_pseudogene", values.get(0));
    }

    @Test
    public void testFixFeatureRemovesQuotesFromPseudogeneAtSuffix() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(PSEUDOGENE, List.of("processed_pseudogene'"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Optional<List<String>> optValues = feature.getAttributeList(PSEUDOGENE);
        Assertions.assertTrue(optValues.isPresent());
        List<String> values = optValues.get();
        Assertions.assertEquals(1, values.size());
        Assertions.assertEquals("processed_pseudogene", values.get(0));
    }

    @Test
    public void testFixFeatureRemovesMultipleQuotesFromPseudogene() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(PSEUDOGENE, List.of("'''''processed_pseudogene'''"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Optional<List<String>> optValues = feature.getAttributeList(PSEUDOGENE);
        Assertions.assertTrue(optValues.isPresent());
        List<String> values = optValues.get();
        Assertions.assertEquals(1, values.size());
        Assertions.assertEquals("processed_pseudogene", values.get(0));
    }

    @Test
    public void testFixFeatureNoQuotesFromPseudogene() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(PSEUDOGENE, List.of("processed_pseudogene"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        pseudogeneFix.fixFeature(feature, 1);
        Optional<List<String>> optValues = feature.getAttributeList(PSEUDOGENE);
        Assertions.assertTrue(optValues.isPresent());
        List<String> values = optValues.get();
        Assertions.assertEquals(1, values.size());
        Assertions.assertEquals("processed_pseudogene", values.get(0));
    }
}
