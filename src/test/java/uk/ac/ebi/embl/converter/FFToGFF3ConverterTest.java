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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.converter.fftogff3.*;
import uk.ac.ebi.embl.converter.gff3.GFF3File;
import uk.ac.ebi.embl.converter.gff3.GFF3Sequence;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;

class FFToGFF3ConverterTest {

  @Test
  void testWriteGFF3() throws Exception {

    Map<String, Path> testFiles = TestUtils.getTestFiles("fftogff3_rules");

    for (String filePrefix : testFiles.keySet()) {
      FFGFF3FileFactory rule = new FFGFF3FileFactory();
      try (BufferedReader testFileReader =
          TestUtils.getResourceReader(testFiles.get(filePrefix).toString())) {
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);
        EmblEntryReader entryReader =
            new EmblEntryReader(
                testFileReader, EmblEntryReader.Format.EMBL_FORMAT, "", readerOptions);
        Writer gff3Writer = new StringWriter();
        GFF3File gff3 = rule.from(entryReader);
        gff3.writeGFF3String(gff3Writer);

        String expected;
        String expectedFilePath = testFiles.get(filePrefix).toString().replace(".embl", ".gff3");
        try (BufferedReader gff3TestFileReader = TestUtils.getResourceReader(expectedFilePath)) {
          expected = new BufferedReader(gff3TestFileReader).lines().collect(Collectors.joining("\n"));
        }

        assertEquals(
                expected.trim(), gff3Writer.toString().trim(), "Error on test case: " + filePrefix);
        gff3Writer.close();
      }
    }
  }
}
