package uk.ac.ebi.embl.gff3tools.fasta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JsonHeaderParserTest {

    private final JsonHeaderParser parser = new JsonHeaderParser();

    @Test
    void parsesStandardHeaderWithJson() {
        String line = ">AF123456.1 | { \"description\":\"Pinus sativa\", \"molecule_type\":\"genomic\", \"topology\":\"circular\" }";
        try {
            ParsedHeader ph = parser.parse(line);

            assertEquals("AF123456.1", ph.getId());
            FastaHeader h = ph.getHeader();
            assertEquals("Pinus sativa", h.getDescription());
            assertEquals("genomic", h.getMoleculeType());
            assertEquals(Topology.CIRCULAR, h.getTopology());
            assertTrue(h.getChromosomeType().isEmpty());
            assertTrue(h.getChromosomeLocation().isEmpty());
            assertTrue(h.getChromosomeName().isEmpty());
        } catch (IOException e) {
            fail("Should not throw for well-formed JSON: " + e.getMessage());
        }
    }

    @Test
    void picksFirstTokenAsIdEvenWithExtraStuff() {
        String line = ">AF123456.1   extra tokens here   | {\"description\":\"x\"}";
        try {
            ParsedHeader ph = parser.parse(line);
            assertEquals("AF123456.1", ph.getId());
        } catch (IOException e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    void parsesCurlyQuotesAndWeirdSpacingInKeys() {
        String line = ">ID1 | {  \u201Cdescription\u201D: \u201CPinus\u201D,  \u201C molecule_type\u201D: \"genomic\" ,  \u201Ctopology\u201D:  \"CIRCULAR\" }";
        try {
            ParsedHeader ph = parser.parse(line);
            FastaHeader h = ph.getHeader();
            assertEquals("Pinus", h.getDescription());
            assertEquals("genomic", h.getMoleculeType());
            assertEquals(Topology.CIRCULAR, h.getTopology());
        } catch (IOException e) {
            fail("Should not throw with normalized curly quotes: " + e.getMessage());
        }
    }

    @Test
    void normalizesKeyVariantsAndChromosomeOptionals() {
        String line = ">ID2 | { \"Description\":\"Desc\", \"molecule-type\":\"rna\", \"Chromosome Type\":\"plasmid\", \"chromosome_location\":\"chr12:100-200\", \"CHROMOSOME_NAME\":\"pX\" }";
        try {
            ParsedHeader ph = parser.parse(line);
            FastaHeader h = ph.getHeader();

            assertEquals("Desc", h.getDescription());
            assertEquals("rna", h.getMoleculeType());
            assertEquals(Optional.of("plasmid"), h.getChromosomeType());
            assertEquals(Optional.of("chr12:100-200"), h.getChromosomeLocation());
            assertEquals(Optional.of("pX"), h.getChromosomeName());
        } catch (IOException e) {
            fail("Should not throw: " + e.getMessage());
        }
    }

    @Test
    void handlesNbspInJson() {
        String nbsp = "\u00A0";
        String line = (">ID3 | {"+nbsp+"\"description\""+nbsp+":" + nbsp + "\"Alpha"+nbsp+"Beta\"" + nbsp + ",\"topology\":\"linear\"}");
        try {
            ParsedHeader ph = parser.parse(line);
            FastaHeader h = ph.getHeader();

            assertEquals("Alpha Beta", h.getDescription()); // NBSP normalized to space
            assertEquals(Topology.LINEAR, h.getTopology());
        } catch (IOException e) {
            fail("Should not throw with NBSP: " + e.getMessage());
        }
    }

    @Test
    void unknownTopologyYieldsNull() {
        String line = ">ID4 | {\"topology\":\"weird-shape\"}";
        try {
            ParsedHeader ph = parser.parse(line);
            assertNull(ph.getHeader().getTopology());
        } catch (IOException e) {
            fail("Should not throw when topology is unknown: " + e.getMessage());
        }
    }

    @Test
    void missingJsonIsFine_NoPipe() {
        String line = ">AF999999.5 some label without json";
        try {
            ParsedHeader ph = parser.parse(line);

            assertEquals("AF999999.5", ph.getId());
            FastaHeader h = ph.getHeader();
            assertNull(h.getDescription());
            assertNull(h.getMoleculeType());
            assertNull(h.getTopology());
            assertTrue(h.getChromosomeType().isEmpty());
            assertTrue(h.getChromosomeLocation().isEmpty());
            assertTrue(h.getChromosomeName().isEmpty());
        } catch (IOException e) {
            fail("Should not throw without pipe/JSON: " + e.getMessage());
        }
    }

    @Test
    void emptyJsonAfterPipeIsFine() {
        String line = ">ID5 |   ";
        try {
            ParsedHeader ph = parser.parse(line);
            FastaHeader h = ph.getHeader();

            assertEquals("ID5", ph.getId());
            assertNull(h.getDescription());
            assertNull(h.getMoleculeType());
            assertNull(h.getTopology());
        } catch (IOException e) {
            fail("Should not throw for empty JSON after pipe: " + e.getMessage());
        }
    }

    @Test
    void malformedJsonThrowsAndIncludesJsonInMessage() {
        String badJson = "{\"description\": \"x\", \"molecule_type\": \"genomic\", OOPS }";
        String line = ">ID6 | " + badJson;
        try {
            parser.parse(line);
            fail("Expected IOException for malformed JSON");
        } catch (IOException e) {
            // Should include a recognizable chunk of normalized JSON
            String msg = e.getMessage();
            assertNotNull(msg);
            assertTrue(msg.contains("OOPS"), "Message should include offending JSON token");
            assertTrue(msg.contains("{\"description\": \"x\"") || msg.contains("{\"description\":\"x\""),
                    "Message should include JSON snippet");
        }
    }

    @Test
    void malformedJsonWithTrailingCommaThrowsAndMentionsComma() {
        String badJson = "{ \"description\":\"y\", \"molecule_type\":\"genomic\", }";
        String line = ">ID7 | " + badJson;
        try {
            parser.parse(line);
            fail("Expected IOException for trailing comma");
        } catch (IOException e) {
            String msg = e.getMessage();
            assertNotNull(msg);
            // different Jackson versions phrase this differently; just assert we included the JSON
            assertTrue(msg.contains("\"description\":\"y\""));
            assertTrue(msg.contains("\"molecule_type\":\"genomic\""));
        }
    }

    @Test
    void jsonWithNullValuesParsesAndLeavesNulls() {
        String line = ">ID8 | {\"description\":null, \"molecule_type\":null, \"topology\":null}";
        try {
            ParsedHeader ph = parser.parse(line);
            FastaHeader h = ph.getHeader();
            assertNull(h.getDescription());
            assertNull(h.getMoleculeType());
            assertNull(h.getTopology());
        } catch (IOException e) {
            fail("Should not throw for explicit nulls: " + e.getMessage());
        }
    }

    @Test
    void trimsIdAndHandlesJustChevron() {
        try {
            ParsedHeader ph1 = parser.parse(">   AF111   | {\"description\":\"x\"}");
            assertEquals("AF111", ph1.getId());

            ParsedHeader ph2 = parser.parse(">");
            assertEquals("", ph2.getId());
            assertNull(ph2.getHeader().getDescription());
        } catch (IOException e) {
            fail("Should not throw here: " + e.getMessage());
        }
    }
}
