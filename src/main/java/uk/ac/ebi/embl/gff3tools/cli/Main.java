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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

@Command(
        name = "gff3tools",
        subcommands = {FileConversionCommand.class, ValidationCommand.class, CommandLine.HelpCommand.class},
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

record CliRulesOption(Map<String, RuleSeverity> rules) {}

class RuleConverter implements CommandLine.ITypeConverter<CliRulesOption> {
    CliRulesOption map = new CliRulesOption(new HashMap<>());

    @Override
    public CliRulesOption convert(String args) throws Exception {
        String[] entries = args.split(",");

        for (String entry : entries) {
            String[] pairs = entry.trim().split(":");
            if (pairs.length != 2) {
                throw new CLIException("Invalid rule: '" + entry + "' There must be 2 values separated by ':' ");
            }
            String key = pairs[0].toUpperCase();
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
