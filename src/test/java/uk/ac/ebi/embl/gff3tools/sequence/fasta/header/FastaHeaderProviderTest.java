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

class FastaHeaderProviderTest {

    private FastaHeader createHeader(String description) {
        FastaHeader h = new FastaHeader();
        h.setDescription(description);
        h.setMoleculeType("genomic DNA");
        h.setTopology("linear");
        return h;
    }

    @Test
    void emptyProviderReturnsEmpty() {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        assertTrue(provider.getHeader("any").isEmpty());
    }

    @Test
    void singleFileSourceReturnsHeaderForKnownId() {
        FastaHeader h = createHeader("Test organism");
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h)));

        assertEquals("Test organism", provider.getHeader("seq1").orElseThrow().getDescription());
        assertTrue(provider.getHeader("unknown").isEmpty());
    }

    @Test
    void cliSourceReturnsHeaderForAnyId() {
        FastaHeader h = createHeader("CLI header");
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new CliFastaHeaderSource(h));

        assertEquals("CLI header", provider.getHeader("anyId").orElseThrow().getDescription());
        assertEquals("CLI header", provider.getHeader("otherId").orElseThrow().getDescription());
    }

    @Test
    void fileSourceTakesPrecedenceOverCliSource() {
        FastaHeader fileHeader = createHeader("From FASTA");
        FastaHeader cliHeader = createHeader("From CLI");

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", fileHeader)));
        provider.addSource(new CliFastaHeaderSource(cliHeader));

        // seq1 found in FASTA -> FASTA header wins
        assertEquals("From FASTA", provider.getHeader("seq1").orElseThrow().getDescription());

        // seq2 not in FASTA -> falls through to CLI
        assertEquals("From CLI", provider.getHeader("seq2").orElseThrow().getDescription());
    }

    @Test
    void multipleFileSourcesFirstMatchWins() {
        FastaHeader h1 = createHeader("Source 1");
        FastaHeader h2 = createHeader("Source 2");

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h1)));
        provider.addSource(new FileFastaHeaderSource(Map.of("seq1", h2, "seq2", h2)));

        // seq1 found in first source -> first source wins
        assertEquals("Source 1", provider.getHeader("seq1").orElseThrow().getDescription());
        // seq2 only in second source -> second source
        assertEquals("Source 2", provider.getHeader("seq2").orElseThrow().getDescription());
    }
}
