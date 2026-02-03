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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class ReplaceIdsCommandTest {

    @Test
    public void testReplaceOneRegion() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123");

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("ACC123\tENA\tgene\t100\t500"));
        assertFalse(output.contains("BN000065"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testReplaceMultipleRegions() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
                        + "BN000066.2\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123", "ACC456");

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("##sequence-region ACC456 1 3000"));
        assertTrue(output.contains("ACC123\tENA\tgene\t100\t500"));
        assertTrue(output.contains("ACC456\tENA\tgene\t200\t600"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testPreservesFastaSection() throws IOException {
        // REVIEW: Good test - verifies FASTA headers are NOT replaced (as per spec)
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
                        + "##FASTA\n"
                        + ">gene1\n"
                        + "ATGCATGC\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123");

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##FASTA"));
        assertTrue(output.contains(">gene1"));
        assertTrue(output.contains("ATGCATGC"));
        // REVIEW: Edge case - what if FASTA header is ">BN000065.1"?
        // Consider: adding a test for that scenario
        // FASTA header should NOT be replaced
        assertFalse(output.contains(">ACC123"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testAccessionCountMismatch_TooFew() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123"); // Only 1 for 2 regions

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("Accession count mismatch"));
        assertTrue(exception.getMessage().contains("file has 2 sequence regions"));
        assertTrue(exception.getMessage().contains("1 accessions were provided"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testAccessionCountMismatch_TooMany() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(tempInput, "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123", "ACC456", "ACC789"); // 3 for 1 region

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("Accession count mismatch"));
        assertTrue(exception.getMessage().contains("file has 1 sequence regions"));
        assertTrue(exception.getMessage().contains("3 accessions were provided"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testEmptyAccession() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(tempInput, "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("empty or blank"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testBlankAccessionInList() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123", " ", "ACC456"); // Blank in middle

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.run());
        assertTrue(exception.getMessage().contains("empty or blank"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testTrimsWhitespaceAroundAccessions() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of(" ACC123 ", " ACC456 "); // With whitespace

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("##sequence-region ACC456 1 3000"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testRemovesVersionFromOriginal() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        // Original has version .1 and .12
        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.12 1 3000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
                        + "BN000066.12\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123", "ACC456");

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        // New accessions should not have versions
        assertTrue(output.contains("##sequence-region ACC123 1 5000"));
        assertTrue(output.contains("##sequence-region ACC456 1 3000"));
        assertTrue(output.contains("ACC123\tENA\tgene\t100\t500"));
        assertTrue(output.contains("ACC456\tENA\tgene\t200\t600"));
        // Old accessions with versions should be gone
        assertFalse(output.contains("BN000065.1"));
        assertFalse(output.contains("BN000066.12"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testInvalidGFF3NoHeader() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(tempInput, "##sequence-region BN000065.1 1 5000\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("ACC123");

        assertThrows(RuntimeException.class, () -> command.run());

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testSequentialMapping() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        // Important: File order is ZZZ, AAA, MMM (not alphabetical)
        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region ZZZ999 1 1000\n"
                        + "##sequence-region AAA111 1 2000\n"
                        + "##sequence-region MMM555 1 3000\n"
                        + "ZZZ999\tENA\tgene\t100\t200\t.\t+\t.\tID=gene1\n"
                        + "AAA111\tENA\tgene\t100\t200\t.\t+\t.\tID=gene2\n"
                        + "MMM555\tENA\tgene\t100\t200\t.\t+\t.\tID=gene3\n");

        ReplaceIdsCommand command = new ReplaceIdsCommand();
        command.inputFilePath = tempInput;
        command.outputFilePath = tempOutput.toString();
        command.accessions = java.util.List.of("FIRST", "SECOND", "THIRD");

        assertDoesNotThrow(() -> command.run());

        String output = Files.readString(tempOutput);
        // First region in file (ZZZ999) -> FIRST
        assertTrue(output.contains("##sequence-region FIRST 1 1000"));
        assertTrue(output.contains("FIRST\tENA\tgene\t100\t200\t.\t+\t.\tID=gene1"));
        // Second region in file (AAA111) -> SECOND
        assertTrue(output.contains("##sequence-region SECOND 1 2000"));
        assertTrue(output.contains("SECOND\tENA\tgene\t100\t200\t.\t+\t.\tID=gene2"));
        // Third region in file (MMM555) -> THIRD
        assertTrue(output.contains("##sequence-region THIRD 1 3000"));
        assertTrue(output.contains("THIRD\tENA\tgene\t100\t200\t.\t+\t.\tID=gene3"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }
}
