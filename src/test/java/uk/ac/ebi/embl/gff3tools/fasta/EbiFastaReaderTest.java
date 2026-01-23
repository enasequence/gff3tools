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
package uk.ac.ebi.embl.gff3tools.fasta;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;

public class EbiFastaReaderTest {

    @Test
    void doesNotTolerateImproperHeaders() throws IOException {
        // by improper headers, i mean ones not in the EBI spec*/
        File fasta = TestUtils.getResourceFile("./fasta/fasta_improper_header.txt");
        List<String> accessionIds = List.of("acc1");

        assertThrows(FastaHeaderParserException.class, () -> new EbiFastaReader(fasta, accessionIds));
    }
}
