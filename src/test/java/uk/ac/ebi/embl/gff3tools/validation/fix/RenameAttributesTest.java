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

import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class RenameAttributesTest {

    GFF3Feature feature;

    private RenameAttributes renameAttributes;

    @BeforeEach
    public void setUp() {
        renameAttributes = new RenameAttributes();
    }

    @Test
    public void testFixFeatureRenameLabelAttributeWithoutLabel() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.CITATION, List.of("PubMed:12345"));
        attributes.put(GFF3Attributes.COMPARE, List.of("comp1"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);

        renameAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertEquals(2, feature.getAttributes().size());
    }

    @Test
    public void testFixFeatureRenameLabelAttributeWithLabel() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.CITATION, List.of("PubMed:12345"));
        attributes.put(GFF3Attributes.LABEL, List.of("labTest"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);

        renameAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertEquals(2, feature.getAttributes().size());
        Assertions.assertEquals(
                "label:labTest", feature.getAttribute(GFF3Attributes.NOTE).get());
    }

    @Test
    public void testFixFeatureRenameLabelAttributeWithLabelAndNote() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.CITATION, new ArrayList<>(Arrays.asList("PubMed:12345")));
        attributes.put(GFF3Attributes.LABEL, new ArrayList<>(Arrays.asList("labTest")));
        attributes.put(GFF3Attributes.NOTE, new ArrayList<>(Arrays.asList("notes")));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);

        renameAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertEquals(2, feature.getAttributes().size());
        Assertions.assertEquals(
                "notes;label:labTest", feature.getAttribute(GFF3Attributes.NOTE).get());
    }

    @Test
    public void testFixFeatureRenameLabelAttributeWithNote() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.CITATION, List.of("PubMed:12345"));
        attributes.put(GFF3Attributes.NOTE, List.of("notes"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);

        renameAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertEquals(2, feature.getAttributes().size());
        Assertions.assertEquals(
                "notes", feature.getAttribute(GFF3Attributes.NOTE).get());
    }

    @Test
    public void testFixFeatureRenameLabelAttributeOnMobileElement() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.MOBILE_ELEMENT, List.of("mobile_element:12345"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);

        renameAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertEquals(1, feature.getAttributes().size());
        Assertions.assertTrue(
                feature.getAttribute(GFF3Attributes.MOBILE_ELEMENT).isEmpty());
        Assertions.assertEquals(
                "mobile_element:12345",
                feature.getAttribute(GFF3Attributes.MOBILE_ELEMENT_TYPE).get());
    }

    @Test
    public void testFixFeatureRenameLabelAttributeOnMobileElementType() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.MOBILE_ELEMENT_TYPE, List.of("mobile_element_type"));
        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);

        renameAttributes.fixFeature(feature, 1);

        Assertions.assertNotNull(feature);
        Assertions.assertEquals(1, feature.getAttributes().size());
        Assertions.assertEquals(
                "mobile_element_type",
                feature.getAttribute(GFF3Attributes.MOBILE_ELEMENT_TYPE).get());
    }
}
