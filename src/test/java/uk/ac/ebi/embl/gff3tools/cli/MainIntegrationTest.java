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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
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
            assertTrue(errContent.toString().contains("GFF3_INVALID_HEADER"));
        } finally {
            Files.deleteIfExists(tempFile);
            System.setErr(originalErr);
        }
    }

    @Test
    void testCLIExceptionProcessCommandOnInput() throws IOException {
        Path tempFile = Files.createTempFile("invalid_gff3", ".gff2");
        Path outputFile = Files.createTempFile("output", ".gff3");
        Files.writeString(tempFile, "This is an invalid file\n");
        Path fastagzFile = Files.createTempFile("invalid_fasta", ".fasta.gz");

        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(fastagzFile))) {
            out.write("This is an invalid file\n".getBytes(StandardCharsets.UTF_8));
        }
        String[] args = new String[] {
            "process", "-gff3", tempFile.toString(), "-fasta", fastagzFile.toString(), outputFile.toString()
        };
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
    void testCLIExceptionProcessCommandOnOutput() throws IOException {
        Path tempFile = Files.createTempFile("invalid_gff3", ".gff2");
        Path outputFile = Files.createTempFile("output", ".embl");
        Files.writeString(tempFile, "This is an invalid file\n");
        Path fastagzFile = Files.createTempFile("invalid_fasta", ".fasta.gz");

        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(fastagzFile))) {
            out.write("This is an invalid file\n".getBytes(StandardCharsets.UTF_8));
        }
        String[] args = new String[] {
            "process", "-gff3", tempFile.toString(), "-fasta", fastagzFile.toString(), outputFile.toString()
        };
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
    void testCLIExceptionProcessCommandOnFileNotExists() throws IOException {

        String[] args =
                new String[] {"process", "-gff3", "invalid.gff3", "-fasta", "fastafile.fasta.gz", "output.gff3"};
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
    void testValidProcessCommand() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fastaGz = writeGzFile();
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");

        String[] args = {
            "process",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fastaGz.toString(),
            "-analysisId",
            "ANA123",
            "-o",
            output.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(0));
        }
    }

    private static Path writeGzFile() throws IOException {
        Path path = Files.createTempFile("input", ".fasta.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(">seq1\nATGC\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}
