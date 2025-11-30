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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;

public class GFF3AnnotationTest {
    @Test
    public void testWriteAttributes() throws IOException, WriteException {

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("ID", List.of("ID_TEST"));
        attributes.put("qualifier", List.of("test_1"));
        String expectedAttribute = "ID=ID_TEST;qualifier=test_1;";
        test(attributes, expectedAttribute);

        attributes = new HashMap<>();
        attributes.put("ID", List.of("ID_TEST"));
        attributes.put("qualifier", List.of("test_2;value"));
        expectedAttribute = "ID=ID_TEST;qualifier=test_2%3Bvalue;";
        test(attributes, expectedAttribute);

        attributes = new HashMap<>();
        attributes.put("ID", List.of("ID_TEST"));
        attributes.put("qualifier1", Arrays.asList("test_1", "test_2", "test_3"));
        attributes.put("qualifier2", Arrays.asList("1", "2", "3"));
        expectedAttribute = "ID=ID_TEST;qualifier1=test_1,test_2,test_3;qualifier2=1,2,3;";
        test(attributes, expectedAttribute);
    }

    private void test(Map<String, List<String>> attributes, String expectedAttribute)
            throws IOException, WriteException {
        try (StringWriter gff3Writer = new StringWriter()) {
            GFF3Annotation annotation = new GFF3Annotation();
            GFF3Feature gff3Feature = TestUtils.createGFF3Feature("ID", "Parent", attributes);
            annotation.addFeature(gff3Feature);
            annotation.writeGFF3String(gff3Writer);
            String attr = gff3Writer.toString().trim();
            assertTrue(attr.endsWith(expectedAttribute));
        }
    }

    @Test
    public void testMergeAnnotations() throws IOException, WriteException {
        // Test case 1: Merge annotations where the first annotation has no sequence region
        GFF3Annotation annotation1 = new GFF3Annotation();
        annotation1.addFeature(TestUtils.createGFF3Feature("ID1", "Parent1", new HashMap<>() {
            {
                put("ID", List.of("feature1"));
            }
        }));

        GFF3Annotation annotation2 = new GFF3Annotation();
        annotation2.setSequenceRegion(new GFF3SequenceRegion("ACC00002", Optional.empty(), 1, 200));
        annotation2.addFeature(TestUtils.createGFF3Feature("ID2", "Parent2", new HashMap<>() {
            {
                put("ID", List.of("feature2"));
            }
        }));

        annotation1.merge(annotation2);

        assertNotNull(annotation1.getSequenceRegion());
        assertEquals("ACC00002", annotation1.getSequenceRegion().accession());
        assertEquals(2, annotation1.getFeatures().size());
        assertEquals("ID1", annotation1.getFeatures().get(0).id.get());
        assertEquals("ID2", annotation1.getFeatures().get(1).id.get());

        // Test case 2: Merge annotations where both have sequence regions
        GFF3Annotation annotation3 = new GFF3Annotation();
        annotation3.setSequenceRegion(new GFF3SequenceRegion("ACC00003", Optional.empty(), 1, 300));
        annotation3.addFeature(TestUtils.createGFF3Feature("ID3", "Parent3", new HashMap<>() {
            {
                put("ID", List.of("feature3"));
            }
        }));

        GFF3Annotation annotation4 = new GFF3Annotation();
        annotation4.setSequenceRegion(new GFF3SequenceRegion("ACC00004", Optional.empty(), 1, 400));
        annotation4.addFeature(TestUtils.createGFF3Feature("ID4", "Parent4", new HashMap<>() {
            {
                put("ID", List.of("feature4"));
            }
        }));

        annotation3.merge(annotation4);

        assertNotNull(annotation3.getSequenceRegion());
        assertEquals("ACC00003", annotation3.getSequenceRegion().accession()); // Should retain original
        assertEquals(2, annotation3.getFeatures().size());
        assertEquals("ID3", annotation3.getFeatures().get(0).id.get());
        assertEquals("ID4", annotation3.getFeatures().get(1).id.get());

        // Test case 3: Merge with empty second annotation
        GFF3Annotation annotation5 = new GFF3Annotation();
        annotation5.setSequenceRegion(new GFF3SequenceRegion("ACC00005", Optional.empty(), 1, 500));
        annotation5.addFeature(TestUtils.createGFF3Feature("ID5", "Parent5", new HashMap<>() {
            {
                put("ID", List.of("feature5"));
            }
        }));

        GFF3Annotation annotation6 = new GFF3Annotation(); // Empty

        annotation5.merge(annotation6);

        assertNotNull(annotation5.getSequenceRegion());
        assertEquals("ACC00005", annotation5.getSequenceRegion().accession());
        assertEquals(1, annotation5.getFeatures().size());
        assertEquals("ID5", annotation5.getFeatures().get(0).id.get());

        // Test case 4: Merge into empty first annotation
        GFF3Annotation annotation7 = new GFF3Annotation(); // Empty

        GFF3Annotation annotation8 = new GFF3Annotation();
        annotation8.setSequenceRegion(new GFF3SequenceRegion("ACC00008", Optional.empty(), 1, 800));
        annotation8.addFeature(TestUtils.createGFF3Feature("ID8", "Parent8", new HashMap<>() {
            {
                put("ID", List.of("feature8"));
            }
        }));

        annotation7.merge(annotation8);

        assertNotNull(annotation7.getSequenceRegion());
        assertEquals("ACC00008", annotation7.getSequenceRegion().accession());
        assertEquals(1, annotation7.getFeatures().size());
        assertEquals("ID8", annotation7.getFeatures().get(0).id.get());
    }

    @Test
    public void testGetAccession() throws IOException, WriteException {
        // Test case 1: Accession from GFF3SequenceRegion directive
        GFF3Annotation annotation1 = new GFF3Annotation();
        annotation1.setSequenceRegion(new GFF3SequenceRegion("ACC00001", Optional.empty(), 1, 100));
        assertEquals("ACC00001", annotation1.getAccession());

        // Test case 2: Accession from the first feature when no GFF3SequenceRegion directive
        GFF3Annotation annotation2 = new GFF3Annotation();
        GFF3Feature feature2 = new GFF3Feature(
                Optional.of("feature_id_2"),
                Optional.empty(),
                "ACC00002", // Accession
                Optional.empty(),
                "source",
                "type",
                1,
                100,
                ".",
                "+",
                ".",
                new HashMap<>());
        annotation2.addFeature(feature2);
        assertEquals("ACC00002", annotation2.getAccession());

        // Test case 3: No accession (empty annotation)
        GFF3Annotation annotation3 = new GFF3Annotation();
        assertThrows(RuntimeException.class, annotation3::getAccession);
    }
}
