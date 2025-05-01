package uk.ac.ebi.embl.converter.gff3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.TestUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GFF3AnnotationTest {
    @Test
    public void testWriteAttributes() throws IOException {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("ID", "ID_TEST");
        attributes.put("qualifier", "test_1");
        String expectedAttribute = "ID=ID_TEST;qualifier=test_1;";
        test(attributes, expectedAttribute);

        attributes = new HashMap<>();
        attributes.put("ID", "ID_TEST");
        attributes.put("qualifier", "test_2;value");
        expectedAttribute = "ID=ID_TEST;qualifier=test_2;value;";
        test(attributes, expectedAttribute);

        attributes = new HashMap<>();
        attributes.put("ID", "ID_TEST");
        attributes.put("qualifier1", Arrays.asList("test_1", "test_2", "test_3"));
        attributes.put("qualifier2", Arrays.asList("1", "2", "3"));
        expectedAttribute = "ID=ID_TEST;qualifier1=test_1;qualifier1=test_2;qualifier1=test_3;qualifier2=1;qualifier2=2;qualifier2=3;";
        test(attributes, expectedAttribute);

    }

    private void test(Map<String, Object> attributes, String expectedAttribute) throws IOException {
        try(StringWriter gff3Writer = new StringWriter()){
            GFF3Annotation annotation = new GFF3Annotation();
            GFF3Feature gff3Feature = TestUtils.createGFF3Feature("ID","Parent",attributes);
            annotation.addFeature(gff3Feature);
            annotation.writeGFF3String(gff3Writer);
            assertTrue(gff3Writer.toString().trim().endsWith(expectedAttribute));
        }
    }
}
