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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.*;
import uk.ac.ebi.embl.converter.exception.CLIException;
import uk.ac.ebi.embl.converter.exception.ExitException;
import uk.ac.ebi.embl.converter.validation.RuleSeverity;
import uk.ac.ebi.embl.converter.validation.ValidationRule;

@Command(
        name = "gff3tools",
        subcommands = {CommandConversion.class, CommandLine.HelpCommand.class},
        description = "Utility to convert and validate gff3 files")
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = new CommandLine(new Main())
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
                    "The conversion needs more memory please increase the memory using the -Xmx java argument.\neg. java -jar -Xmx2G %s %s"
                            .formatted(filename, Arrays.stream(args).collect(Collectors.joining(" "))));
            exitCode = CLIExitCode.OUT_OF_MEMORY.asInt();
        } catch (Throwable e) {
            // Non-zero exit code (1) is returned in case of an Exception in run() method.
            LOG.error(e.getMessage(), e);
        }

        System.exit(exitCode);
    }
}

record CliRulesOption(Map<ValidationRule, RuleSeverity> rules) {}

class RuleConverter implements ITypeConverter<CliRulesOption> {
    CliRulesOption map = new CliRulesOption(new HashMap<>());

    @Override
    public CliRulesOption convert(String args) throws Exception {
        String[] entries = args.split(",");

        for (String entry : entries) {
            String[] pairs = entry.trim().split(":");
            if (pairs.length != 2) {
                throw new CLIException("Invalid rule: '" + entry + "' There must be 2 values separated by ':' ");
            }
            ValidationRule key;
            try {
                key = ValidationRule.valueOf(pairs[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CLIException("The rule: \"" + pairs[0] + "\" is invalid");
            }
            RuleSeverity value;
            try {
                value = RuleSeverity.valueOf(pairs[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CLIException("The rule severity: \"" + pairs[1] + "\" is invalid");
            }
            this.map.rules().put(key, value);
        }
        return this.map;
    }
}

class ExecutionExceptionHandler implements IExecutionExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutionExceptionHandler.class);

    // Tried to use LOG.error instead of println here but would not pipe anything to
    // stderr.
    @Override
    public int handleExecutionException(Exception e, CommandLine commandLine, ParseResult parseResult)
            throws Exception {
        if (e.getCause() instanceof ExitException) {
            LOG.error(e.getMessage());
            return ((ExitException) e.getCause()).exitCode().asInt();
        }
        throw e;
    }
}
