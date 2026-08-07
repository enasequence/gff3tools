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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.cli.Main;

/**
 * Golden fixture coverage for the streaming SQ block write path (T-2). The shared
 * {@code gff3toff_rules} pair-runner (GFF3ToFFConverterTest) never supplies a {@code --sequence}
 * source, so a real SQ block is never exercised there. This test drives the CLI directly with a
 * FASTA {@code --sequence} source (mixed-case bases plus a non-acgt base) and pins the emitted SQ
 * header + body against a golden EMBL file.
 */
class Gff3ToFFSqBlockGoldenTest {

    private static final Path RESOURCE_DIR = Path.of("src/test/resources/gff3toff_sq_rules");

    @TempDir
    Path tempDir;

    @Test
    void streamingSqBlockMatchesGoldenFile() throws Exception {
        Path gff3 = RESOURCE_DIR.resolve("sq_block.gff3");
        Path fasta = RESOURCE_DIR.resolve("sq_block.fasta");
        Path golden = RESOURCE_DIR.resolve("sq_block.embl");
        Path outFile = tempDir.resolve("sq_block-out.embl");

        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));

        int exitCode =
                command.execute("conversion", "--sequence", fasta.toString(), gff3.toString(), outFile.toString());

        assertEquals(0, exitCode, "Conversion with --sequence should succeed\nout: " + out + "\nerr: " + err);

        String expected = Files.readString(golden).trim();
        String actual = Files.readString(outFile).trim();
        assertEquals(expected, actual);
        assertTrue(actual.contains("SQ   Sequence 120 BP; 30 A; 28 C; 30 G; 30 T; 2 other;"));
    }
}
