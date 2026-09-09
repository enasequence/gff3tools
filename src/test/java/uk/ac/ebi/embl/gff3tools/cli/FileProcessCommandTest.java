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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class FileProcessCommandTest {

    @TempDir
    Path tempDir;

    private int executeProcess(String... args) {
        StringWriter err = new StringWriter();
        StringWriter out = new StringWriter();
        CommandLine command = new CommandLine(new Main())
                .registerConverter(CliRulesOption.class, new RuleConverter())
                .registerConverter(CliParamsOption.class, new ParamsConverter())
                .setExecutionExceptionHandler(new ExecutionExceptionHandler());
        command.setErr(new PrintWriter(err));
        command.setOut(new PrintWriter(out));
        return command.execute(args);
    }

    // ── --params must not be silently discarded on this command ─────────────────────────

    @Test
    void unknownParamKey_exitsUsage() throws Exception {
        Path gff3 = tempDir.resolve("input.gff3");
        Files.writeString(gff3, "##gff-version 3\n");
        Path fasta = tempDir.resolve("input.fasta");
        Files.writeString(fasta, ">seq1\nATGC\n");
        Path output = tempDir.resolve("output.gff3");

        int exitCode = executeProcess(
                "process",
                "-accessions",
                "ACC1",
                "-gff3",
                gff3.toString(),
                "-fasta",
                fasta.toString(),
                "-o",
                output.toString(),
                "--params=NOT_A_REAL_RULE.NOT_A_REAL_PARAM:x");

        assertEquals(
                CLIExitCode.USAGE.asInt(),
                exitCode,
                "--params must not be silently discarded on the process command, even though it does "
                        + "not yet build a validation engine");
    }

    @Test
    void listParamsFlag_printsListingAndSkipsFileValidation() {
        // -gff3/-fasta/-o are required by picocli parsing itself, so they must be supplied even
        // though --list-params must short-circuit before any of the paths are touched.
        int exitCode = executeProcess(
                "process",
                "-accessions",
                "ACC1",
                "-gff3",
                tempDir.resolve("does-not-exist.gff3").toString(),
                "-fasta",
                tempDir.resolve("does-not-exist.fasta").toString(),
                "-o",
                tempDir.resolve("does-not-exist-output.gff3").toString(),
                "--list-params");

        assertEquals(
                0, exitCode, "--list-params must succeed and skip file validation, even with nonexistent file paths");
    }
}
