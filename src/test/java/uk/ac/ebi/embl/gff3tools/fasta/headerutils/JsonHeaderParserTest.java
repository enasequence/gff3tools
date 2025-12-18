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
package uk.ac.ebi.embl.gff3tools.fasta.headerutils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.fasta.Topology;

public class JsonHeaderParserTest {

    private final JsonHeaderParser parser = new JsonHeaderParser();

    // ---------------------------------------------------------
    // VALID CASES
    // ---------------------------------------------------------

    @Test
    void parsesStandardHeaderWithJson() {
        String line =
                ">AF123456.1 | { \"description\":\"Pinus sativa\", \"molecule_type\":\"genomic\", \"topology\":\"circular\" }";

        ParsedHeader ph = assertDoesNotThrow(() -> parser.parse(line));
        assertEquals("AF123456.1", ph.getId());

        FastaHeader h = ph.getHeader();
        assertEquals("Pinus sativa", h.getDescription());
        assertEquals("genomic", h.getMoleculeType());
        assertEquals(Topology.CIRCULAR, h.getTopology());
        assertEquals(null, h.getChromosomeType());
        assertEquals(null, h.getChromosomeLocation());
        assertEquals(null, h.getChromosomeName());
    }

    @Test
    void picksFirstTokenAsIdEvenWithExtraStuff() {
        String line = ">AF123456.1   extra tokens here   | "
                + " {\"description\":\"x\", \"molecule_type\":\"dna\", \"topology\":\"linear\"}";

        ParsedHeader ph = assertDoesNotThrow(() -> parser.parse(line));
        assertEquals("AF123456.1", ph.getId());
    }

    @Test
    void parsesCurlyQuotesAndWeirdSpacingInKeys() {
        String line =
                ">ID1 | { \u201Cdescription\u201D: \u201CPinus\u201D,  \u201C molecule_type\u201D:\"genomic\", \u201Ctopology\u201D:\"CIRCULAR\" }";

        ParsedHeader ph = assertDoesNotThrow(() -> parser.parse(line));
        FastaHeader h = ph.getHeader();

        assertEquals("Pinus", h.getDescription());
        assertEquals("genomic", h.getMoleculeType());
        assertEquals(Topology.CIRCULAR, h.getTopology());
    }

    @Test
    void normalizesKeyVariantsAndChromosomeOptionals() {
        String line = ">ID2 | { \"Description\":\"Desc\", \"molecule-type\":\"rna\", \"topology\":\"linear\", "
                + "\"Chromosome Type\":\"plasmid\", \"chromosome_location\":\"chr12:100-200\", \"CHROMOSOME_NAME\":\"pX\" }";

        ParsedHeader ph = assertDoesNotThrow(() -> parser.parse(line));
        FastaHeader h = ph.getHeader();

        assertEquals("Desc", h.getDescription());
        assertEquals("rna", h.getMoleculeType());
        assertEquals(Topology.LINEAR, h.getTopology());
        assertEquals("plasmid", h.getChromosomeType());
        assertEquals("chr12:100-200", h.getChromosomeLocation());
        assertEquals("pX", h.getChromosomeName());
    }

    @Test
    void handlesNbspInJson() {
        String nbsp = "\u00A0";
        String line = ">ID3 | {" + nbsp
                + "\"description\"" + nbsp + ":" + nbsp + "\"Alpha" + nbsp + "Beta\"" + ","
                + "\"molecule_type\":\"rna\", \"topology\":\"linear\"}";

        ParsedHeader ph = assertDoesNotThrow(() -> parser.parse(line));
        FastaHeader h = ph.getHeader();

        assertEquals("Alpha Beta", h.getDescription());
        assertEquals("rna", h.getMoleculeType());
        assertEquals(Topology.LINEAR, h.getTopology());
    }

    @Test
    void missingJsonIsFine_NoPipe() {
        String line = ">AF999999.5 some label without json";

        ParsedHeader ph = assertDoesNotThrow(() -> parser.parse(line));
        assertEquals("AF999999.5", ph.getId());

        FastaHeader h = ph.getHeader();
        assertNull(h.getDescription());
        assertNull(h.getMoleculeType());
        assertNull(h.getTopology());
    }

    @Test
    void trimsIdAndHandlesJustChevron() {
        ParsedHeader ph1 = assertDoesNotThrow(() -> parser.parse(
                ">   AF111   | {\"description\":\"x\",\"molecule_type\":\"dna\",\"topology\":\"linear\"}"));
        assertEquals("AF111", ph1.getId());

        // No pipe: JSON not required
        ParsedHeader ph2 = assertDoesNotThrow(() -> parser.parse(">"));
        assertEquals("", ph2.getId());
        assertNull(ph2.getHeader().getDescription());
    }

    // ---------------------------------------------------------
    // INVALID CASES — MUST THROW FASTAFIleException
    // ---------------------------------------------------------

    @Test
    void noJsonAfterPipeThrows() {
        String line = ">ID5 |   ";
        assertThrows(FastaFileException.class, () -> parser.parse(line));
    }

    @Test
    void emptyJsonAfterPipeThrows() {
        String line = ">ID5 | {} ";
        assertThrows(FastaFileException.class, () -> parser.parse(line));
    }

    @Test
    void jsonWithNullValuesThrows() {
        String line = ">ID8 | {\"description\":null, \"molecule_type\":null, \"topology\":null}";
        assertThrows(FastaFileException.class, () -> parser.parse(line));
    }

    @Test
    void missingRequiredFieldsThrows() {
        String line = ">ID9 | {\"description\":\"x\"}";
        FastaFileException e = assertThrows(FastaFileException.class, () -> parser.parse(line));
        assertTrue(e.getMessage().contains("missing required"));
    }

    @Test
    void unknownTopologyThrows() {
        String line = ">ID4 | {\"description\":\"x\", \"molecule_type\":\"dna\", \"topology\":\"banana\"}";
        FastaFileException e = assertThrows(FastaFileException.class, () -> parser.parse(line));
        assertTrue(e.getMessage().contains("topology"));
    }

    // ---------------------------------------------------------
    // MALFORMED JSON
    // ---------------------------------------------------------

    @Test
    void malformedJsonThrowsAndIncludesJsonInMessage() {
        String badJson = "{\"description\": \"x\", \"molecule_type\": \"genomic\", OOPS }";
        String line = ">ID6 | " + badJson;

        FastaFileException e = assertThrows(FastaFileException.class, () -> parser.parse(line));
        assertTrue(e.getMessage().contains("OOPS"));
        assertTrue(e.getMessage().contains("{\"description"));
    }

    @Test
    void malformedJsonBracesThrowsAndIncludesJsonInMessage() {
        String badJson = "{\"description\": \"x\", \"molecule_type\": \"genomic\", OOPS }";
        String line = ">ID6 | " + badJson;

        FastaFileException e = assertThrows(FastaFileException.class, () -> parser.parse(line));
        assertTrue(e.getMessage().contains("OOPS"));
        assertTrue(e.getMessage().contains("{\"description"));
    }

    @Test
    void malformedJsonWithTrailingCommaThrowsAndMentionsComma() {
        String badJson = "{ \"description\":\"y\", \"molecule_type\":\"genomic\", }";
        String line = ">ID7 | " + badJson;

        FastaFileException e = assertThrows(FastaFileException.class, () -> parser.parse(line));
        assertTrue(e.getMessage().contains("\"description\":\"y\""));
        assertTrue(e.getMessage().contains("\"molecule_type\":\"genomic\""));
    }
}
