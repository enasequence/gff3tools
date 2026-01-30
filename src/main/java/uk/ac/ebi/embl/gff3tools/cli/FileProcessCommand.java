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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;

@CommandLine.Command(name = "process", description = "Performs the file processing of gff3 & fasta files")
@Slf4j
public class FileProcessCommand extends AbstractCommand {

    @CommandLine.Option(
            names = "-accessions",
            description = "Comma-separated list of accessions (e.g. ACC1,ACC2)",
            split = ",",
            required = true)
    private List<String> accessions;

    @CommandLine.Option(names = "-gff3", description = "Gff3 input file", required = true)
    private Path gff3InputFile;

    @CommandLine.Option(names = "-fasta", description = "Fasta input file", required = true)
    private Path fastaInputFile;

    @CommandLine.Option(names = "-o", description = "Processed output file", required = true)
    private Path outputFilePath;

    @Override
    public void run() {
        try {
            validateFile(gff3InputFile, ConversionFileFormat.gff3.name());
            validateFile(fastaInputFile, ConversionFileFormat.fasta.name());
            validateOutputFile(outputFilePath);
            validateAccessions();
            // TODO: process gff3 + fasta files + initialize validation engine from rules
        } catch (CLIException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    protected void validateFile(Path filePath, String fileExtension) throws CLIException {

        // First checks the file exist or not
        if (!Files.exists(filePath)) {
            throw new CLIException("File does not exist: " + filePath);
        }

        String actualExtension = getFileExtension(filePath)
                .orElseThrow(() -> new CLIException("File has no extension: " + filePath.getFileName()));

        if (!fileExtension.equalsIgnoreCase(actualExtension)) {
            throw new CLIException("Invalid %s file: %s".formatted(fileExtension, filePath.getFileName()));
        }

        if (!Files.isReadable(filePath)) {
            throw new CLIException("File is not readable: " + filePath);
        }
    }

    protected void validateOutputFile(Path filePath) throws CLIException {

        String fileExtension = getFileExtension(filePath)
                .orElseThrow(() -> new CLIException("File has no extension: " + filePath.getFileName()));

        if (!fileExtension.equalsIgnoreCase(ConversionFileFormat.gff3.name())) {
            throw new CLIException("Invalid output file format %s, Expected gff3".formatted(fileExtension));
        }

        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            throw new CLIException("Output directory does not exist: " + parent);
        }

        if (Files.exists(filePath) && !Files.isWritable(filePath)) {
            throw new CLIException("Output file is not writable: " + filePath);
        }
    }

    protected void validateAccessions() throws CLIException {
        if (accessions.stream().anyMatch(String::isBlank)) {
            throw new CLIException("Accessions must not be blank");
        }
    }
}
