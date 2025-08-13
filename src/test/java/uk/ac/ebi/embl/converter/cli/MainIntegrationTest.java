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
package uk.ac.ebi.embl.converter.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

public class MainIntegrationTest {

    @BeforeEach
    void setup() {}

    @AfterEach
    void tearDown() {}

    @Test
    void testCLIExceptionExitCode() {
        String[] args = new String[] {"conversion", "-f", "invalid_format", "-t", "gff3", "input.gff3"};
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testReadExceptionExitCode() {
        String[] args = new String[] {"conversion", "-f", "embl", "-t", "gff3", "non_existent_input.embl"}; //
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.NON_EXISTENT_FILE.asInt()));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testValidationExceptionExitCode() throws IOException {
        // Create a temporary file with an invalid GFF3 header to trigger
        // ValidationException
        Path tempFile = Files.createTempFile("invalid_gff3", ".gff3");
        Files.writeString(tempFile, "This is an invalid file\n");

        String[] args = new String[] {"conversion", "-f", "gff3", "-t", "embl", tempFile.toString()};
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.VALIDATION_ERROR.asInt()));
            assertTrue(errContent
                    .toString()
                    .contains("GFF3_INVALID_HEADER"));
        } finally {
            Files.deleteIfExists(tempFile);
            System.setErr(originalErr);
        }
    }
}
