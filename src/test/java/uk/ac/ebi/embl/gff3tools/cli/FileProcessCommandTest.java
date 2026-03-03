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
package uk.ac.ebi.embl.gff3tools.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class FileProcessCommandTest {

    @Test
    public void testReplaceHeaderAccession() {
        Map<String, String> accessionMap = Map.of("seq1.1", "CB0001.1", "seq2.1", "CB0002.1");

        assertEquals(">CB0001.1", FileProcessCommand.replaceHeaderAccession(">seq1.1", accessionMap));
        assertEquals(">CB0002.1", FileProcessCommand.replaceHeaderAccession(">seq2.1", accessionMap));
    }

    @Test
    public void testReplaceHeaderAccessionWithSuffix() {
        Map<String, String> accessionMap = Map.of("seq1.1", "CB0001.1");

        assertEquals(">CB0001.1|feature1", FileProcessCommand.replaceHeaderAccession(">seq1.1|feature1", accessionMap));
        assertEquals(
                ">CB0001.1 some desc", FileProcessCommand.replaceHeaderAccession(">seq1.1 some desc", accessionMap));
    }

    @Test
    public void testReplaceHeaderAccessionNoMatch() {
        Map<String, String> accessionMap = Map.of("seq1.1", "CB0001.1");

        assertEquals(">unknown.1", FileProcessCommand.replaceHeaderAccession(">unknown.1", accessionMap));
    }

    @Test
    public void testReplaceHeaderAccessionPartialMatchNotReplaced() {
        Map<String, String> accessionMap = Map.of("seq1.1", "CB0001.1");

        // "seq1.10" should NOT match "seq1.1"
        assertEquals(">seq1.10", FileProcessCommand.replaceHeaderAccession(">seq1.10", accessionMap));
    }
}
