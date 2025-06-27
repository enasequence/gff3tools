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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.*;
import uk.ac.ebi.embl.converter.Converter;
import uk.ac.ebi.embl.converter.exception.CLIException;
import uk.ac.ebi.embl.converter.exception.ExitException;
import uk.ac.ebi.embl.converter.fftogff3.FFToGff3Converter;
import uk.ac.ebi.embl.converter.gff3toff.Gff3ToFFConverter;
import uk.ac.ebi.embl.converter.validation.RuleSeverity;
import uk.ac.ebi.embl.converter.validation.RuleSeverityState;
import uk.ac.ebi.embl.converter.validation.ValidationRule;

@Command(
        name = "gff3tools",
        subcommands = {CommandConversion.class, CommandLine.HelpCommand.class},
        description = "Utility to convert and validate gff3 files")
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            exitCode = new CommandLine(new Main())
                    .setExecutionExceptionHandler(new ExecutionExceptionHandler())
                    .execute(args);
        } catch (OutOfMemoryError e) {
            String filename = new java.io.File(Main.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .getPath())
                    .getName();
            System.err.println(
                    "The conversion needs more memory please increase the memory using the -Xmx java argument.\neg. java -jar -Xmx2G %s %s"
                            .formatted(filename, Arrays.stream(args).collect(Collectors.joining(" "))));
        } catch (Throwable e) {
            // Non-zero exit code (1) is returned in case of an Exception in run() method.
            LOG.error(e.getMessage(), e);
        }

        System.exit(exitCode);
    }
}

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "conversion", description = "Performs format conversions to or from gff3")
class CommandConversion implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(CommandConversion.class);

    public enum FileFormat {
        embl,
        gff3
    }

    @CommandLine.Option(
            names = "--rules",
            paramLabel = "<key:value,key:value>",
            description = "Specify rules in the format key:value",
            converter = RuleConverter.class)
    public CliRulesOption rules;

    @CommandLine.Option(names = "-f", description = "The type of the file to be converted")
    public FileFormat fromFileType;

    @CommandLine.Option(names = "-t", description = "The type of the file to convert to")
    public FileFormat toFileType;

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
        try {
            fromFileType = validateFileType(fromFileType, inputFilePath, "-f");
            toFileType = validateFileType(toFileType, outputFilePath, "-t");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (rules != null) {
            RuleSeverityState.INSTANCE.putAll(rules.rules());
        }

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
            Converter converter = getConverter(fromFileType, toFileType);
            converter.convert(inputReader, outputWriter);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Converter getConverter(FileFormat inputFileType, FileFormat outputFileType) throws FormatSupportError {
        if (inputFileType == FileFormat.gff3 && outputFileType == FileFormat.embl) {
            return new Gff3ToFFConverter();
        } else if (inputFileType == FileFormat.embl && outputFileType == FileFormat.gff3) {
            return new FFToGff3Converter();
        } else {
            throw new FormatSupportError(fromFileType, toFileType);
        }
    }

    private FileFormat validateFileType(FileFormat fileFormat, Path filePath, String cliOption) throws CLIException {
        if (fileFormat == null) {
            if (!filePath.toString().isEmpty()) {
                String fileExtension = getFileExtension(filePath);
                if (fileExtension != null) {
                    try {
                        fileFormat = FileFormat.valueOf(fileExtension);
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

    static class FormatSupportError extends ExitException {
        public FormatSupportError(final FileFormat fromFt, final FileFormat toFt) {
            super("Conversion from \"" + fromFt + "\" to \"" + toFt + "\" is not supported");
        }

        @Override
        public CLIExitCode exitCode() {
            return CLIExitCode.UNSUPPORTED_FORMAT_CONVERSION;
        }
    }
}

record CliRulesOption(Map<ValidationRule, RuleSeverity> rules) {}

class RuleConverter implements ITypeConverter<CliRulesOption> {
    CliRulesOption map = new CliRulesOption(new HashMap<>());

    @Override
    public CliRulesOption convert(String args) throws Exception {
        String[] entries = args.split(",");

        for (String entry : entries) {
            String[] pairs = entry.trim().split(":");
            if (pairs.length != 2) {
                throw new CLIException("Invalid rule: " + entry);
            }
            ValidationRule key;
            try {
                key = ValidationRule.valueOf(pairs[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CLIException("The rule: \"" + pairs[0] + "\" is invalid");
            }
            RuleSeverity value;
            try {
                value = RuleSeverity.valueOf(pairs[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new CLIException("The rule severity: \"" + pairs[1] + "\" is invalid");
            }
            this.map.rules().put(key, value);
        }
        return this.map;
    }
}

class ExecutionExceptionHandler implements IExecutionExceptionHandler {

    // Tried to use LOG.error instead of println here but would not pipe anything to
    // stderr.
    @Override
    public int handleExecutionException(Exception e, CommandLine commandLine, ParseResult parseResult)
            throws Exception {
        if (e.getCause() instanceof ExitException) {
            System.err.println(e.getMessage());
            return ((ExitException) e.getCause()).exitCode().asInt();
        }
        throw e;
    }
}
