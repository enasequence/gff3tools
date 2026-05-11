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
package uk.ac.ebi.embl.gff3tools.sequence.fasta.header;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;

class FileFastaHeaderSourceTest {

    @Test
    void returnsHeaderForKnownSeqId() {
        FastaHeader h = new FastaHeader();
        h.setDescription("Test");
        FileFastaHeaderSource source = new FileFastaHeaderSource(Map.of("seq1", h));

        assertTrue(source.getHeader("seq1").isPresent());
        assertEquals("Test", source.getHeader("seq1").get().getDescription());
    }

    @Test
    void returnsEmptyForUnknownSeqId() {
        FastaHeader h = new FastaHeader();
        h.setDescription("Test");
        FileFastaHeaderSource source = new FileFastaHeaderSource(Map.of("seq1", h));

        assertTrue(source.getHeader("unknown").isEmpty());
    }

    @Test
    void emptyMapReturnsEmpty() {
        FileFastaHeaderSource source = new FileFastaHeaderSource(Map.of());
        assertTrue(source.getHeader("any").isEmpty());
    }
}
