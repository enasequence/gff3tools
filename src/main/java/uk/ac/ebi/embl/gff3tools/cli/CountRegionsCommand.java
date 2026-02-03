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
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;

@CommandLine.Command(
        name = "count-regions",
        description = "Counts the number of sequence regions in a GFF3 file",
        mixinStandardHelpOptions = true)
@Slf4j
public class CountRegionsCommand extends AbstractCommand {

    // Pattern from GFF3FileReader
    private static final Pattern SEQUENCE_REGION_DIRECTIVE = Pattern.compile(
            "^##sequence-region\\s+(?<accession>(?<accessionId>[^.]+)(?:\\.(?<accessionVersion>\\d+))?)\\s+(?<start>[0-9]+)\\s+(?<end>[0-9]+)$");
    private static final Pattern VERSION_DIRECTIVE = Pattern.compile(
            "^##gff-version (?<version>(?<major>[0-9]+)(\\.(?<minor>[0-9]+)(:?\\.(?<patch>[0-9]+))?)?)\\s*$");
    // Match only single-# comments, not directives (##)
    private static final Pattern COMMENT = Pattern.compile("^#[^#].*$");

    @Override
    public void run() {
        try {
            // Suppress info logs when writing count to stdout to avoid contaminating output
            // Note: This command always writes to stdout (no output file option)
            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
            ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);

            // Count regions using streaming
            int count = countSequenceRegions();

            // Output count to stdout
            System.out.println(count);

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private int countSequenceRegions() throws Exception {
        int count = 0;
        boolean headerFound = false;
        int lineNumber = 0;

        try (BufferedReader reader = getPipe(
                Files::newBufferedReader, () -> new BufferedReader(new InputStreamReader(System.in)), inputFilePath)) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip blank lines
                if (line.isBlank()) {
                    continue;
                }

                // Check for FASTA section - stop counting after this
                if (line.startsWith("##FASTA")) {
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

                // Count sequence-region directives
                Matcher matcher = SEQUENCE_REGION_DIRECTIVE.matcher(line);
                if (matcher.matches()) {
                    count++;
                }
            }

            if (!headerFound) {
                throw new ValidationException("Invalid GFF3 file: no ##gff-version directive found");
            }
        }

        return count;
    }
}
