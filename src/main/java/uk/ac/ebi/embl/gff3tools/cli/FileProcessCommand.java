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
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Species;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.fix.AccessionReplacementFix;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

@CommandLine.Command(name = "process", description = "Performs the file processing of gff3 & fasta files")
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

            Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();
            Map<String, String> accessionMap = buildAccessionMap();

            ValidationEngine validationEngine = initValidationEngine(ruleOverrides);

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

            // Apply accession replacement as a post-processing step.
            // The fix is disabled in the engine (enabled=false) to avoid interfering with
            // the reader's accession-based grouping. It will be engine-driven once
            // context injection + priority execution features land.
            AccessionReplacementFix.setAccessionMap(accessionMap);
            try {
                AccessionReplacementFix fix = new AccessionReplacementFix();
                for (GFF3Annotation annotation : annotations) {
                    fix.replaceSequenceRegion(annotation, 0);
                    for (GFF3Feature feature : annotation.getFeatures()) {
                        fix.replaceFeatureAccession(feature, 0);
                    }
                }
            } finally {
                AccessionReplacementFix.clearAccessionMap();
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputFilePath)) {
                GFF3File gff3File = GFF3File.builder()
                        .header(header)
                        .species(species)
                        .annotations(annotations)
                        .parsingWarnings(validationEngine.getParsingWarnings())
                        .build();
                gff3File.writeGFF3String(writer);

                writeFastaWithReplacedAccessions(writer, accessionMap);
            }

            log.info("Processed {} annotations, output written to {}", annotations.size(), outputFilePath);
        } catch (CLIException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }
    }

    Map<String, String> buildAccessionMap() throws CLIException {
        boolean isMapMode = isMapMode();

        if (isMapMode) {
            return buildMapModeAccessions();
        } else {
            return buildListModeAccessions();
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

    private Map<String, String> buildListModeAccessions() throws CLIException {
        // In list mode, we need to read the GFF3 to discover annotation accessions in order,
        // then map them positionally to the provided accession list.
        List<String> annotationAccessions = collectAnnotationAccessions();

        if (annotationAccessions.size() != accessions.size()) {
            throw new CLIException("Accession count mismatch: %d accessions provided but %d annotations found in GFF3"
                    .formatted(accessions.size(), annotationAccessions.size()));
        }

        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < annotationAccessions.size(); i++) {
            map.put(annotationAccessions.get(i), accessions.get(i));
        }
        return map;
    }

    private List<String> collectAnnotationAccessions() throws CLIException {
        try {
            // First pass: read annotations to discover accessions in order.
            // Use a no-op validation engine (all fixes disabled) for this pass.
            ValidationEngine noOpEngine = initValidationEngine(getRuleOverrides());
            List<String> accessionOrder = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            try (GFF3FileReader reader = new GFF3FileReader(noOpEngine, gff3InputFile)) {
                reader.readHeader();
                reader.read(annotation -> {
                    String accession = annotation.getAccession();
                    if (seen.add(accession)) {
                        accessionOrder.add(accession);
                    }
                });
            }
            return accessionOrder;
        } catch (Exception e) {
            throw new CLIException("Failed to read GFF3 for accession discovery: " + e.getMessage());
        }
    }

    private void writeFastaWithReplacedAccessions(Writer writer, Map<String, String> accessionMap) throws IOException {
        if (!Files.exists(fastaInputFile) || Files.size(fastaInputFile) == 0) {
            return;
        }

        writer.write("##FASTA\n");

        try (BufferedReader br = openFastaReader(fastaInputFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(">")) {
                    line = replaceHeaderAccession(line, accessionMap);
                }
                writer.write(line);
                writer.write("\n");
            }
        }
    }

    private static BufferedReader openFastaReader(Path fastaPath) throws IOException {
        if (fastaPath.toString().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(fastaPath))));
        }
        return Files.newBufferedReader(fastaPath);
    }

    static String replaceHeaderAccession(String headerLine, Map<String, String> accessionMap) {
        // Header format: >accession or >accession|featureId or >accession rest
        String content = headerLine.substring(1); // strip '>'

        for (Map.Entry<String, String> entry : accessionMap.entrySet()) {
            String oldAccession = entry.getKey();
            if (content.equals(oldAccession)
                    || content.startsWith(oldAccession + "|")
                    || content.startsWith(oldAccession + " ")) {
                return ">" + entry.getValue() + content.substring(oldAccession.length());
            }
        }
        return headerLine;
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
