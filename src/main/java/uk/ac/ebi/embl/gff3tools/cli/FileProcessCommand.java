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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.UnregisteredValidationRuleException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

@CommandLine.Command(name = "process", description = "Performs the file processing of gff3 & fasta files")
@Slf4j
public class FileProcessCommand implements Runnable {

    @CommandLine.Option(
            names = "--rules",
            paramLabel = "<key:value,key:value>",
            description = "Specify rules in the format key:value")
    private CliRulesOption rules;

    @CommandLine.Option(names = "-gff3", description = "Gff3 input file", required = true)
    private Path gff3InputFile;

    @CommandLine.Option(names = "-fasta", description = "Fasta input file", required = true)
    private Path fastaInputFile;

    @CommandLine.Option(names = "-analysisId", description = "Analysis Id")
    private String analysisId;

    @CommandLine.Option(names = "-o", description = "Processed output file", required = true)
    private Path outputFilePath;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        try {
            ValidationEngine engine = initValidationEngine(ruleOverrides);
            validateFile(gff3InputFile, ConversionFileFormat.gff3.name());
            validateFile(fastaInputFile, ConversionFileFormat.fasta.name());
            // TODO: process gff3 + fasta files

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected Map<String, RuleSeverity> getRuleOverrides() {
        return Optional.ofNullable(rules).map(CliRulesOption::rules).orElse(new HashMap<>());
    }

    protected ValidationEngine initValidationEngine(Map<String, RuleSeverity> ruleOverrides)
            throws UnregisteredValidationRuleException {
        return new ValidationEngineBuilder().overrideMethodRules(ruleOverrides).build();
    }

    protected void validateFile(Path filePath, String fileExtension) throws CLIException {
        if (filePath == null) {
            throw new CLIException("Missing " + fileExtension + " input file");
        }
        String actualExtension = getFileExtension(filePath)
                .orElseThrow(() -> new CLIException("File has no extension: " + filePath.getFileName()));

        if (!fileExtension.equalsIgnoreCase(actualExtension)) {
            throw new CLIException("Invalid %s file: %s".formatted(fileExtension, filePath.getFileName()));
        }

        if (!Files.exists(filePath)) {
            throw new CLIException("File does not exist: " + filePath);
        }
        if (!Files.isReadable(filePath)) {
            throw new CLIException("File is not readable: " + filePath);
        }
    }

    protected static Optional<String> getFileExtension(Path path) {
        String name = path.getFileName().toString();

        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }

        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? Optional.of(name.substring(dot + 1)) : Optional.empty();
    }
}
