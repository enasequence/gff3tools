package uk.ac.ebi.embl.gff3tools.fasta;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class FASTAFileReaderTest {
/*
    @Test
    void readsExampleAndParsesIdsAndHeaderJson() throws Exception {
        URI uri = Objects.requireNonNull(
                getClass().getClassLoader().getResource("fasta/example.txt"),
                "Test resource fasta/example.txt is missing"
        ).toURI();
        File file = Paths.get(uri).toFile();

        FastaReader reader = new FastaReader();
        List<FastaEntry> records = reader.readFile(file);

        // We expect two records (your two headers), not counting the "NONSENSE" lines.
        assertEquals(2, records.size(), "Should parse two FASTA records");

        // ---- Record 1 ----
        FastaEntry r1 = records.get(0);
        assertEquals("AF123456.1", r1.getId(),
                "Accession should be the first token between '>' and '|' (trimmed)");
        FastaHeader h1 = r1.getHeader();
        assertNotNull(h1, "Header must be present");
        assertEquals("Pinus sativa isolate xyz, complete mitochondrion", h1.getDescription());
        assertEquals("genomic", h1.getMoleculeType());
        assertEquals(Topology.CIRCULAR, h1.getTopology());
        assertTrue(h1.getChromosomeType().isEmpty());
        assertTrue(h1.getChromosomeLocation().isEmpty());
        assertTrue(h1.getChromosomeName().isEmpty());

        // ---- Record 2 ----
        FastaEntry r2 = records.get(1);
        assertEquals("AF123455.2", r2.getId(),
                "Second accession should be parsed the same way");
        FastaHeader h2 = r2.getHeader();
        assertNotNull(h2);
        assertEquals("Pinus sativa isolate xyz, complete mitochondrion", h2.getDescription());
        assertEquals("genomic", h2.getMoleculeType());
        assertEquals(Topology.CIRCULAR, h2.getTopology());
    }
    *?
 */
}
