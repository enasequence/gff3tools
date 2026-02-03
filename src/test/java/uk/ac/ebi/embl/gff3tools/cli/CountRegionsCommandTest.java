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

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class CountRegionsCommandTest {

    private CountRegionsCommand command;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final InputStream originalIn = System.in;

    @BeforeEach
    public void setUp() {
        command = new CountRegionsCommand();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    @Test
    public void testCountZeroRegions() throws IOException {
        // GFF3 file with no sequence regions
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile, "##gff-version 3\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("0", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountOneRegion() throws IOException {
        // GFF3 file with one sequence region
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile, "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("1", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountMultipleRegions() throws IOException {
        // GFF3 file with multiple sequence regions
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                tempFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n"
                        + "##sequence-region BN000067 1 2000\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("3", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountRegionsWithFeatures() throws IOException {
        // GFF3 file with sequence regions and features
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                tempFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
                        + "##sequence-region BN000066.2 1 3000\n"
                        + "BN000066.2\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountRegionsStopsAtFASTA() throws IOException {
        // GFF3 file with FASTA section - should only count regions before FASTA
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                tempFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n"
                        + "##FASTA\n"
                        + ">seq1\n"
                        + "ATGCATGC\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountRegionsWithBlankLines() throws IOException {
        // GFF3 file with blank lines (should be ignored)
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                tempFile,
                "##gff-version 3\n" + "\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testInvalidGFF3NoHeader() throws IOException {
        // Invalid GFF3 file - no version directive
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile, "##sequence-region BN000065.1 1 5000\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertThrows(RuntimeException.class, () -> command.run());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testNonExistentFile() {
        String[] args = new String[] {"non_existent_file.gff3"};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertThrows(RuntimeException.class, () -> command.run());
    }

    @Test
    public void testCountRegionsWithVersionNumbers() throws IOException {
        // Test both with and without version numbers
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                tempFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066 1 3000\n"
                        + "##sequence-region BN000067.12 1 2000\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("3", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testCountRegionsFromStdin() {
        // Test reading from stdin
        String gff3Content =
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n" + "##sequence-region BN000066.2 1 3000\n";

        System.setIn(new ByteArrayInputStream(gff3Content.getBytes()));

        // Empty path triggers stdin reading
        command.inputFilePath = Path.of("");

        assertDoesNotThrow(() -> command.run());
        assertEquals("2", outContent.toString().trim());
    }

    @Test
    public void testCountRegionsWithCommentsBeforeHeader() throws IOException {
        // Test that single-# comments are allowed before the version directive
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                tempFile,
                "# This is a comment\n" + "# Another comment\n" + "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        assertDoesNotThrow(() -> command.run());
        assertEquals("1", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testEmptyFile() throws IOException {
        // Test completely empty file
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile, "");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        // Should fail with validation error
        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("no ##gff-version directive found"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testDirectiveBeforeVersionHeader() throws IOException {
        // Test that directives before version header are rejected
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(tempFile, "##sequence-region seq1 1 100\n" + "##gff-version 3\n");

        String[] args = new String[] {tempFile.toString()};
        CommandLine commandLine = new CommandLine(command);
        commandLine.parseArgs(args);

        // Should fail with validation error
        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("directive found before ##gff-version"));

        Files.deleteIfExists(tempFile);
    }
}
