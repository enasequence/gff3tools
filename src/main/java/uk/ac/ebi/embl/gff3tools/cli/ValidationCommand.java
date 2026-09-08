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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.Gff3ProviderFactory;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "validation", description = "Performs validations on gff3 files")
@Slf4j
public class ValidationCommand extends AbstractCommand {

    @CommandLine.Mixin
    public SequenceOptions sequenceOptions;

    @CommandLine.Parameters(
            paramLabel = "[output-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path outputFilePath;

    private int warningCount = 0;

    private void addToWarnCount(int c) {
        this.warningCount += c;
    }

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        boolean writingToFile = !outputFilePath.toString().isEmpty();
        Path tempFile = null;

        try {
            if (writingToFile) {
                tempFile = Files.createTempFile("gff3tools-", ".tmp");
            }
            final Path effectiveOutputPath = writingToFile ? tempFile : null;

            List<FileSequenceSource> sources =
                    buildFastaSourceList(sequenceOptions.sequenceSpecs, sequenceOptions.sequenceFormat);
            CompositeSequenceProvider compositeProvider = Gff3ProviderFactory.buildCompositeProvider(sources);

            // GAP_GENERATION only matters to callers that keep the fixed annotation; when no output
            // is requested here, the fixed annotation is discarded so the fix is inert.
            Map<String, Boolean> fixOverrides = writingToFile ? Map.of() : Map.of("GAP_GENERATION", false);

            try (ValidationEngine validationEngine =
                    initValidationEngine(ruleOverrides, fixOverrides, compositeProvider)) {

                try (BufferedReader inputReader = getPipe(
                                Files::newBufferedReader,
                                () -> new BufferedReader(new InputStreamReader(System.in)),
                                inputFilePath);
                        GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, inputReader, inputFilePath)) {
                    GFF3Header header = gff3Reader.readHeader();
                    List<GFF3Annotation> annotations = writingToFile ? new ArrayList<>() : null;
                    gff3Reader.read(annotation -> {
                        if (writingToFile) {
                            annotations.add(annotation);
                        }
                        List<ValidationException> warnings = validationEngine.getParsingWarnings();
                        if (warnings != null && !warnings.isEmpty()) {
                            for (ValidationException e : warnings) {
                                log.warn("WARNING: %s".formatted(e.getMessage()));
                            }
                            addToWarnCount(warnings.size());
                            warnings.clear();
                        }
                    });

                    // Check for collected errors at end of processing
                    int errorCount = validationEngine.getCollectedErrors().size();
                    if (errorCount > 0) {
                        log.info("Validation completed with %d error(s)".formatted(errorCount));
                        validationEngine.throwIfErrorsCollected();
                    } else if (warningCount > 0) {
                        log.info("The file passed validations with %d warnings".formatted(warningCount));
                    } else {
                        log.info("The file has passed all validations!");
                    }

                    if (writingToFile) {
                        try (BufferedWriter outputWriter = Files.newBufferedWriter(effectiveOutputPath)) {
                            GFF3File gff3File = new GFF3File(
                                    header, gff3Reader.gff3Species, annotations, gff3Reader, null, false, null, null);
                            gff3File.writeGFF3String(outputWriter);
                        }
                    }
                }
            }

            if (writingToFile && tempFile != null) {
                try {
                    Files.move(
                            tempFile,
                            outputFilePath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, outputFilePath, StandardCopyOption.REPLACE_EXISTING);
                }
                tempFile = null;
            }
        } catch (Exception e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception deleteEx) {
                    log.warn("Failed to delete temporary file: {}", tempFile);
                }
            }
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
