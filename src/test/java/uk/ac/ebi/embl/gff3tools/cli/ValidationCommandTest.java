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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

public class ValidationCommandTest {

    private ValidationCommand validationCommand;

    @BeforeEach
    public void setUp() {
        validationCommand = new ValidationCommand();
    }

    @Test
    public void testSuccessfulValidation() throws IOException {
        // Simulate a successful validation scenario
        Path tempFile = Files.createTempFile("testFile", ".gff3");
        Files.writeString(tempFile, "# comment\n##gff-version 3\n"); // Add valid GFF3 content here

        String[] args = new String[] {"%s".formatted(tempFile.toString())};

        CommandLine commandLine =
                new CommandLine(validationCommand).registerConverter(CliRulesOption.class, new RuleConverter());
        assertDoesNotThrow(() -> commandLine.parseArgs(args));

        assertDoesNotThrow(() -> validationCommand.run());

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testHandleParsingErrors() throws IOException {
        // Create a file with parsing errors
        Path tempFile = Files.createTempFile("invalidTestFile", ".gff3");
        Files.writeString(tempFile, "invalid content\n"); // Invalid GFF3 content

        String[] args = new String[] {"%s".formatted(tempFile.toString())};
        CommandLine commandLine =
                new CommandLine(validationCommand).registerConverter(CliRulesOption.class, new RuleConverter());
        assertDoesNotThrow(() -> commandLine.parseArgs(args));

        assertThrows(RuntimeException.class, () -> validationCommand.run());

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testNonExistingFile() {
        CommandLine commandLine =
                new CommandLine(validationCommand).registerConverter(CliRulesOption.class, new RuleConverter());
        assertDoesNotThrow(() -> commandLine.parseArgs(new String[] {"non_existent_file.gff3 "}));
        assertThrows(RuntimeException.class, () -> validationCommand.run());
    }
}
