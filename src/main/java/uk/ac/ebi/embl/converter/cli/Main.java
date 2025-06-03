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
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;
import uk.ac.ebi.embl.converter.fftogff3.FFToGff3Converter;
import uk.ac.ebi.embl.converter.fftogff3.FFtoGFF3ConversionError;
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
        exit(exitCode);
    }

    public static int exit(int code) {
        System.exit(code);
        return code;
    }
}

// Using pandoc CLI interface conventions
@Command(name = "conversion", description = "Performs format conversions to or from gff3")
class CommandConversion implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CommandConversion.class);

    public enum FileFormat {
        ff,
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
        Path emptyPath = Path.of("");
        if (fromFileType == null) {
            if (!inputFilePath.equals(emptyPath)) {
                String fileExtension = getFileExtension(inputFilePath);
                if (fileExtension != null) {
                    try {
                        fromFileType = FileFormat.valueOf(fileExtension);
                    } catch (IllegalArgumentException e) {
                        throw new Error("Unrecognized file format: " + fileExtension
                                + " use the -f option to specify the format manually or update the file extension");
                    }
                } else {
                    throw new Error(
                            "No file extension present, use the -f option to specify the format manually or set the file extension");
                }
            } else {
                throw new Error("When using stdin -f must be specified");
            }
        }

        if (toFileType == null) {
            if (!outputFilePath.equals(emptyPath)) {
                String fileExtension = getFileExtension(outputFilePath);
                if (fileExtension != null) {
                    try {
                        toFileType = FileFormat.valueOf(fileExtension);
                    } catch (IllegalArgumentException e) {
                        throw new Error("Unrecognized file format: " + fileExtension
                                + " use the -t option to specify the format manually or update the file extension");
                    }
                } else {
                    throw new Error(
                            "No file extension present, use the -t option to specify the format manually or set the file extension");
                }
            } else {
                toFileType = FileFormat.gff3;
            }
        }

        BufferedReader inputReader;
        if (!inputFilePath.equals(emptyPath)) {
            try {
                inputReader = Files.newBufferedReader(inputFilePath);
            } catch (IOException e) {
                throw new Error("Error opening file: " + inputFilePath + " for reading", e);
            }
        } else {
            inputReader = new BufferedReader(new InputStreamReader(System.in));
        }

        BufferedWriter outputWriter;
        if (!outputFilePath.equals(emptyPath)) {
            try {
                outputWriter = Files.newBufferedWriter(outputFilePath);
            } catch (IOException e) {
                throw new Error("Error opening file: " + outputFilePath + " for writing", e);
            }
        } else {
            // Disable info logs if we pipe to stdout
            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
            ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
            outputWriter = new BufferedWriter(new OutputStreamWriter(System.out));
        }

        if (fromFileType == FileFormat.gff3 && toFileType == FileFormat.ff) {
            Gff3ToFFConverter converter = new Gff3ToFFConverter();
            try {
                converter.convert(inputReader, outputWriter);
            } catch (FFtoGFF3ConversionError e) {
                throw new Error(e.getMessage(), e);
            }
        } else if (fromFileType == FileFormat.ff && toFileType == FileFormat.gff3) {
            FFToGff3Converter converter = new FFToGff3Converter();
            try {
                converter.convert(inputReader, outputWriter);
            } catch (FFtoGFF3ConversionError e) {
                throw new Error(e.getMessage(), e);
            }
        } else {
            throw new Error("Conversion from " + fromFileType + " to " + toFileType + " is not supported");
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
