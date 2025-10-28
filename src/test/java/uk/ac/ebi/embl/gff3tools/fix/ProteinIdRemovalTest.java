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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;

public class ProteinIdRemovalTest {

    GFF3Annotation gff3Annotation;

    private ProteinIdRemoval proteinIdRemoval;

    @BeforeEach
    public void setUp() {
        gff3Annotation = new GFF3Annotation();
        proteinIdRemoval = new ProteinIdRemoval();
    }

    @Test
    public void testProteinIdRemovalSuccess() {
        Map<String, Object> a1 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, "");
        Map<String, Object> a2 = new HashMap<>();
        a1.put(GFF3Attributes.LOCUS_TAG, "LOCUS_TAG");
        Map<String, Object> a3 = new HashMap<>();
        a1.put(GFF3Attributes.PROTEIN_ID, "proteinId");
        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), a1);
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), a2);
        GFF3Feature f3 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), a3);
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        proteinIdRemoval.fixAnnotation(gff3Annotation);

        Assertions.assertEquals(3, gff3Annotation.getFeatures().size());

        gff3Annotation
                .getFeatures()
                .forEach(feature -> Assertions.assertNull(feature.getAttributeByName(GFF3Attributes.PROTEIN_ID)));
    }

    @Test
    public void testProteinIdRemovalWithoutProteinId() {
        Map<String, Object> a1 = new HashMap<>();
        a1.put(GFF3Attributes.ATTRIBUTE_ID, "1");
        Map<String, Object> a2 = new HashMap<>();
        a1.put(GFF3Attributes.ATTRIBUTE_ID, "2");
        Map<String, Object> a3 = new HashMap<>();
        a1.put(GFF3Attributes.ATTRIBUTE_ID, "3");

        GFF3Feature f1 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), a1);
        GFF3Feature f2 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), a2);
        GFF3Feature f3 = TestUtils.createGFF3Feature(OntologyTerm.CDS.name(), OntologyTerm.CDS.name(), a3);
        gff3Annotation.setFeatures(List.of(f1, f2, f3));

        proteinIdRemoval.fixAnnotation(gff3Annotation);

        Assertions.assertEquals(3, gff3Annotation.getFeatures().size());

        gff3Annotation
                .getFeatures()
                .forEach(feature -> Assertions.assertNull(feature.getAttributeByName(GFF3Attributes.PROTEIN_ID)));
    }
}
