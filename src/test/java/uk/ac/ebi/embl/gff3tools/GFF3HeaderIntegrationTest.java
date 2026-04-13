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
package uk.ac.ebi.embl.gff3tools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.cli.Main;

class GFF3HeaderIntegrationTest {

    private Path getResourcePath(String resourceName) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(resourceName);
        assertNotNull(resource, "Resource not found: " + resourceName);
        return Paths.get(resource.getPath());
    }

    @Test
    void testWriteEMBLWithFastaHeaderLinear() throws Exception {
        Path gff3 = getResourcePath("gff3toff_header_tests/fasta_header_basic.gff3");
        Path headerJson = getResourcePath("sequence/fasta/header_test.json");
        Path outFile = Files.createTempFile("header-test-linear-", ".embl");

        try {
            String[] args = {"conversion", "--fasta-header", headerJson.toString(), gff3.toString(), outFile.toString()
            };
            StringWriter err = new StringWriter();
            StringWriter out = new StringWriter();
            CommandLine command = new CommandLine(new Main());
            command.setErr(new PrintWriter(err));
            command.setOut(new PrintWriter(out));

            int exitCode = command.execute(args);
            assertEquals(0, exitCode, "Wrong exit code.\nout: " + out.toString() + "\nerr: " + err.toString());

            String actual = Files.readString(outFile);
            String expectedFilePath = getResourcePath("gff3toff_header_tests/fasta_header_basic.embl")
                    .toString();
            String expected = Files.readString(Path.of(expectedFilePath));
            assertEquals(expected.trim(), actual.trim(), "EMBL output mismatch for linear header test");
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    @Test
    void testWriteEMBLWithFastaHeaderCircular() throws Exception {
        Path gff3 = getResourcePath("gff3toff_header_tests/fasta_header_circular.gff3");
        Path headerJson = getResourcePath("sequence/fasta/header_circular.json");
        Path outFile = Files.createTempFile("header-test-circular-", ".embl");

        try {
            String[] args = {"conversion", "--fasta-header", headerJson.toString(), gff3.toString(), outFile.toString()
            };
            StringWriter err = new StringWriter();
            StringWriter out = new StringWriter();
            CommandLine command = new CommandLine(new Main());
            command.setErr(new PrintWriter(err));
            command.setOut(new PrintWriter(out));

            int exitCode = command.execute(args);
            assertEquals(0, exitCode, "Wrong exit code.\nout: " + out.toString() + "\nerr: " + err.toString());

            String actual = Files.readString(outFile);
            String expectedFilePath = getResourcePath("gff3toff_header_tests/fasta_header_circular.embl")
                    .toString();
            String expected = Files.readString(Path.of(expectedFilePath));
            assertEquals(expected.trim(), actual.trim(), "EMBL output mismatch for circular header test");
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    @Test
    void testWriteEMBLWithFastaHeaderChromosome() throws Exception {
        Path gff3 = getResourcePath("gff3toff_header_tests/fasta_header_chromosome.gff3");
        Path headerJson = getResourcePath("sequence/fasta/header_chromosome.json");
        Path outFile = Files.createTempFile("header-test-chromosome-", ".embl");

        try {
            String[] args = {"conversion", "--fasta-header", headerJson.toString(), gff3.toString(), outFile.toString()
            };
            StringWriter err = new StringWriter();
            StringWriter out = new StringWriter();
            CommandLine command = new CommandLine(new Main());
            command.setErr(new PrintWriter(err));
            command.setOut(new PrintWriter(out));

            int exitCode = command.execute(args);
            assertEquals(0, exitCode, "Wrong exit code.\nout: " + out.toString() + "\nerr: " + err.toString());

            String actual = Files.readString(outFile);
            String expectedFilePath = getResourcePath("gff3toff_header_tests/fasta_header_chromosome.embl")
                    .toString();
            String expected = Files.readString(Path.of(expectedFilePath));
            assertEquals(expected.trim(), actual.trim(), "EMBL output mismatch for chromosome header test");
        } finally {
            Files.deleteIfExists(outFile);
        }
    }

    @Test
    void testWriteEMBLWithFastaHeaderPlasmidMitochondrion() throws Exception {
        Path gff3 = getResourcePath("gff3toff_header_tests/fasta_header_plasmid_mito.gff3");
        Path headerJson = getResourcePath("sequence/fasta/header_plasmid_mito.json");
        Path outFile = Files.createTempFile("header-test-plasmid-mito-", ".embl");

        try {
            String[] args = {"conversion", "--fasta-header", headerJson.toString(), gff3.toString(), outFile.toString()
            };
            StringWriter err = new StringWriter();
            StringWriter out = new StringWriter();
            CommandLine command = new CommandLine(new Main());
            command.setErr(new PrintWriter(err));
            command.setOut(new PrintWriter(out));

            int exitCode = command.execute(args);
            assertEquals(0, exitCode, "Wrong exit code.\nout: " + out.toString() + "\nerr: " + err.toString());

            String actual = Files.readString(outFile);
            String expectedFilePath = getResourcePath("gff3toff_header_tests/fasta_header_plasmid_mito.embl")
                    .toString();
            String expected = Files.readString(Path.of(expectedFilePath));
            assertEquals(expected.trim(), actual.trim(), "EMBL output mismatch for plasmid + mitochondrion header test");
        } finally {
            Files.deleteIfExists(outFile);
        }
    }
}
