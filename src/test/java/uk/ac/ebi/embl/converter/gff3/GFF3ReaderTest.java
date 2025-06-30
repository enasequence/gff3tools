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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.TestUtils;
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3FileReader;

public class GFF3ReaderTest {
    @Test
    void canParseAllExamples() throws Exception {
        Map<String, Path> testFiles = TestUtils.getTestFiles("fftogff3_rules", ".gff3");

        for (String filePrefix : testFiles.keySet()) {
            File file = new File(testFiles.get(filePrefix).toUri());
            FileReader filerReader = new FileReader(file);
            BufferedReader reader = new BufferedReader(filerReader);
            GFF3FileReader gff3Reader = new GFF3FileReader(reader);
            try {
                gff3Reader.readHeader();
                while (true) {
                    if (gff3Reader.readAnnotation() == null) break;
                }
            } catch (Exception e) {
                fail(String.format("Error parsing file: %s", filePrefix), e);
            }
        }
    }

    @Test
    void testMissingHeader() throws Exception {
        File testFile = TestUtils.getResourceFile("validation_errors/empty_file.gff3");

        FileReader filerReader = new FileReader(testFile);
        BufferedReader reader = new BufferedReader(filerReader);
        GFF3FileReader gff3Reader = new GFF3FileReader(reader);
        try {
            gff3Reader.readHeader();
        } catch (InvalidGFF3HeaderException e) {
            Assertions.assertTrue(e.getMessage().contains("GFF3 header not found"));
            Assertions.assertEquals(1, e.getLine());
            return;
        }
        fail(String.format("Expected exception when parsing file: %s", testFile.getPath()));
    }

    @Test
    void testAttributesFromAndToString() throws Exception {

        test("ID=ID_TEST;qualifier1=test_1;qualifier2=test_2;");
        test("ID=ID_TEST;qualifier1=test_1,test_2,test_3;");
        test("ID=ID_TEST;qualifier1=test_1,test_3;qualifier2=test_2;");
        test("ID=ID_TEST;qualifier1=test_1,test_3;");
        test("ID=ID_TEST;qualifier1=%00%09%25%3B%2C;");
    }

    private void test(String attributeLine) throws Exception {
        GFF3FileReader gff3Reader = new GFF3FileReader(new StringReader(attributeLine));
        Map<String, Object> attrMap = gff3Reader.attributesFromString(attributeLine);

        assertEquals(attributeLine, getAttributeString(attrMap));
    }

    private String getAttributeString(Map<String, Object> attributes) throws WriteException, IOException {
        try (StringWriter gff3Writer = new StringWriter()) {
            GFF3Annotation annotation = new GFF3Annotation();
            GFF3Feature gff3Feature = TestUtils.createGFF3Feature("ID", "Parent", attributes);
            annotation.addFeature(gff3Feature);
            annotation.writeGFF3String(gff3Writer);

            // retutn only attributes
            return gff3Writer.toString().split("\t")[8].trim();
        }
    }
}
