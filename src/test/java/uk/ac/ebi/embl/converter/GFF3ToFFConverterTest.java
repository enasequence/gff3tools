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
package uk.ac.ebi.embl.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.fftogff3.FFtoGFF3ConversionError;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.converter.gff3toff.EmblFlatFile;
import uk.ac.ebi.embl.converter.gff3toff.Gff3ToFFConverter;

class GFF3ToFFConverterTest {

    @Test
     void testWriteGFF3() throws Exception {

         Map<String, Path> testFiles = TestUtils.getTestFiles("gff3toff_rules", ".gff3");

         for (String filePrefix : testFiles.keySet()) {
             Gff3ToFFConverter converter = new Gff3ToFFConverter();
             try (BufferedReader testFileReader =
                     TestUtils.getResourceReader(testFiles.get(filePrefix).toString())) {

                 Writer flatFileWriter = new StringWriter();
                 converter.convert(testFileReader, flatFileWriter);

                 String expected;
                 String expectedFilePath = testFiles.get(filePrefix).toString().replace(".gff3", ".embl");
                 try (BufferedReader emblTestFileReader = TestUtils.getResourceReader(expectedFilePath)) {
                     expected = new BufferedReader(emblTestFileReader).lines().collect(Collectors.joining("\n"));
                 }

                 assertEquals(expected.trim(), flatFileWriter.toString().trim(), "Error on test case: " + filePrefix);
                 flatFileWriter.close();
             } catch (FFtoGFF3ConversionError e) {
                 //throw e;
                 fail("Error on test case: " + filePrefix + " - " + e.getMessage());
             }
         }
     }
}
