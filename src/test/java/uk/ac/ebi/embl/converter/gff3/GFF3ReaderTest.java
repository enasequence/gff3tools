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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.converter.TestUtils;
import uk.ac.ebi.embl.converter.gff3.reader.GFF3FileReader;

public class GFF3ReaderTest {
    @Test
    void canParseAllExamples() throws Exception {
        Map<String, Path> testFiles = TestUtils.getTestFiles("fftogff3_rules", ".gff3");

        for (String filePrefix : new String[] {"gene_mrna_parents"}) {
            File file = new File(testFiles.get(filePrefix).toUri());
            FileReader filerReader = new FileReader(file);
            BufferedReader reader = new BufferedReader(filerReader);
            GFF3FileReader gff3Reader = new GFF3FileReader(reader);
            try {
                GFF3File gff3File = gff3Reader.read();
                assertNotNull(gff3File);
            } catch (Exception e) {
                fail(String.format("Error parsing file: %s", filePrefix), e);
            }
        }
    }
}
