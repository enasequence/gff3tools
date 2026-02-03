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
import org.junit.jupiter.api.Disabled;
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
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testProcessCommandMissingRequiredInput() {
        String[] args = {"process", "-gff3", "input.gff3", "-o", "output.gff3"};

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));

            String errorOutput = errContent.toString(StandardCharsets.UTF_8);

            assertTrue(
                    errorOutput.contains("Missing required options: '-accessions"),
                    "Expected missing -accessions error but got:\n" + errorOutput);
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testProcessCommandWithEmptyAccessions() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fasta = writeGzFile();
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");

        String[] args = {
            "process", "-accessions", "", "-gff3", gff3.toString(), "-fasta", fasta.toString(), "-o", output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            assertTrue(
                    errContent.toString(StandardCharsets.UTF_8).contains("Accessions must not be blank"),
                    "Expected empty accessions validation error");
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fasta);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testProcessCommandWithBlankAccessions() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fasta = writeGzFile();
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");

        String[] args = {
            "process",
            "-accessions",
            ", , ,",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fasta.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            assertTrue(
                    errContent.toString(StandardCharsets.UTF_8).contains("Accessions must not be blank"),
                    "Expected blank accessions validation error");
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fasta);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnInputGff3() throws IOException {
        Path gff3 = Files.createTempFile("invalid_gff3", ".gff2");
        Path output = Files.createTempFile("output", ".gff3");
        Files.writeString(gff3, "This is an invalid file\n");
        Path fasta = Files.createTempFile("invalid_fasta", ".fasta.gz");

        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(fasta))) {
            out.write("This is an invalid file\n".getBytes(StandardCharsets.UTF_8));
        }
        String[] args = new String[] {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fasta.toString(),
            "-o",
            output.toString()
        };
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("Invalid gff3 file"), "Expected invalid gff3 error but got:\n" + errorOutput);
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(output);
            Files.deleteIfExists(fasta);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnInvalidFasta() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path invalidFasta = Files.createTempFile("input", ".txt");
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");
        Files.writeString(invalidFasta, "not a fasta");

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            invalidFasta.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("Invalid fasta file"),
                    "Expected invalid fasta error but got:\n" + errorOutput);
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(invalidFasta);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnOutput() throws IOException {
        Path gff3 = Files.createTempFile("invalid_gff3", ".gff3");
        Path output = Files.createTempFile("output", ".embl");
        Files.writeString(gff3, "This is an invalid file\n");
        Path fasta = Files.createTempFile("invalid_fasta", ".fasta.gz");

        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(fasta))) {
            out.write("This is an invalid file\n".getBytes(StandardCharsets.UTF_8));
        }
        String[] args = new String[] {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fasta.toString(),
            "-o",
            output.toString()
        };
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("Invalid output file format"),
                    "Expected invalid gff3 error but got:\n" + errorOutput);
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(output);
            Files.deleteIfExists(fasta);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnFileNotExists() throws IOException {

        String[] args = new String[] {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            "invalid.gff3",
            "-fasta",
            "fastafile.fasta.gz",
            "-o",
            "output.gff3"
        };
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer((Answer<Void>) i -> null);
            Main.main(args);
            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("File does not exist: invalid.gff3"),
                    "Expected 'file does not exist' validation error, but got:\n" + errorOutput);
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnInputFileNoExtension() throws IOException {
        Path gff3NoExt = Files.createTempFile("input", ""); // no extension
        Path fasta = writeGzFile();
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3NoExt, "##gff-version 3\n");

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3NoExt.toString(),
            "-fasta",
            fasta.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("File has no extension"),
                    "Expected no-extension error but got:\n" + errorOutput);
        } finally {
            Files.deleteIfExists(gff3NoExt);
            Files.deleteIfExists(fasta);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnFastaNoExtension() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fastaNoExt = Files.createTempFile("fasta", "");
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");
        Files.writeString(fastaNoExt, ">seq\nATGC\n");

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fastaNoExt.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            assertTrue(
                    errContent.toString(StandardCharsets.UTF_8).contains("File has no extension"),
                    "Expected FASTA no-extension error");
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fastaNoExt);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnInvalidFastaExtension() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fastaTxt = Files.createTempFile("fasta", ".txt");
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");
        Files.writeString(fastaTxt, "not a fasta");

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fastaTxt.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            assertTrue(
                    errContent.toString(StandardCharsets.UTF_8).contains("Invalid fasta file"),
                    "Expected invalid FASTA extension error");
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fastaTxt);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOnUnreadableInput() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fasta = writeGzFile();
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");
        gff3.toFile().setReadable(false);

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fasta.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("File is not readable"),
                    "Expected unreadable file error but got:\n" + errorOutput);
        } finally {
            gff3.toFile().setReadable(true);
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fasta);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOutputDirectoryDoesNotExist() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fasta = writeGzFile();

        // Non-existent directory
        Path nonExistingDir = gff3.getParent().resolve("missing_dir");
        Path output = nonExistingDir.resolve("out.gff3");

        Files.writeString(gff3, "##gff-version 3\n");

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fasta.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));

            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("Output directory does not exist"),
                    "Expected output directory validation error but got:\n" + errorOutput);
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fasta);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testCLIExceptionProcessCommandOutputNotWritable() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fasta = writeGzFile();
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");
        output.toFile().setWritable(false);

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fasta.toString(),
            "-o",
            output.toString()
        };

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(CLIExitCode.USAGE.asInt()));
            String errorOutput = errContent.toString(StandardCharsets.UTF_8);
            assertTrue(
                    errorOutput.contains("Output file is not writable"),
                    "Expected output file not writable validation error but got:\n" + errorOutput);
        } finally {
            output.toFile().setWritable(true);
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fasta);
            Files.deleteIfExists(output);
            System.setErr(originalErr);
        }
    }

    @Test
    @Disabled("FileProcessCommand refactored to parent command - functionality moved to sub-commands")
    void testValidProcessCommand() throws IOException {
        Path gff3 = Files.createTempFile("input", ".gff3");
        Path fastaGz = writeGzFile();
        Path output = Files.createTempFile("out", ".gff3");

        Files.writeString(gff3, "##gff-version 3\n");

        String[] args = {
            "process",
            "-accessions",
            "AB_1000,ES8999,B560000.1",
            "-gff3",
            gff3.toString(),
            "-fasta",
            fastaGz.toString(),
            "-o",
            output.toString()
        };

        try (MockedStatic<Main> mock = mockStatic(Main.class)) {
            mock.when(() -> Main.main(any())).thenCallRealMethod();
            mock.when(() -> Main.exit(anyInt())).thenAnswer(i -> null);

            Main.main(args);

            mock.verify(() -> Main.exit(0));
        } finally {
            Files.deleteIfExists(gff3);
            Files.deleteIfExists(fastaGz);
            Files.deleteIfExists(output);
        }
    }

    /**
     * Creates a temporary gzipped FASTA file.
     * <p>
     * Caller is responsible for deleting the returned file.
     */
    private static Path writeGzFile() throws IOException {
        Path path = Files.createTempFile("input", ".fasta.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(">seq1\nATGC\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}
