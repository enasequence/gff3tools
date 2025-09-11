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
package uk.ac.ebi.embl.converter.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.vavr.Function0;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.converter.Converter;
import uk.ac.ebi.embl.converter.exception.*;
import uk.ac.ebi.embl.converter.fftogff3.FFToGff3Converter;
import uk.ac.ebi.embl.converter.gff3toff.Gff3ToFFConverter;
import uk.ac.ebi.embl.converter.validation.*;
import uk.ac.ebi.embl.converter.validation.builtin.*;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "conversion", description = "Performs format conversions to or from gff3")
public class FileConversionCommand implements Runnable {

    @CommandLine.Option(
            names = "--rules",
            paramLabel = "<key:value,key:value>",
            description = "Specify rules in the format key:value")
    public CliRulesOption rules;

    @CommandLine.Option(names = "-f", description = "The type of the file to be converted")
    public ConversionFileFormat fromFileType;

    @CommandLine.Option(names = "-t", description = "The type of the file to convert to")
    public ConversionFileFormat toFileType;

    @CommandLine.Option(names = "-m", description = "Optional master file")
    public Path masterFilePath;

    @CommandLine.Parameters(
            paramLabel = "[input-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path inputFilePath;

    @CommandLine.Parameters(
            paramLabel = "[output-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path outputFilePath;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides =
                Optional.ofNullable(rules).map((r) -> r.rules()).orElse(new HashMap<>());

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
            Converter converter = getConverter(engine, fromFileType, toFileType, masterFilePath);
            converter.convert(inputReader, outputWriter);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private ValidationEngine initValidationEngine(Map<String, RuleSeverity> ruleOverrides)
            throws UnregisteredValidationRuleException {
        ValidationEngineBuilder engineBuilder = new ValidationEngineBuilder();
        engineBuilder.registerValidations(ValidationRegistry.getValidations());
        engineBuilder.overrideRuleSeverities(ruleOverrides);
        return engineBuilder.build();
    }

    private Converter getConverter(
            ValidationEngine engine,
            ConversionFileFormat inputFileType,
            ConversionFileFormat outputFileType,
            Path masterFilePath)
            throws FormatSupportException {
        if (inputFileType == ConversionFileFormat.gff3 && outputFileType == ConversionFileFormat.embl) {
            return new Gff3ToFFConverter(engine);
        } else if (inputFileType == ConversionFileFormat.embl && outputFileType == ConversionFileFormat.gff3) {
            return new FFToGff3Converter(engine, masterFilePath);
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

    @FunctionalInterface
    interface NewPipeFunction<T> {
        T apply(Path p, Charset c) throws IOException;
    }

    private <T> T getPipe(NewPipeFunction<T> newFilePipe, Function0<T> newStdPipe, Path filePath) throws ExitException {
        if (!filePath.toString().isEmpty()) {
            try {
                return newFilePipe.apply(filePath, StandardCharsets.UTF_8);
            } catch (NoSuchFileException e) {
                throw new NonExistingFile("The file does not exist: " + filePath, e);
            } catch (IOException e) {
                throw new ReadException("Error opening file: " + filePath, e);
            }
        } else {
            return newStdPipe.apply();
        }
    }

    public static String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot > 0 && lastIndexOfDot < fileName.length() - 1) {
            return fileName.substring(lastIndexOfDot + 1);
        }
        return null; // No extension found
    }
}
