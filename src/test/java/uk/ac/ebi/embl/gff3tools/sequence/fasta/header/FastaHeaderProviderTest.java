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

    // --- version-tolerant resolution (TTENA-317) ---

    @Test
    void unversionedQueryResolvesVersionedKey() {
        // FASTA headers are usually keyed by the versioned submission id while the mapper queries
        // the unversioned accession id.
        FastaHeader h = createHeader("From FASTA");
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("OZ375371.1", h)));

        assertEquals("From FASTA", provider.getHeader("OZ375371").orElseThrow().getDescription());
    }

    @Test
    void versionedQueryResolvesUnversionedKey() {
        FastaHeader h = createHeader("From FASTA");
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("OZ375371", h)));

        assertEquals(
                "From FASTA", provider.getHeader("OZ375371.1").orElseThrow().getDescription());
    }

    @Test
    void versionedQueryAboveNineResolvesExactKey() {
        // The mapper queries the versioned accession, so versions of 10 and above must resolve
        // without any tolerance.
        FastaHeader h = createHeader("From FASTA");
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("X.10", h)));

        assertEquals("From FASTA", provider.getHeader("X.10").orElseThrow().getDescription());
        assertTrue(provider.getHeader("X").isEmpty());
    }

    @Test
    void exactMatchInLaterSourceWinsOverVariantInEarlierSource() {
        FastaHeader h1 = createHeader("Source 1");
        FastaHeader h2 = createHeader("Source 2");

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("X.1", h1)));
        provider.addSource(new FileFastaHeaderSource(Map.of("X", h2)));

        // The exact match in the second source wins over the X.1 variant in the first.
        assertEquals("Source 2", provider.getHeader("X").orElseThrow().getDescription());
        assertEquals("Source 1", provider.getHeader("X.1").orElseThrow().getDescription());
    }

    @Test
    void ambiguousVariantAcrossSourcesIsSkipped() {
        FastaHeader h1 = createHeader("Source 1");
        FastaHeader h2 = createHeader("Source 2");

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("X.1", h1)));
        provider.addSource(new FileFastaHeaderSource(Map.of("X.2", h2)));

        // Neither X.1 nor X.2 may silently win for a query of X.
        assertTrue(provider.getHeader("X").isEmpty());
        assertEquals("Source 1", provider.getHeader("X.1").orElseThrow().getDescription());
        assertEquals("Source 2", provider.getHeader("X.2").orElseThrow().getDescription());
    }

    @Test
    void catchAllSourceDoesNotShadowKeyedSources() {
        FastaHeader fileHeader = createHeader("From FASTA");
        FastaHeader cliHeader = createHeader("From CLI");

        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("X.1", fileHeader)));
        provider.addSource(new CliFastaHeaderSource(cliHeader));

        // The versioned FASTA key must resolve before the catch-all CLI header is consulted,
        // for the exact query (X.1) and the variant query (X) alike.
        assertEquals("From FASTA", provider.getHeader("X.1").orElseThrow().getDescription());
        assertEquals("From FASTA", provider.getHeader("X").orElseThrow().getDescription());
        // Unknown ids fall through to the catch-all.
        assertEquals("From CLI", provider.getHeader("unknown").orElseThrow().getDescription());
    }

    @Test
    void nullSeqIdReturnsEmpty() {
        FastaHeaderProvider provider = new FastaHeaderProvider();
        provider.addSource(new FileFastaHeaderSource(Map.of("X", createHeader("From FASTA"))));
        provider.addSource(new CliFastaHeaderSource(createHeader("From CLI")));

        assertTrue(provider.getHeader(null).isEmpty());
    }
}
