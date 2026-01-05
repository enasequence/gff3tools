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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "conversion", description = "Performs format conversions to or from gff3")
@Slf4j
public class FileConversionCommand extends AbstractCommand {

    @CommandLine.Parameters(
            paramLabel = "[output-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path outputFilePath;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        try (BufferedReader inputReader = getPipe(
                        Files::newBufferedReader,
                        () -> new BufferedReader(new InputStreamReader(System.in)),
                        inputFilePath);
                BufferedWriter outputWriter = getPipe(
                        Files::newBufferedWriter,
                        () -> {
                            // Set the log level to ERROR while writing the file to an output stream to
                            // ignore INFO,
                            // WARN logs
                            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
                            ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
                            return new BufferedWriter(new OutputStreamWriter(System.out));
                        },
                        outputFilePath)) {
            fromFileType = validateFileType(fromFileType, inputFilePath, "-f");
            toFileType = validateFileType(toFileType, outputFilePath, "-t");
            ValidationEngine engine = initValidationEngine(ruleOverrides);
            Converter converter = getConverter(engine, fromFileType, toFileType);
            converter.convert(inputReader, outputWriter);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (outputFilePath != null) {
            try {
                long sizeBytes = Files.size(outputFilePath);
                log.info("GFF3 file generated: {} ({})", outputFilePath.getFileName(), humanReadableSize(sizeBytes));

            } catch (IOException e) {
                log.warn("Unable to determine GFF3 file size for {}", outputFilePath, e);
            }
        }
    }

    private Converter getConverter(
            ValidationEngine engine, ConversionFileFormat inputFileType, ConversionFileFormat outputFileType)
            throws FormatSupportException {
        if (inputFileType == ConversionFileFormat.gff3 && outputFileType == ConversionFileFormat.embl) {
            // Need input file to random access the translation sequence.
            return new Gff3ToFFConverter(engine, inputFilePath);
        } else if (inputFileType == ConversionFileFormat.embl && outputFileType == ConversionFileFormat.gff3) {
            // FASTA path to write translation sequences
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
                String fileExtension = getFileExtension(filePath);
                if (fileExtension != null) {
                    try {
                        fileFormat = ConversionFileFormat.valueOf(fileExtension);
                    } catch (IllegalArgumentException e) {
                        throw new CLIException("Unrecognized file format: " + fileExtension + " use the " + cliOption
                                + " option to specify the format manually or update the file extension");
                    }
                } else {
                    throw new CLIException("No file extension present, use the " + cliOption
                            + " option to specify the format manually or set the file extension");
                }
            } else {
                throw new CLIException("When streaming " + cliOption + " must be specified");
            }
        }
        return fileFormat;
    }

    public static String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot > 0 && lastIndexOfDot < fileName.length() - 1) {
            return fileName.substring(lastIndexOfDot + 1);
        }
        return null; // No extension found
    }

    private static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), unit);
    }
}
