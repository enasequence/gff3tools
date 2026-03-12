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
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.AccessionProvider;

@CommandLine.Command(name = "process", description = "Performs the file processing of gff3 files")
@Slf4j
public class FileProcessCommand extends AbstractCommand {

    @CommandLine.Option(
            names = "-accessions",
            description = "Comma-separated list of accessions (e.g. ACC1,ACC2 or old1:new1,old2:new2)",
            split = ",",
            required = true)
    private List<String> accessions;

    @CommandLine.Option(names = "-gff3", description = "Gff3 input file", required = true)
    private Path gff3InputFile;

    @CommandLine.Option(names = "-o", description = "Processed output file", required = true)
    private Path outputFilePath;

    @Override
    public void run() {
        try {
            validateFile(gff3InputFile, ConversionFileFormat.gff3.name());
            validateOutputFile(outputFilePath);
            validateAccessions();

            Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();
            AccessionProvider accessionProvider =
                    isMapMode() ? new AccessionProvider(buildMapModeAccessions()) : new AccessionProvider(accessions);

            ValidationEngine validationEngine = new ValidationEngineBuilder()
                    .overrideMethodRules(ruleOverrides)
                    .failFast(failFast)
                    .withProvider(accessionProvider)
                    .build();

            List<GFF3Annotation> annotations = new ArrayList<>();
            GFF3Header header;
            GFF3Species species;

            try (GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, gff3InputFile)) {
                header = gff3Reader.readHeader();
                species = gff3Reader.getSpecies();
                gff3Reader.read(annotations::add);
                if (species == null) {
                    species = gff3Reader.getSpecies();
                }
            }

            validationEngine.throwIfErrorsCollected();

            try (BufferedWriter writer = Files.newBufferedWriter(outputFilePath)) {
                GFF3File gff3File = GFF3File.builder()
                        .header(header)
                        .species(species)
                        .annotations(annotations)
                        .parsingWarnings(validationEngine.getParsingWarnings())
                        .build();
                gff3File.writeGFF3String(writer);
            }

            log.info("Processed {} annotations, output written to {}", annotations.size(), outputFilePath);
        } catch (CLIException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    boolean isMapMode() {
        return accessions.stream().anyMatch(a -> a.contains(":"));
    }

    private Map<String, String> buildMapModeAccessions() throws CLIException {
        Map<String, String> map = new LinkedHashMap<>();
        for (String entry : accessions) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new CLIException(
                        "Invalid map-mode accession entry: " + entry + ". Expected format: oldAccession:newAccession");
            }
            map.put(parts[0], parts[1]);
        }
        return map;
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

        // Reject mixed format: some entries with ':', some without
        boolean hasMapEntries = accessions.stream().anyMatch(a -> a.contains(":"));
        boolean hasListEntries = accessions.stream().anyMatch(a -> !a.contains(":"));
        if (hasMapEntries && hasListEntries) {
            throw new CLIException(
                    "Mixed accession format: all entries must be either map-mode (old:new) or list-mode (accession), not both");
        }
    }
}
