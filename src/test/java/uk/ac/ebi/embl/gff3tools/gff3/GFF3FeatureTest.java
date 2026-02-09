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
package uk.ac.ebi.embl.gff3tools.gff3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class GFF3FeatureTest {

    @Test
    public void testGFF3FeatureFivePrimePartialTrue() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PARTIAL, List.of("start"));
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertTrue(feature.isFivePrimePartial());
        assertFalse(feature.isThreePrimePartial());
    }

    @Test
    public void testGFF3FeatureFivePrimePartialFalse() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PARTIAL, List.of("start,end"));
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertFalse(feature.isFivePrimePartial());
        assertFalse(feature.isThreePrimePartial());
    }

    @Test
    public void testGFF3FeatureThreePrimePartialTrue() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PARTIAL, List.of("end"));
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertTrue(feature.isThreePrimePartial());
        assertFalse(feature.isFivePrimePartial());
    }

    @Test
    public void testGFF3FeatureThreePrimePartialFalse() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PARTIAL, List.of("end,start"));
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertFalse(feature.isThreePrimePartial());
        assertFalse(feature.isFivePrimePartial());
    }

    @Test
    public void testGFF3FeaturePartialFalse() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PARTIAL, List.of("start,end"));
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertFalse(feature.isThreePrimePartial());
        assertFalse(feature.isFivePrimePartial());
    }

    @Test
    public void testGFF3FeatureFivePrimePartialCaseSensitive() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PARTIAL, List.of("START"));
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertTrue(feature.isFivePrimePartial());
        assertFalse(feature.isThreePrimePartial());
    }

    @Test
    public void testGFF3FeatureThreePartialCaseSensitive() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PARTIAL, List.of("END"));
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertFalse(feature.isFivePrimePartial());
        assertTrue(feature.isThreePrimePartial());
    }

    @Test
    public void testGFF3FeatureTestSettingPartiality() {
        Map<String, List<String>> attributes = new HashMap<>();
        GFF3Feature feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), attributes);
        assertFalse(feature.isFivePrimePartial());
        assertFalse(feature.isThreePrimePartial());

        feature.setFivePrimePartial();
        feature.setThreePrimePartial();

        assertTrue(feature.isFivePrimePartial());
        assertTrue(feature.isThreePrimePartial());
    }
}
