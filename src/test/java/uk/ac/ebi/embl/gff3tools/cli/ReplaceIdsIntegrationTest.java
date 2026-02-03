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
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class ReplaceIdsIntegrationTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    public void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void testFullCommandFlowWithFile() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");
        Path tempOutput = Files.createTempFile("output", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n"
                        + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n"
                        + "BN000066.2\tENA\tgene\t200\t600\t.\t-\t.\tID=gene2\n");

        String[] args = new String[] {
            "process", "replace-ids", "--accessions", "ACC123,ACC456", "-o", tempOutput.toString(), tempInput.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(0));
        }

        String output = Files.readString(tempOutput);
        assertTrue(output.contains("ACC123"));
        assertTrue(output.contains("ACC456"));
        assertFalse(output.contains("BN000065.1"));

        Files.deleteIfExists(tempInput);
        Files.deleteIfExists(tempOutput);
    }

    @Test
    public void testStdinStdoutFlow() throws IOException {
        String input = "##gff-version 3\n"
                + "##sequence-region BN000065.1 1 5000\n"
                + "BN000065.1\tENA\tgene\t100\t500\t.\t+\t.\tID=gene1\n";

        Path tempInput = Files.createTempFile("input", ".gff3");
        Files.writeString(tempInput, input);

        // Simulate: replace-ids --accessions ACC123 input.gff3 (output to stdout)
        String[] args = new String[] {"process", "replace-ids", "--accessions", "ACC123", tempInput.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(0));
        }

        String output = outContent.toString();
        assertTrue(output.contains("ACC123"));
        assertFalse(output.contains("BN000065.1"));

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testAccessionCountMismatchExitCode() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");

        Files.writeString(
                tempInput,
                "##gff-version 3\n"
                        + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        String[] args = new String[] {"process", "replace-ids", "--accessions", "ACC123", tempInput.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            // Should exit with USAGE error
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        }

        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Accession count mismatch"));

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testEmptyAccessionExitCode() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");

        Files.writeString(tempInput, "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n");

        String[] args = new String[] {"process", "replace-ids", "--accessions", "", tempInput.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        }

        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("empty or blank"));

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testInvalidGFF3ExitCode() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");

        // Invalid GFF3: no header
        Files.writeString(tempInput, "##sequence-region BN000065.1 1 5000\n");

        String[] args = new String[] {"process", "replace-ids", "--accessions", "ACC123", tempInput.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.VALIDATION_ERROR.asInt()));
        }

        Files.deleteIfExists(tempInput);
    }

    @Test
    public void testHelpCommand() {
        String[] args = new String[] {"process", "replace-ids", "--help"};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(0));
        }

        String output = outContent.toString();
        assertTrue(output.contains("replace-ids"));
        assertTrue(output.contains("--accessions"));
    }

    @Test
    public void testMissingAccessions() throws IOException {
        Path tempInput = Files.createTempFile("input", ".gff3");

        Files.writeString(tempInput, "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n");

        String[] args = new String[] {"process", "replace-ids", tempInput.toString()};

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        }

        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("Missing required option") || errorOutput.contains("--accessions"));

        Files.deleteIfExists(tempInput);
    }
}
