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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * {@code --output-master-entry}: EMBL to GFF3 conversion writing a master entry JSON as a
 * by-product. The fixtures are public INSDC records as served by ENA (A00001: EP patent;
 * HE820743: submission plus thesis).
 */
class MasterEntryOutputTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private int run(String... args) {
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(new StringWriter()));
        command.setOut(new PrintWriter(new StringWriter()));
        return command.execute(args);
    }

    private JsonNode generate(String fixture) throws Exception {
        Path master = tempDir.resolve(fixture + ".master.json");
        int exitCode = run(
                "conversion",
                "-f",
                "embl",
                "-t",
                "gff3",
                "--output-master-entry",
                master.toString(),
                resource("master_output/" + fixture + ".embl").toString(),
                tempDir.resolve(fixture + ".gff3").toString());
        assertEquals(0, exitCode, "conversion failed");
        return MAPPER.readTree(master.toFile());
    }

    @Test
    void patentRecordGivesPatentReferenceAndEntryFields() throws Exception {
        JsonNode master = generate("A00001.1");

        assertEquals("A00001", master.get("accession").asText());
        assertEquals("PAT", master.get("dataClass").asText());
        assertEquals("VRL", master.get("division").asText());
        assertEquals(335, master.get("sequenceLength").asInt());
        assertEquals("10641", master.get("taxon").asText());
        assertEquals("28-JAN-1993", master.get("firstPublic").asText());
        assertEquals(34, master.get("firstPublicRelease").asInt());
        assertEquals("b42f821113c5f1d4fa7ea2b935e74216", master.get("md5").asText());
        // KW "." is empty, and empty collections are left out entirely
        assertFalse(master.has("keywords"));

        JsonNode reference = master.get("references").get(0);
        assertEquals("PATENT", reference.get("type").asText());
        assertEquals("1-335", reference.get("referencePosition").asText());
        assertEquals("Baulcombe", reference.get("authors").get(0).get("surname").asText());
        assertEquals("D.C.", reference.get("authors").get(0).get("firstName").asText());
        JsonNode patent = reference.get("patent");
        assertEquals("EP", patent.get("patentOffice").asText());
        assertEquals("0242016", patent.get("patentNumber").asText());
        assertEquals("A1", patent.get("patentType").asText());
        assertEquals(1, patent.get("sequenceNumber").asInt());
        assertEquals("1987-10-21", patent.get("day").asText());
        assertEquals(
                "AGRICULTURAL GENETICS COMPANY LIMITED",
                patent.get("applicants").get(0).asText());
    }

    @Test
    void submissionAndThesisReferences() throws Exception {
        JsonNode references = generate("HE820743.1").get("references");

        JsonNode submission = references.get(0);
        assertEquals("SUBMISSION", submission.get("type").asText());
        assertEquals(
                "2012-05-25", submission.get("submission").get("submittedDate").asText());
        assertTrue(submission
                .get("submission")
                .get("submitterAddress")
                .asText()
                .startsWith("Molecular Genetics / Seed Development, IPK"));

        JsonNode thesis = references.get(1);
        assertEquals("THESIS", thesis.get("type").asText());
        // the trailing full stop of the RL line is not part of the institute
        assertEquals("IPK Gatersleben", thesis.get("thesis").get("institute").asText());
        assertEquals(2008, thesis.get("thesis").get("year").asInt());
    }

    @Test
    void generatedMasterRoundTripsToTheSameReferenceLines() throws Exception {
        Path master = tempDir.resolve("master.json");
        Path gff3 = tempDir.resolve("HE820743.1.gff3");
        assertEquals(
                0,
                run(
                        "conversion",
                        "-f",
                        "embl",
                        "-t",
                        "gff3",
                        "--output-master-entry",
                        master.toString(),
                        resource("master_output/HE820743.1.embl").toString(),
                        gff3.toString()));

        Path embl = tempDir.resolve("roundtrip.embl");
        assertEquals(
                0,
                run(
                        "conversion",
                        "-f",
                        "gff3",
                        "-t",
                        "embl",
                        "-m",
                        master.toString(),
                        gff3.toString(),
                        embl.toString()));

        String roundTrip = Files.readString(embl).replace("\r\n", "\n");
        assertTrue(roundTrip.contains("RL   Thesis (2008), IPK Gatersleben.\n"), roundTrip);
        assertTrue(roundTrip.contains("RL   Submitted (25-MAY-2012) to the INSDC.\n"), roundTrip);
    }

    @Test
    void optionIsRejectedOutsideEmblToGff3() throws Exception {
        Path master = tempDir.resolve("master.json");
        Path gff3 = tempDir.resolve("in.gff3");
        Files.writeString(gff3, "##gff-version 3\n");

        int exitCode = run(
                "conversion",
                "-f",
                "gff3",
                "-t",
                "embl",
                "--output-master-entry",
                master.toString(),
                gff3.toString(),
                tempDir.resolve("out.embl").toString());

        assertEquals(CLIExitCode.USAGE.asInt(), exitCode);
        assertFalse(Files.exists(master));
    }

    private static Path resource(String name) throws URISyntaxException {
        return Paths.get(
                MasterEntryOutputTest.class.getClassLoader().getResource(name).toURI());
    }
}
