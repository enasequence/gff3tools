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

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
        name = "process",
        description = "Performs file processing operations on GFF3 files",
        subcommands = {CountRegionsCommand.class, ReplaceIdsCommand.class, CommandLine.HelpCommand.class})
@Slf4j
public class FileProcessCommand extends AbstractCommand {

    @Override
    public void run() {
        // Parent command does nothing - Picocli shows help if no sub-command specified
        CommandLine.usage(this, System.out);
    }
}
