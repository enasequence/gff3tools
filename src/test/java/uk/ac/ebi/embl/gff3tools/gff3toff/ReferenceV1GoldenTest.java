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

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.cli.Main;

/**
 * v1 master.json references (no {@code type}) must render exactly as before the v2 reference
 * schema (docs/2610141424_reference_data_v2.md). The expected RN..RL blocks under
 * reference_golden/ were produced by the converter at f53eb1fb, the commit before v2 support,
 * with one intended exception: broker accounts now get the legacy "broker, address, country"
 * submitter address (sequencetools ReferenceUtils.getAddressFromSubmissionAccount). Reference 6
 * in v1_master.json covers it; its expected lines follow the broker rule, not f53eb1fb.
 */
class ReferenceV1GoldenTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @CsvSource({
        "metadata/master_entry.json, reference_golden/seq1.gff3, reference_golden/master_entry.expected",
        "cli-examples/wgs/wgs_master.json, cli-examples/wgs/wgs_contig.gff3, reference_golden/wgs_master.expected",
        "reference_golden/v1_master.json, reference_golden/seq1.gff3, reference_golden/v1_master.expected"
    })
    void v1ReferenceBlockMatchesPreV2Output(String master, String gff3, String expected) throws Exception {
        Path outFile = tempDir.resolve("output.embl");
        CommandLine command = new CommandLine(new Main());
        command.setErr(new PrintWriter(new StringWriter()));
        command.setOut(new PrintWriter(new StringWriter()));
        int exitCode = command.execute(
                "conversion",
                "--master-entry",
                getResourcePath(master).toString(),
                getResourcePath(gff3).toString(),
                outFile.toString());
        assertEquals(0, exitCode, "conversion failed");

        assertEquals(
                normalise(Files.readString(getResourcePath(expected))),
                referenceBlock(normalise(Files.readString(outFile))));
    }

    /** Lines from the first RN line through the last RL line, inclusive. */
    private static String referenceBlock(String embl) {
        StringBuilder out = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        boolean started = false;
        for (String line : embl.split("\n", -1)) {
            started |= line.startsWith("RN");
            if (!started) continue;
            pending.append(line).append('\n');
            if (line.startsWith("RL")) {
                out.append(pending);
                pending.setLength(0);
            }
        }
        return out.toString();
    }

    private static String normalise(String text) {
        return text.replace("\r\n", "\n");
    }

    private Path getResourcePath(String resourceName) throws URISyntaxException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        assertNotNull(resource, "Resource not found: " + resourceName);
        return Paths.get(resource.toURI());
    }
}
