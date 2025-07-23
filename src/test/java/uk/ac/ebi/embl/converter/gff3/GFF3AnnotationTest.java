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
package uk.ac.ebi.embl.converter.gff3;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.TestUtils;
import uk.ac.ebi.embl.converter.exception.WriteException;

public class GFF3AnnotationTest {
    @Test
    public void testWriteAttributes() throws IOException, WriteException {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("ID", "ID_TEST");
        attributes.put("qualifier", "test_1");
        String expectedAttribute = "ID=ID_TEST;qualifier=test_1;";
        test(attributes, expectedAttribute);

        attributes = new HashMap<>();
        attributes.put("ID", "ID_TEST");
        attributes.put("qualifier", "test_2;value");
        expectedAttribute = "ID=ID_TEST;qualifier=test_2%3Bvalue;";
        test(attributes, expectedAttribute);

        attributes = new HashMap<>();
        attributes.put("ID", "ID_TEST");
        attributes.put("qualifier1", Arrays.asList("test_1", "test_2", "test_3"));
        attributes.put("qualifier2", Arrays.asList("1", "2", "3"));
        expectedAttribute = "ID=ID_TEST;qualifier1=test_1,test_2,test_3;qualifier2=1,2,3;";
        test(attributes, expectedAttribute);
    }

    private void test(Map<String, Object> attributes, String expectedAttribute) throws IOException, WriteException {
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
        // Create first annotation
        GFF3Annotation annotation1 = new GFF3Annotation();
        Map<String, Object> attributes1 = new HashMap<>();
        attributes1.put("ID", "feature1");
        GFF3Feature feature1 = TestUtils.createGFF3Feature("ID1", "Parent1", attributes1);
        annotation1.addFeature(feature1);

        // Create second annotation
        GFF3Annotation annotation2 = new GFF3Annotation();
        Map<String, Object> attributes2 = new HashMap<>();
        attributes2.put("ID", "feature2");
        GFF3Feature feature2 = TestUtils.createGFF3Feature("ID2", "Parent2", attributes2);
        annotation2.addFeature(feature2);

        // Merge annotations
        annotation1.merge(annotation2);

        // Assert merged content
        try (StringWriter gff3Writer = new StringWriter()) {
            annotation1.writeGFF3String(gff3Writer);
            String mergedContent = gff3Writer.toString();

            assertTrue(mergedContent.contains("ID1"));
            assertTrue(mergedContent.contains("ID2"));
            assertTrue(mergedContent.contains("feature1"));
            assertTrue(mergedContent.contains("feature2"));
        }
    }

    @Test
    public void testGetAccession() throws IOException, WriteException {
        // Test case 1: Accession from GFF3SequenceRegion directive
        GFF3Annotation annotation1 = new GFF3Annotation();
        annotation1.getDirectives().add(new GFF3Directives.GFF3SequenceRegion("ACC00001", Optional.empty(), 1, 100));
        assertEquals("ACC00001", annotation1.getAccession());

        // Test case 2: Accession from the first feature when no GFF3SequenceRegion directive
        GFF3Annotation annotation2 = new GFF3Annotation();
        GFF3Feature feature2 = new GFF3Feature(
                Optional.of("feature_id_2"),
                Optional.empty(),
                "ACC00002", // Accession
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
        assertNull(annotation3.getAccession());
    }
}
