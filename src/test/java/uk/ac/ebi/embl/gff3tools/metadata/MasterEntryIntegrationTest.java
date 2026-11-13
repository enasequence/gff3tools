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
package uk.ac.ebi.embl.gff3tools.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.cli.Main;

class MasterEntryIntegrationTest {

    @TempDir
    Path tempDir;

    private Path getResourcePath(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        assertNotNull(resource, "Resource not found: " + resourceName);
        return Paths.get(resource.getPath());
    }

    private int executeCommand(String... args) {
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }

    @Test
    void gff3ToEmbl_withMasterEntryJson_succeeds() throws Exception {
        Path gff3 = tempDir.resolve("test.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                """);

        Path masterJson = getResourcePath("metadata/master_entry.json");
        Path outFile = tempDir.resolve("output.embl");

        int exitCode = executeCommand(
                "conversion", "--master-entry", masterJson.toString(), gff3.toString(), outFile.toString());

        assertEquals(0, exitCode, "Conversion with --master-entry JSON should succeed");
        assertTrue(Files.exists(outFile), "Output file should be created");

        String content = Files.readString(outFile);
        // Verify key fields from the MasterEntry JSON are in the EMBL output
        assertTrue(content.contains("Homo sapiens genome assembly"), "Should contain description");
        assertTrue(content.contains("genomic DNA"), "Should contain molecule type");
        assertTrue(content.contains("Homo sapiens"), "Should contain scientific name");
        assertTrue(content.contains("PRJEB12345"), "Should contain project accession");
        assertTrue(content.contains("HUM"), "Should contain division");
        assertTrue(content.contains("BioSample"), "Should contain publication cross-reference");
        assertTrue(content.contains("Submitted"), "Should contain submission reference");
    }

    @Test
    void gff3ToEmbl_withMasterEntryEmbl_succeeds() throws Exception {
        Path gff3 = tempDir.resolve("test.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                """);

        Path masterEmbl = getResourcePath("fftogff3_rules/reduced/master.embl");
        Path outFile = tempDir.resolve("output.embl");

        int exitCode = executeCommand(
                "conversion", "--master-entry", masterEmbl.toString(), gff3.toString(), outFile.toString());

        assertEquals(0, exitCode, "Conversion with --master-entry EMBL should succeed");
        assertTrue(Files.exists(outFile), "Output file should be created");

        // reduced/master.embl carries a WGS line, so it is a WGS set master: the converted
        // entry must get contig-level treatment (dataClass WGS, KW WGS., SET cross-refs).
        String content = Files.readString(outFile);
        assertTrue(content.contains("; WGS; INV; 1000 BP."), "ID line with WGS data class and per-contig length");
        assertTrue(content.contains("KW   WGS."), "WGS keyword added");
        assertTrue(content.contains("DR   ENA; CAXMMS010000000; SET."), "master accession cross-ref");
        assertTrue(content.contains("DR   ENA; CAXMMS000000000; SET."), "root set cross-ref");
    }

    @Test
    void gff3ToEmbl_wgsSetMasterEmbl_matchesJsonMasterContigFields() throws Exception {
        Path gff3 = getResourcePath("cli-examples/wgs/wgs_contig.gff3");
        Path masterJson = getResourcePath("cli-examples/wgs/wgs_master.json");
        Path masterEmbl = getResourcePath("cli-examples/wgs/wgs_master.embl");
        Path outJson = tempDir.resolve("from_json.embl");
        Path outEmbl = tempDir.resolve("from_embl.embl");

        assertEquals(
                0,
                executeCommand(
                        "conversion", "--master-entry", masterJson.toString(), gff3.toString(), outJson.toString()));
        assertEquals(
                0,
                executeCommand(
                        "conversion", "--master-entry", masterEmbl.toString(), gff3.toString(), outEmbl.toString()));

        // Contig-level fields derived from the WGS set master must be identical for both master formats.
        List<String> fromJson = wgsContigLines(outJson);
        List<String> fromEmbl = wgsContigLines(outEmbl);
        assertEquals(fromJson, fromEmbl);
        assertTrue(fromEmbl.stream().anyMatch(l -> l.startsWith("ID") && l.contains("; WGS;")), "ID data class WGS");
        assertTrue(fromEmbl.contains("KW   WGS."));
        assertTrue(fromEmbl.contains("DR   ENA; CAXMYH010000000; SET."));
        assertTrue(fromEmbl.contains("DR   ENA; CAXMYH000000000; SET."));
    }

    private static List<String> wgsContigLines(Path embl) throws Exception {
        return Files.readAllLines(embl).stream()
                .filter(l -> l.startsWith("ID") || l.startsWith("KW") || (l.startsWith("DR") && l.endsWith("SET.")))
                .toList();
    }

    @Test
    void emblToGff3_withMasterEntryJson_succeeds() throws Exception {
        Path emblInput = getResourcePath("fftogff3_rules/reduced/scaffold-reduced.embl");
        Path masterJson = getResourcePath("metadata/master_entry.json");
        Path outFile = tempDir.resolve("output.gff3");

        int exitCode = executeCommand(
                "conversion", "--master-entry", masterJson.toString(), emblInput.toString(), outFile.toString());

        assertEquals(0, exitCode, "EMBL->GFF3 conversion with --master-entry JSON should succeed");
        assertTrue(Files.exists(outFile), "Output file should be created");

        String content = Files.readString(outFile);
        // Should contain species directive from the master entry
        assertTrue(content.contains("##species"), "Should contain ##species directive");
        assertTrue(content.contains("9606"), "Should use taxon ID from master entry");
    }

    @Test
    void emblToGff3_withMasterEntryEmbl_backwardCompatible() throws Exception {
        // This test verifies backward compatibility: using -m with an EMBL master file
        // should produce the same output as before
        Path scaffoldPath = getResourcePath("fftogff3_rules/reduced/scaffold-reduced.embl");
        Path masterPath = getResourcePath("fftogff3_rules/reduced/master.embl");
        Path expectedPath = getResourcePath("fftogff3_rules/reduced/scaffold-reduced-expected.gff3");
        Path outFile = tempDir.resolve("output.gff3");

        int exitCode =
                executeCommand("conversion", "-m", masterPath.toString(), scaffoldPath.toString(), outFile.toString());

        assertEquals(0, exitCode, "Backward-compatible -m EMBL conversion should succeed");
        assertTrue(Files.exists(outFile), "Output file should be created");

        String expected = Files.readString(expectedPath).trim();
        String actual = Files.readString(outFile).trim();
        assertEquals(expected, actual, "Output should match expected GFF3 for backward compatibility");
    }
}
