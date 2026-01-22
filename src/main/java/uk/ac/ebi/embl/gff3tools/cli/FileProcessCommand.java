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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
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
    public CliRulesOption rules;

    @CommandLine.Option(names = "-gff3", description = "Gff3 input file", required = true)
    public Path gff3InputFile;

    @CommandLine.Option(names = "-fasta", description = "Fasta input file", required = true)
    public Path fastaInputFile;

    @CommandLine.Option(names = "-analysisId", description = "Analysis Id")
    public String analysisId;

    @CommandLine.Option(names = "-o", description = "Processed output file", required = true)
    public Path outputFilePath;

    protected Map<String, RuleSeverity> getRuleOverrides() {
        return Optional.ofNullable(rules).map((r) -> r.rules()).orElse(new HashMap<>());
    }

    protected ValidationEngine initValidationEngine(Map<String, RuleSeverity> ruleOverrides)
            throws UnregisteredValidationRuleException {
        return new ValidationEngineBuilder().overrideMethodRules(ruleOverrides).build();
    }

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        try (BufferedReader gff3FileReader = Files.newBufferedReader(gff3InputFile);
                BufferedReader fastaFileReader = openZipFile(fastaInputFile)) {
            ValidationEngine engine = initValidationEngine(ruleOverrides);
            validateFile(gff3InputFile, "-gff3");
            validateFile(fastaInputFile, "-fasta");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected void validateFile(Path filePath, String cliOption) throws CLIException {
        if (filePath == null || filePath.toString().isEmpty()) {
            throw new CLIException("Missing a file for option " + cliOption);
        }

        String extension = getFileExtension(filePath);

        if ("-gff3".equals(cliOption) && !"gff3".equals(extension)) {

            throw new CLIException(
                    "Invalid Input file %s for option %s : ".formatted(filePath.getFileName(), cliOption));
        }

        if ("-o".equals(cliOption) && !"gff3".equals(extension)) {
            throw new CLIException(
                    "Invalid Output file %s for option %s : ".formatted(filePath.getFileName(), cliOption));
        }

        if ("-fasta".equals(cliOption) && !"fasta".equals(extension)) {
            throw new CLIException(
                    "Invalid Fasta file %s for option %s : ".formatted(filePath.getFileName(), cliOption));
        }
    }

    protected BufferedReader openZipFile(Path path) throws IOException {
        InputStream in = Files.newInputStream(path);

        if (path.getFileName().toString().endsWith(".gz")) {
            in = new GZIPInputStream(in);
        }

        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    protected static String getFileExtension(Path path) {
        String name = path.getFileName().toString();

        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3); // remove .gz
        }

        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1); // gff3, fasta, embl
        }
        return null;
    }
}
