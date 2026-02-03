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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;

@CommandLine.Command(
        name = "replace-ids",
        description = "Replaces sequence region IDs in a GFF3 file with provided accessions",
        mixinStandardHelpOptions = true)
@Slf4j
public class ReplaceIdsCommand extends AbstractCommand {

    // Patterns from GFF3FileReader
    private static final Pattern SEQUENCE_REGION_DIRECTIVE = Pattern.compile(
            "^##sequence-region\\s+(?<accession>(?<accessionId>[^.]+)(?:\\.(?<accessionVersion>\\d+))?)\\s+(?<start>[0-9]+)\\s+(?<end>[0-9]+)$");
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile(
            "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$");
    // Match only single-# comments, not directives (##)
    private static final Pattern COMMENT = Pattern.compile("^#[^#].*$");
    private static final Pattern FASTA_DIRECTIVE = Pattern.compile("^##FASTA$");

    // Pattern to detect feature lines (9 tab-separated columns)
    private static final Pattern FEATURE_LINE = Pattern.compile("^[^#].*");

    @CommandLine.Option(
            names = "--accessions",
            description = "Comma-separated list of accessions to replace sequence region IDs with",
            required = true,
            split = ",")
    public List<String> accessions;

    @CommandLine.Option(
            names = {"-o", "--output"},
            paramLabel = "<output-file>",
            description = "Output file (default: stdout)")
    public String outputFilePath;

    @Override
    public void run() {
        try {
            // Trim whitespace from accessions
            List<String> trimmedAccessions =
                    accessions.stream().map(String::trim).toList();

            // Validate accessions are non-empty
            validateAccessions(trimmedAccessions);

            // Determine if writing to stdout
            boolean writingToStdout = outputFilePath == null || outputFilePath.isEmpty();

            if (writingToStdout) {
                // Suppress info logs when writing to stdout
                LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
                ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
            }

            // Build replacement map (validates count matches)
            Map<String, String> replacementMap = buildReplacementMap(trimmedAccessions);

            // Perform replacement
            performReplacement(replacementMap, writingToStdout);

        } catch (CLIException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void validateAccessions(List<String> accessions) throws CLIException {
        for (int i = 0; i < accessions.size(); i++) {
            if (accessions.get(i).isEmpty()) {
                throw new CLIException("Accession at position " + (i + 1) + " is empty or blank");
            }
        }
    }

    private Map<String, String> buildReplacementMap(List<String> newAccessions) throws Exception {
        Map<String, String> replacementMap = new LinkedHashMap<>();
        List<String> originalAccessions = new ArrayList<>();
        boolean headerFound = false;
        int lineNumber = 0;

        // First pass: collect sequence regions in order
        try (BufferedReader reader = getPipe(
                Files::newBufferedReader, () -> new BufferedReader(new InputStreamReader(System.in)), inputFilePath)) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip blank lines
                if (line.isBlank()) {
                    continue;
                }

                // Stop at FASTA section
                if (FASTA_DIRECTIVE.matcher(line).matches()) {
                    break;
                }

                // Validate GFF3 header as first non-comment/non-blank line
                if (!headerFound) {
                    Matcher versionMatcher = VERSION_DIRECTIVE.matcher(line);
                    if (versionMatcher.matches()) {
                        headerFound = true;
                        continue;
                    } else if (line.startsWith("##")) {
                        // Directive before version header is invalid
                        throw new ValidationException(
                                "Invalid GFF3 file: directive found before ##gff-version at line " + lineNumber);
                    } else if (!COMMENT.matcher(line).matches()) {
                        // First non-blank, non-comment line must be version directive
                        throw new ValidationException(
                                "Invalid GFF3 file: expected ##gff-version directive at line " + lineNumber);
                    }
                    // Otherwise it's a comment, keep looking for header
                    continue;
                }

                // Collect sequence-region directives
                Matcher matcher = SEQUENCE_REGION_DIRECTIVE.matcher(line);
                if (matcher.matches()) {
                    String accession = matcher.group("accession");
                    originalAccessions.add(accession);
                }
            }

            if (!headerFound) {
                throw new ValidationException("Invalid GFF3 file: no ##gff-version directive found");
            }
        }

        // Validate count matches
        if (originalAccessions.size() != newAccessions.size()) {
            throw new CLIException("Accession count mismatch: file has " + originalAccessions.size()
                    + " sequence regions but " + newAccessions.size() + " accessions were provided");
        }

        // Build replacement map
        for (int i = 0; i < originalAccessions.size(); i++) {
            replacementMap.put(originalAccessions.get(i), newAccessions.get(i));
        }

        return replacementMap;
    }

    private void performReplacement(Map<String, String> replacementMap, boolean writingToStdout) throws Exception {
        int replacementCount = 0;
        boolean inFastaSection = false;

        try (BufferedReader reader = getPipe(
                        Files::newBufferedReader,
                        () -> new BufferedReader(new InputStreamReader(System.in)),
                        inputFilePath);
                BufferedWriter writer = getPipe(
                        Files::newBufferedWriter,
                        () -> new BufferedWriter(new OutputStreamWriter(System.out)),
                        outputFilePath == null || outputFilePath.isEmpty() ? null : Path.of(outputFilePath))) {

            String line;
            while ((line = reader.readLine()) != null) {

                // Check if we've entered the FASTA section
                if (FASTA_DIRECTIVE.matcher(line).matches()) {
                    inFastaSection = true;
                    writer.write(line);
                    writer.write("\n");
                    continue;
                }

                // In FASTA section: copy unchanged
                if (inFastaSection) {
                    writer.write(line);
                    writer.write("\n");
                    continue;
                }

                // Replace in ##sequence-region directives
                Matcher seqRegionMatcher = SEQUENCE_REGION_DIRECTIVE.matcher(line);
                if (seqRegionMatcher.matches()) {
                    String originalAccession = seqRegionMatcher.group("accession");
                    String newAccession = replacementMap.get(originalAccession);

                    if (newAccession != null) {
                        String start = seqRegionMatcher.group("start");
                        String end = seqRegionMatcher.group("end");

                        String replacedLine = String.format("##sequence-region %s %s %s", newAccession, start, end);

                        writer.write(replacedLine);
                        writer.write("\n");

                        if (!writingToStdout) {
                            log.info("Replaced sequence region: {} -> {}", originalAccession, newAccession);
                        }
                        replacementCount++;
                        continue;
                    }
                }

                // Replace in feature lines (column 1 is seqid)
                if (FEATURE_LINE.matcher(line).matches() && line.contains("\t")) {
                    String[] parts = line.split("\t", 2);
                    if (parts.length >= 2) {
                        String seqid = parts[0];

                        // Check if this seqid needs replacement
                        String newAccession = replacementMap.get(seqid);
                        if (newAccession != null) {
                            writer.write(newAccession);
                            writer.write("\t");
                            writer.write(parts[1]);
                            writer.write("\n");
                            continue;
                        }
                    }
                }

                // No replacement needed: write line as-is
                writer.write(line);
                writer.write("\n");
            }

            if (!writingToStdout) {
                log.info("Replacement complete: {} sequence regions replaced", replacementCount);
            }
        }
    }
}
