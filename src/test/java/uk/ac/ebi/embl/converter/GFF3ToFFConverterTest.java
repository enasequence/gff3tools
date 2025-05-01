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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.cli.Params;
import uk.ac.ebi.embl.converter.fftogff3.FFtoGFF3ConversionError;
import uk.ac.ebi.embl.converter.gff3toff.Gff3ToFFConverter;

class GFF3ToFFConverterTest {

    @Test
    void testWriteGFF3() throws Exception {

        Map<String, Path> testFiles = TestUtils.getTestFiles("gff3toff_rules", ".gff3");

        for (String filePrefix : testFiles.keySet()) {
            Gff3ToFFConverter converter = new Gff3ToFFConverter();
            try {

                String inFile = testFiles.get(filePrefix).toString();
                String outFile = filePrefix + ".embl";
                String[] args = {"-in", inFile, "-out", outFile};
                Params params = Params.parse(args);
                converter.convert(params);

                String expected;
                String expectedFilePath = inFile.replace(".gff3", ".embl");
                try (BufferedReader emblTestFileReader = TestUtils.getResourceReader(expectedFilePath)) {
                    expected = new BufferedReader(emblTestFileReader).lines().collect(Collectors.joining("\n"));
                }

                assertEquals(
                        expected.trim(),
                        Files.readString(Paths.get(outFile)).trim(),
                        "Error on test case: " + filePrefix);
                Files.deleteIfExists(Paths.get(outFile));
            } catch (FFtoGFF3ConversionError e) {
                // throw e;
                fail("Error on test case: " + filePrefix + " - " + e.getMessage());
            }
        }
    }
}
