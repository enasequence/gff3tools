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
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;
import uk.ac.ebi.embl.converter.ConversionError;
import uk.ac.ebi.embl.converter.Converter;
import uk.ac.ebi.embl.converter.fftogff3.FFToGff3Converter;
import uk.ac.ebi.embl.converter.gff3toff.Gff3ToFFConverter;

@Command(
        name = "gff3tools",
        subcommands = {CommandConversion.class, CommandLine.HelpCommand.class},
        description = "Utility to convert and validate gff3 files")
public class Main {
    @Spec
    CommandSpec spec;

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int exitCode = -1;
        try {
            exitCode = new CommandLine(new Main()).execute(args);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        System.exit(exitCode);
    }
}

class CLIError extends Exception {
    public CLIError(String message) {
        super(message);
    }
}

// Using pandoc CLI interface conventions
@Command(name = "conversion", description = "Performs format conversions to or from gff3")
class CommandConversion implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CommandConversion.class);

    public enum FileFormat {
        embl,
        gff3
    }

    @Option(names = "-f", description = "The type of the file to be converted")
    public FileFormat fromFileType;

    @Option(names = "-t", description = "The type of the file to convert to")
    public FileFormat toFileType;

    @Parameters(paramLabel = "[input-file]", defaultValue = "", showDefaultValue = Help.Visibility.NEVER)
    public Path inputFilePath;

    @Parameters(paramLabel = "[output-file]", defaultValue = "", showDefaultValue = Help.Visibility.NEVER)
    public Path outputFilePath;

    @Override
    public void run() {
        try {
            fromFileType = validateFileType(fromFileType, inputFilePath, "-f");
            toFileType = validateFileType(toFileType, outputFilePath, "-t");
        } catch (CLIError e) {
            throw new Error(e);
        }

        try (BufferedReader inputReader = getPipe(
                        Files::newBufferedReader,
                        () -> new BufferedReader(new InputStreamReader(System.in)),
                        inputFilePath);
                BufferedWriter outputWriter = getPipe(
                        Files::newBufferedWriter,
                        () -> {
                            // Set the log level to ERROR while writing the file to an output stream to ignore INFO,
                            // WARN logs
                            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
                            ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
                            return new BufferedWriter(new OutputStreamWriter(System.out));
                        },
                        outputFilePath)) {
            Converter converter = getConverter(fromFileType, toFileType);
            converter.convert(inputReader, outputWriter);
        } catch (ConversionError | IOException e) {
            throw new Error(e.getMessage(), e);
        }
    }

    private Converter getConverter(FileFormat inputFileType, FileFormat outputFileType) throws ConversionError {
        if (inputFileType == FileFormat.gff3 && outputFileType == FileFormat.embl) {
            return new Gff3ToFFConverter();
        } else if (inputFileType == FileFormat.embl && outputFileType == FileFormat.gff3) {
            return new FFToGff3Converter();
        } else {
            throw new ConversionError("Conversion from " + fromFileType + " to " + toFileType + " is not supported");
        }
    }

    private FileFormat validateFileType(FileFormat fileFormat, Path filePath, String cliOption) throws CLIError {
        if (fileFormat == null) {
            if (!filePath.toString().isEmpty()) {
                String fileExtension = getFileExtension(filePath);
                if (fileExtension != null) {
                    try {
                        fileFormat = FileFormat.valueOf(fileExtension);
                    } catch (IllegalArgumentException e) {
                        throw new CLIError("Unrecognized file format: " + fileExtension + " use the " + cliOption
                                + " option to specify the format manually or update the file extension");
                    }
                } else {
                    throw new CLIError("No file extension present, use the " + cliOption
                            + " option to specify the format manually or set the file extension");
                }
            } else {
                throw new CLIError("When using stdin " + cliOption + " must be specified");
            }
        }
        return fileFormat;
    }

    @FunctionalInterface
    interface NewPipeFunction<T> {
        T apply(Path p, Charset c) throws IOException;
    }

    private <T> T getPipe(NewPipeFunction<T> newFilePipe, Function0<T> newStdPipe, Path filePath) {
        if (!filePath.toString().isEmpty()) {
            try {
                return newFilePipe.apply(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new Error("Error opening file: " + filePath, e);
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
