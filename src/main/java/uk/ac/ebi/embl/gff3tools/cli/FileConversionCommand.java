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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
import uk.ac.ebi.embl.gff3tools.fftogff3.FastaToGff3Converter;
import uk.ac.ebi.embl.gff3tools.gff3toff.Gff3ToFFConverter;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.tsvconverter.TSVToGFF3Converter;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "conversion", description = "Performs format conversions to or from gff3")
@Slf4j
public class FileConversionCommand extends AbstractCommand {

    private static final int GZIP_MAGIC_BYTE1 = 0x1f;
    private static final int GZIP_MAGIC_BYTE2 = 0x8b;

    @CommandLine.Option(names = "-f", description = "The type of the input file to be converted")
    public ConversionFileFormat fromFileType;

    @CommandLine.Option(names = "-t", description = "The type of the file to convert to")
    public ConversionFileFormat toFileType;

    @CommandLine.Option(
            names = {"--master-entry", "-m"},
            description = "Optional master entry file. Accepts MasterEntry JSON (.json) or EMBL flatfile (.embl/.ff).")
    public Path masterFilePath;

    @CommandLine.Option(
            names = {"--output-sequence", "-os"},
            description = "Output path for nucleotide sequences in FASTA format (TSV to GFF3 conversion only)")
    public Path fastaOutputPath;

    @CommandLine.Option(
            names = {"--min-gap-length", "-mgl"},
            description = "Minimum run of N bases reported as a gap feature (FASTA to GFF3 conversion only). "
                    + "Default: ${DEFAULT-VALUE}.")
    public int minGapLength = FastaToGff3Converter.DEFAULT_MIN_GAP_LENGTH;

    @CommandLine.Option(
            names = {"--gap-type", "-gt"},
            description = "Optional INSDC gap_type for generated gap features (FASTA to GFF3 conversion only). "
                    + "When set, gaps map to assembly_gap; otherwise a plain gap is emitted.")
    public String gapType;

    @CommandLine.Option(
            names = {"--linkage-evidence", "-le"},
            description = "Optional INSDC linkage_evidence for generated gap features (FASTA to GFF3 conversion "
                    + "only). Only valid with a gap_type that requires it (e.g. \"within scaffold\").")
    public String linkageEvidence;

    @CommandLine.Parameters(
            paramLabel = "[output-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path outputFilePath;

    @CommandLine.Mixin
    public SequenceOptions sequenceOptions;

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

            List<FileSequenceSource> sources =
                    buildFastaSourceList(sequenceOptions.sequenceSpecs, sequenceOptions.sequenceFormat);
            CompositeSequenceProvider compositeProvider = buildCompositeProvider(sources);
            MasterMetadataProvider metadataProvider = buildMetadataProvider(masterFilePath);
            FastaHeaderProvider headerProvider = buildHeaderProvider(sources, sequenceOptions.fastaHeaderPath);

            try (BufferedReader inputReader = createInputReader();
                    BufferedWriter outputWriter =
                            writingToFile ? Files.newBufferedWriter(effectiveOutputPath) : createStdoutWriter()) {
                fromFileType = validateFileType(fromFileType, inputFilePath, "-f");
                toFileType = validateFileType(toFileType, outputFilePath, "-t");
                try (ValidationEngine engine =
                        initValidationEngine(ruleOverrides, compositeProvider, metadataProvider, headerProvider)) {
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
                    // ATOMIC_MOVE fails across filesystems; fall back to a regular move
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
        // Suppress INFO/WARN logs while writing to stdout to avoid mixing log output with file content
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

        boolean gzipped;
        try (InputStream peekStream = Files.newInputStream(inputFilePath)) {
            int byte1 = peekStream.read();
            int byte2 = peekStream.read();
            gzipped = (byte1 == GZIP_MAGIC_BYTE1 && byte2 == GZIP_MAGIC_BYTE2);
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The file does not exist: " + inputFilePath, e);
        } catch (IOException e) {
            throw new ReadException("Error opening file: " + inputFilePath, e);
        }

        try {
            if (gzipped) {
                log.debug("Detected gzip-compressed input file");
                return new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(Files.newInputStream(inputFilePath))));
            }
            return new BufferedReader(new InputStreamReader(Files.newInputStream(inputFilePath)));
        } catch (IOException e) {
            throw new ReadException("Error opening file: " + inputFilePath, e);
        }
    }

    private Converter getConverter(
            ValidationEngine engine, ConversionFileFormat inputFileType, ConversionFileFormat outputFileType)
            throws FormatSupportException, CLIException {
        if (inputFileType == ConversionFileFormat.gff3 && outputFileType == ConversionFileFormat.embl) {
            return new Gff3ToFFConverter(engine, inputFilePath);
        } else if (inputFileType == ConversionFileFormat.embl && outputFileType == ConversionFileFormat.gff3) {
            // Master metadata (from -m) is registered on the engine via buildMetadataProvider
            return new FFToGff3Converter(engine);
        } else if (inputFileType == ConversionFileFormat.tsv && outputFileType == ConversionFileFormat.gff3) {
            // TSV to GFF3 conversion using sequencetools template processing
            return new TSVToGFF3Converter(engine, fastaOutputPath);
        } else if (inputFileType == ConversionFileFormat.fasta && outputFileType == ConversionFileFormat.gff3) {
            SequenceFormat format = resolveSequenceFormat(inputFilePath, null);
            return new FastaToGff3Converter(engine, inputFilePath, format, minGapLength, gapType, linkageEvidence);
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
