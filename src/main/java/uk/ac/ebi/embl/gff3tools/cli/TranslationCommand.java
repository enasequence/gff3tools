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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceReaderFactory;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceProvider;

@CommandLine.Command(name = "translate", description = "Translate CDS features in GFF3 files using nucleotide sequences")
@Slf4j
public class TranslationCommand extends AbstractCommand {

    @CommandLine.Option(
            names = "--sequence-fasta",
            description = "Path to a nucleotide FASTA file for translation")
    public Path sequenceFastaPath;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();
        Path processDir = Optional.ofNullable(inputFilePath.getParent()).orElse(Path.of("."));

        try {
            if (sequenceFastaPath == null) {
                throw new RuntimeException(
                        "A sequence source is required. Provide --sequence-fasta or ensure a plugin supplies sequences.");
            }

            try (SequenceReader sequenceReader =
                    SequenceReaderFactory.readFasta(sequenceFastaPath.toFile())) {

                FileSequenceProvider sequenceProvider = new FileSequenceProvider();
                sequenceProvider.setSequenceReader(sequenceReader);

                ValidationEngine validationEngine = initValidationEngine(ruleOverrides, processDir, sequenceProvider);

                try (BufferedReader inputReader = getPipe(
                                Files::newBufferedReader,
                                () -> new BufferedReader(new InputStreamReader(System.in)),
                                inputFilePath);
                        GFF3FileReader gff3Reader =
                                new GFF3FileReader(validationEngine, inputReader, inputFilePath)) {

                    gff3Reader.readHeader();
                    gff3Reader.read(annotation -> {
                        List<ValidationException> warnings = validationEngine.getParsingWarnings();
                        if (warnings != null && !warnings.isEmpty()) {
                            for (ValidationException e : warnings) {
                                log.warn("WARNING: %s".formatted(e.getMessage()));
                            }
                            warnings.clear();
                        }
                    });

                    int errorCount =
                            validationEngine.getCollectedErrors().size();
                    if (errorCount > 0) {
                        log.info("Translation completed with %d error(s)".formatted(errorCount));
                        validationEngine.throwIfErrorsCollected();
                    } else {
                        log.info("Translation completed successfully");
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
