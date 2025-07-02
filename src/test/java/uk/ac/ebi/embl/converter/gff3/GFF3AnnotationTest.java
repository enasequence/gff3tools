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
}
