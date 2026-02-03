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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class CountRegionsIntegrationTest {

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
    public void testFullCommandFlow() throws IOException {
        Path tempFile = Files.createTempFile("test", ".gff3");
        Files.writeString(
                tempFile,
                "##gff-version 3\n" + "##sequence-region BN000065.1 1 5000\n"
                        + "##sequence-region BN000066.2 1 3000\n");

        String[] args = new String[] {"process", "count-regions", tempFile.toString()};

        int exitCode = new CommandLine(new Main()).execute(args);
        assertEquals(0, exitCode);
        assertEquals("2", outContent.toString().trim());

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testWithRealGFF3File() throws IOException {
        // Use the demo file from test resources if available
        Path demoFile = Path.of("src/test/resources/demo/OZ026791.gff3");
        if (Files.exists(demoFile)) {
            String[] args = new String[] {"process", "count-regions", demoFile.toString()};

            int exitCode = new CommandLine(new Main()).execute(args);
            assertEquals(0, exitCode);
            // OZ026791.gff3 has 1 sequence region
            assertEquals("1", outContent.toString().trim());
        }
    }

    @Test
    public void testNonExistentFile() {
        String[] args = new String[] {"process", "count-regions", "non_existent_file.gff3"};

        int exitCode = new CommandLine(new Main()).execute(args);
        assertNotEquals(0, exitCode);
    }

    @Test
    public void testHelpCommand() {
        String[] args = new String[] {"process", "count-regions", "--help"};

        int exitCode = new CommandLine(new Main()).execute(args);
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("count-regions"));
        assertTrue(outContent.toString().contains("Counts the number of sequence regions"));
    }
}
