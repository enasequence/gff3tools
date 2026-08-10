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

import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "gff3tools",
        subcommands = {
            FileConversionCommand.class,
            FileProcessCommand.class,
            ValidationCommand.class,
            TranslationCommand.class,
            CommandLine.HelpCommand.class
        },
        description = "Utility to convert and validate gff3 files")
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int exitCode = CLIExitCode.GENERAL.asInt();
        try {
            exitCode = new CommandLine(new Main())
                    .registerConverter(CliRulesOption.class, new RuleConverter())
                    .setExecutionExceptionHandler(new ExecutionExceptionHandler())
                    .execute(args);
        } catch (OutOfMemoryError e) {
            String filename = new java.io.File(Main.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .getPath())
                    .getName();
            LOG.error(
                    "The conversion needs more memory please increase the memory using the -Xmx java argument.\neg. java -Xmx2G -jar %s %s"
                            .formatted(filename, Arrays.stream(args).collect(Collectors.joining(" "))));
            exitCode = CLIExitCode.OUT_OF_MEMORY.asInt();
        } catch (Throwable e) {
            // Non-zero exit code (1) is returned in case of an Exception in run() method.
            LOG.error(e.getMessage(), e);
        }

        exit(exitCode);
    }

    public static void exit(int status) {
        System.exit(status);
    }
}
