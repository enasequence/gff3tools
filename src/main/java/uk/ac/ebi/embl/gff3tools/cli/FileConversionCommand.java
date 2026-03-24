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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.FormatSupportException;
import uk.ac.ebi.embl.gff3tools.fftogff3.FFToGff3Converter;
import uk.ac.ebi.embl.gff3tools.gff3toff.Gff3ToFFConverter;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "conversion", description = "Performs format conversions to or from gff3")
@Slf4j
public class FileConversionCommand extends AbstractCommand {

    @CommandLine.Parameters(
            paramLabel = "[output-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path outputFilePath;

    @CommandLine.Option(
            names = "--sequence",
            description = "Sequence source for translation validation. Repeatable. Use path for FASTA files "
                    + "or key:path for plain sequences. "
                    + "Examples: --sequence seqs.fasta --sequence chr1:chr1.seq")
    public List<String> sequenceSpecs;

    @CommandLine.Option(
            names = "--sequence-format",
            description = "Format of the sequence file: fasta, plain. Inferred from extension if omitted.")
    public SequenceFormat sequenceFormat;

    @CommandLine.Option(
            names = "--translation-mode",
            description =
                    "Translation output mode when converting to GFF3: gff3-fasta, fasta, attribute (default: gff3-fasta)",
            defaultValue = "gff3_fasta",
            converter = TranslationMode.Converter.class)
    public TranslationMode translationMode;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        // Determine if we're writing to a file or stdout
        boolean writingToFile = !outputFilePath.toString().isEmpty();
        Path tempFile = null;

        try {
            if (writingToFile) {
                tempFile = Files.createTempFile("gff3tools-", ".tmp");
            }

            final Path effectiveOutputPath = writingToFile ? tempFile : null;

            CompositeSequenceProvider compositeProvider = buildCompositeProvider(sequenceSpecs, sequenceFormat);

            try (BufferedReader inputReader = getPipe(
                            Files::newBufferedReader,
                            () -> new BufferedReader(new InputStreamReader(System.in)),
                            inputFilePath);
                    BufferedWriter outputWriter =
                            writingToFile ? Files.newBufferedWriter(effectiveOutputPath) : createStdoutWriter()) {
                fromFileType = validateFileType(fromFileType, inputFilePath, "-f");
                toFileType = validateFileType(toFileType, outputFilePath, "-t");
                Path processDir = Optional.ofNullable(inputFilePath.getParent()).orElse(Path.of("."));

                try (ValidationEngine engine = compositeProvider != null
                        ? initValidationEngine(ruleOverrides, processDir, compositeProvider)
                        : initValidationEngine(ruleOverrides, processDir)) {
                    Converter converter = getConverter(engine, fromFileType, toFileType);
                    converter.convert(inputReader, outputWriter);
                }
            }

            // Conversion succeeded - move temp file to final destination atomically
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

    private BufferedWriter createStdoutWriter() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
        return new BufferedWriter(new OutputStreamWriter(System.out));
    }

    private Converter getConverter(
            ValidationEngine engine, ConversionFileFormat inputFileType, ConversionFileFormat outputFileType)
            throws FormatSupportException {
        if (inputFileType == ConversionFileFormat.gff3 && outputFileType == ConversionFileFormat.embl) {
            return new Gff3ToFFConverter(engine, inputFilePath);
        } else if (inputFileType == ConversionFileFormat.embl && outputFileType == ConversionFileFormat.gff3) {
            return masterFilePath == null
                    ? new FFToGff3Converter(engine)
                    : new FFToGff3Converter(engine, masterFilePath);
        } else {
            throw new FormatSupportException(fromFileType, toFileType);
        }
    }

    private ConversionFileFormat validateFileType(ConversionFileFormat fileFormat, Path filePath, String cliOption)
            throws CLIException {
        if (fileFormat == null) {
            if (!filePath.toString().isEmpty()) {
                String fileExtension = getFileExtension(filePath)
                        .orElseThrow(() -> new CLIException("No file extension present, use the " + cliOption
                                + " option to specify the format manually or set the file extension"));
                try {
                    fileFormat = ConversionFileFormat.valueOf(fileExtension);
                } catch (IllegalArgumentException e) {
                    throw new CLIException("Unrecognized file format: " + fileExtension + " use the " + cliOption
                            + " option to specify the format manually or update the file extension");
                }
            } else {
                throw new CLIException("When streaming " + cliOption + " must be specified");
            }
        }
        return fileFormat;
    }
}
