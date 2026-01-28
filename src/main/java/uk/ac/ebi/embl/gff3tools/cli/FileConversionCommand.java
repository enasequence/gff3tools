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
import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.FormatSupportException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.fftogff3.FFToGff3Converter;
import uk.ac.ebi.embl.gff3tools.gff3toff.Gff3ToFFConverter;
import uk.ac.ebi.embl.gff3tools.tsvconverter.TSVToGFF3Converter;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "conversion", description = "Performs format conversions to or from gff3")
@Slf4j
public class FileConversionCommand extends AbstractCommand {

    private static final int GZIP_MAGIC_BYTE1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE2 = 0x8b;

    @CommandLine.Parameters(
            paramLabel = "[output-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path outputFilePath;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        // Determine if we're writing to a file or stdout
        boolean writingToFile = !outputFilePath.toString().isEmpty();
        Path tempFile = null;

        try {
            // Write to a temp file first to ensure atomic output: if conversion fails,
            // no partial/corrupt output file is created. Only on success do we move the
            // temp file to the final destination.
            // Temp files are created in the system temp directory (controlled via -Djava.io.tmpdir)
            // for better control in pipeline environments.
            if (writingToFile) {
                tempFile = Files.createTempFile("gff3tools-", ".tmp");
            }

            final Path effectiveOutputPath = writingToFile ? tempFile : null;

            try (BufferedReader inputReader = createInputReader();
                    BufferedWriter outputWriter =
                            writingToFile ? Files.newBufferedWriter(effectiveOutputPath) : createStdoutWriter()) {
                fromFileType = validateFileType(fromFileType, inputFilePath, "-f");
                toFileType = validateFileType(toFileType, outputFilePath, "-t");
                ValidationEngine engine = initValidationEngine(ruleOverrides);
                Converter converter = getConverter(engine, fromFileType, toFileType);
                converter.convert(inputReader, outputWriter);
            }

            // Conversion succeeded - move temp file to final destination atomically
            if (writingToFile && tempFile != null) {
                Files.move(
                        tempFile, outputFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                tempFile = null; // Mark as successfully moved
            }
        } catch (Exception e) {
            // Clean up temp file on error
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
        // Set the log level to ERROR while writing the file to an output stream to
        // ignore INFO, WARN logs
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.ERROR);
        return new BufferedWriter(new OutputStreamWriter(System.out));
    }

    /**
     * Creates a BufferedReader for the input file.
     * Automatically detects and handles gzip-compressed files.
     */
    private BufferedReader createInputReader() throws NonExistingFile, ReadException {
        if (inputFilePath == null || inputFilePath.toString().isEmpty()) {
            return new BufferedReader(new InputStreamReader(System.in));
        }

        InputStream fis = null;
        try {
            fis = Files.newInputStream(inputFilePath);
            BufferedInputStream bis = new BufferedInputStream(fis);

            // Check for gzip magic bytes
            bis.mark(2);
            int byte1 = bis.read();
            int byte2 = bis.read();
            bis.reset();

            if (byte1 == GZIP_MAGIC_BYTE1 && byte2 == GZIP_MAGIC_BYTE2) {
                log.debug("Detected gzip-compressed input file");
                return new BufferedReader(new InputStreamReader(new GZIPInputStream(bis)));
            }

            return new BufferedReader(new InputStreamReader(bis));
        } catch (NoSuchFileException e) {
            closeQuietly(fis);
            throw new NonExistingFile("The file does not exist: " + inputFilePath, e);
        } catch (IOException e) {
            closeQuietly(fis);
            throw new ReadException("Error opening file: " + inputFilePath, e);
        }
    }

    private Converter getConverter(
            ValidationEngine engine, ConversionFileFormat inputFileType, ConversionFileFormat outputFileType)
            throws FormatSupportException {
        if (inputFileType == ConversionFileFormat.gff3 && outputFileType == ConversionFileFormat.embl) {
            // Need input file to random access the translation sequence.
            return new Gff3ToFFConverter(engine, inputFilePath);
        } else if (inputFileType == ConversionFileFormat.embl && outputFileType == ConversionFileFormat.gff3) {
            // Create converter with optional master file and nucleotide sequence output path
            return new FFToGff3Converter(engine, masterFilePath, fastaOutputPath);
        } else if (inputFileType == ConversionFileFormat.tsv && outputFileType == ConversionFileFormat.gff3) {
            // TSV to GFF3 conversion using sequencetools template processing
            return new TSVToGFF3Converter(engine, fastaOutputPath);
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

    /**
     * Closes an InputStream quietly, ignoring any exceptions.
     */
    private static void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException ignored) {
                // Intentionally ignored
            }
        }
    }
}
