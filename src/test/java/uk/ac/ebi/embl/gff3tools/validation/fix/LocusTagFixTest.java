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
import static uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes.LOCUS_TAG;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class LocusTagFixTest {

    GFF3Feature feature;

    private LocusTagFix locusTagFix;

    @BeforeEach
    public void setUp() {
        locusTagFix = new LocusTagFix();
    }

    @Test
    public void testNoLocusTag() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(GFF3Attributes.PROTEIN_ID, List.of("protein_id"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        locusTagFix.fixFeature(feature, 1);
        assertNotNull(feature);
    }

    @Test
    public void testLocusTagLowerCase() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(LOCUS_TAG, List.of("locus_tag"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        locusTagFix.fixFeature(feature, 1);
        List<String> locusTags = feature.getAttributeList(LOCUS_TAG).orElse(new ArrayList<>());
        assertNotNull(feature);
        for (String s : locusTags) {
            assertEquals(s.toUpperCase(Locale.ROOT), s);
        }
    }

    @Test
    public void testLocusTagUpperCase() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(LOCUS_TAG, List.of("LOCUS_TAG"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        locusTagFix.fixFeature(feature, 1);
        List<String> locusTags = feature.getAttributeList(LOCUS_TAG).orElse(new ArrayList<>());
        assertNotNull(feature);
        for (String s : locusTags) {
            assertEquals(s.toUpperCase(Locale.ROOT), s);
        }
    }

    @Test
    public void testLocusTagUpperCase_Numeric() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(LOCUS_TAG, List.of("1231_12"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        locusTagFix.fixFeature(feature, 1);
        List<String> locusTags = feature.getAttributeList(LOCUS_TAG).orElse(new ArrayList<>());
        assertNotNull(feature);
        for (String s : locusTags) {
            assertEquals(s.toUpperCase(Locale.ROOT), s);
        }
    }

    @Test
    public void testLocusTagUpperCase_AlphaNumeric() {
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put(LOCUS_TAG, List.of("locusID1231_12"));

        feature = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), attributes);
        locusTagFix.fixFeature(feature, 1);
        List<String> locusTags = feature.getAttributeList(LOCUS_TAG).orElse(new ArrayList<>());
        assertNotNull(feature);
        for (String s : locusTags) {
            assertEquals(s.toUpperCase(Locale.ROOT), s);
        }
    }
}
