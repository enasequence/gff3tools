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
package uk.ac.ebi.embl.gff3tools.gff3toff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.cli.Main;

/**
 * Keeps docs/reference-data-v2/master-example.json honest: the published example must convert and
 * produce the reference lines it is meant to illustrate.
 */
class ReferenceV2ExampleTest {

    private static final Path EXAMPLE = Paths.get("docs", "reference-data-v2", "master-example.json");

    @TempDir
    Path tempDir;

    @Test
    void publishedExampleConvertsToExpectedReferenceLines() throws Exception {
        Path outFile = tempDir.resolve("output.embl");
        CommandLine command = new CommandLine(new Main());
        command.setErr(new PrintWriter(new StringWriter()));
        command.setOut(new PrintWriter(new StringWriter()));
        int exitCode = command.execute(
                "conversion",
                "--master-entry",
                EXAMPLE.toAbsolutePath().toString(),
                getResourcePath("reference_golden/seq1.gff3").toString(),
                outFile.toString());
        assertEquals(0, exitCode, "conversion failed");

        String embl = Files.readString(outFile).replace("\r\n", "\n");
        assertTrue(embl.contains("RX   DOI; 10.1007/s11120-006-9122-4."), embl);
        assertTrue(embl.contains("RX   PUBMED; 17211581."), embl);
        assertTrue(embl.contains("RL   Photosyn. Res. 93(1-3):55-67(2007)."), embl);
        assertTrue(embl.contains("RG   Example Sequencing Consortium"), embl);
        assertTrue(embl.contains("RL   Submitted (02-NOV-2023) to the INSDC.\nRL   Example Institute"), embl);
    }

    private static Path getResourcePath(String resourceName) throws URISyntaxException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        assertTrue(resource != null, "Resource not found: " + resourceName);
        return Paths.get(resource.toURI());
    }
}
