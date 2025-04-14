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

import java.io.File;
import picocli.CommandLine;

public class Params {

    @CommandLine.Option(names = "-in", description = "Input file for conversion", required = true)
    public File inFile;

    @CommandLine.Option(names = "-out", description = "Out file after conversion", required = true)
    public File outFile;

    public static Params parse(String[] args) {
        Params params = new Params();
        CommandLine cmd = new CommandLine(params);
        try {
            cmd.setOptionsCaseInsensitive(true).parseArgs(args);
        } catch (CommandLine.ParameterException ex) {
            printErrorAndUsage(cmd, ex);
            throw ex;
        }
        return params;
    }

    private static void printErrorAndUsage(CommandLine cmd, CommandLine.ParameterException ex) {
        cmd.getErr().println(ex.getMessage());
        if (!CommandLine.UnmatchedArgumentException.printSuggestions(ex, cmd.getErr())) {
            cmd.usage(cmd.getErr());
        }
    }
}
