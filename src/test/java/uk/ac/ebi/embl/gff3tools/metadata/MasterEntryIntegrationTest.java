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
    void mutualExclusion_fastaHeaderAndMasterEntry_failsWithError() throws Exception {
        Path gff3 = tempDir.resolve("test.gff3");
        Files.writeString(
                gff3,
                """
                ##gff-version 3
                ##sequence-region seq1 1 1000
                seq1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1
                """);

        Path headerJson = tempDir.resolve("header.json");
        Files.writeString(headerJson, "{\"description\":\"test\",\"molecule_type\":\"dna\",\"topology\":\"linear\"}");

        Path masterJson = tempDir.resolve("master.json");
        Files.writeString(masterJson, "{\"id\":\"test\"}");

        Path outFile = tempDir.resolve("output.embl");

        // Using both --fasta-header and --master-entry should fail
        int exitCode = executeCommand(
                "conversion",
                "--fasta-header",
                headerJson.toString(),
                "--master-entry",
                masterJson.toString(),
                gff3.toString(),
                outFile.toString());

        assertNotEquals(0, exitCode, "Should fail when both --fasta-header and --master-entry are used");
        assertFalse(Files.exists(outFile), "Output file should not be created");
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
        assertTrue(content.contains("PUBMED"), "Should contain publication cross-reference");
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

    @Test
    void fastaHeaderAlone_backwardCompatible() throws Exception {
        // Ensure --fasta-header alone still works
        Path gff3 = getResourcePath("gff3toff_header_tests/fasta_header_basic.gff3");
        Path headerJson = getResourcePath("sequence/fasta/header_test.json");
        Path expectedEmbl = getResourcePath("gff3toff_header_tests/fasta_header_basic.embl");
        Path outFile = tempDir.resolve("output.embl");

        int exitCode = executeCommand(
                "conversion", "--fasta-header", headerJson.toString(), gff3.toString(), outFile.toString());

        assertEquals(0, exitCode, "--fasta-header alone should succeed");
        assertTrue(Files.exists(outFile), "Output file should be created");

        String expected = Files.readString(expectedEmbl).trim();
        String actual = Files.readString(outFile).trim();
        assertEquals(expected, actual, "EMBL output should match expected for backward compatibility");
    }
}
