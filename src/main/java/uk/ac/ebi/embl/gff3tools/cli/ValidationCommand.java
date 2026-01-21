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
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "validation", description = "Performs validations on gff3 files")
@Slf4j
public class ValidationCommand extends AbstractCommand {

    private int warningCount = 0;

    private void addToWarnCount(int c) {
        this.warningCount += c;
    }

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        ValidationEngine validationEngine;

        try {
            validationEngine = initValidationEngine(ruleOverrides);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        try (BufferedReader inputReader = getPipe(
                        Files::newBufferedReader,
                        () -> new BufferedReader(new InputStreamReader(System.in)),
                        inputFilePath);
                GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, inputReader, inputFilePath)) {
            ;

            gff3Reader.readHeader();
            gff3Reader.read(annotation -> {
                List<ValidationException> warnings = validationEngine.getParsingWarnings();
                if (warnings != null && warnings.size() > 0) {
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

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
