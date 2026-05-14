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
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.Converter;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.FormatSupportException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.fftogff3.FFToGff3Converter;
import uk.ac.ebi.embl.gff3tools.fftogff3.FastaToGff3Converter;
import uk.ac.ebi.embl.gff3tools.gff3toff.Gff3ToFFConverter;
import uk.ac.ebi.embl.gff3tools.metadata.EmblEntryMetadataSource;
import uk.ac.ebi.embl.gff3tools.metadata.MasterEntryJsonMetadataSource;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadataProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.tsvconverter.TSVToGFF3Converter;
import uk.ac.ebi.embl.gff3tools.utils.GzipUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "conversion", description = "Performs format conversions to or from gff3")
@Slf4j
public class FileConversionCommand extends AbstractCommand {

    // INSDC gap types for which linkage_evidence is both required and allowed. Mirrors
    // AssemblyGapValidation; kept here only to fail fast with a clear usage message.
    private static final Set<String> GAP_TYPES_REQUIRING_LINKAGE =
            Set.of("within scaffold", "repeat within scaffold", "contamination");

    @CommandLine.Option(names = "-f", description = "The type of the input file to be converted")
    public ConversionFileFormat fromFileType;

    @CommandLine.Option(names = "-t", description = "The type of the file to convert to")
    public ConversionFileFormat toFileType;

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

    @CommandLine.Option(
            names = {"--master-entry", "-m"},
            description = "Optional master entry file. Accepts MasterEntry JSON (.json) or EMBL flatfile (.embl/.ff).")
    public Path masterFilePath;

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

            // Resolve formats up front so the input FASTA can be registered as a sequence source
            // before the engine providers are built.
            fromFileType = validateFileType(fromFileType, inputFilePath, "-f");
            toFileType = validateFileType(toFileType, outputFilePath, "-t");

            List<FileSequenceSource> sources = new ArrayList<>(
                    buildFastaSourceList(sequenceOptions.sequenceSpecs, sequenceOptions.sequenceFormat));

            // For FASTA -> GFF3, register the input FASTA as a sequence source so the engine gains
            // its sequence/header context (read once) and the same sequence/annotation/fasta-header
            // validations run as in the FASTA+GFF3 case.
            FileSequenceSource inputFastaSource = null;
            if (fromFileType == ConversionFileFormat.fasta && toFileType == ConversionFileFormat.gff3) {
                validateGapOptions();
                SequenceFormat fmt = resolveSequenceFormat(inputFilePath, sequenceOptions.sequenceFormat);
                // Plain (headerless) sequences carry no submission ID, so there is nothing to put
                // in the GFF3 seqId column or sequence-region directive. Fail fast with a clear
                // message rather than silently emitting an empty GFF3.
                if (fmt == SequenceFormat.plain) {
                    throw new CLIException("FASTA to GFF3 conversion requires FASTA input with sequence headers; "
                            + "plain sequence input (--sequence-format plain) has no sequence ID to emit.");
                }
                // FileSequenceSource will decompress a gzipped input automatically.
                inputFastaSource = new FileSequenceSource(inputFilePath, fmt, null);
                sources.add(inputFastaSource);
            }

            CompositeSequenceProvider compositeProvider = buildCompositeProvider(sources);
            MasterMetadataProvider metadataProvider = buildMetadataProvider(masterFilePath);
            FastaHeaderProvider headerProvider = buildHeaderProvider(sources, sequenceOptions.fastaHeaderPath);

            final FileSequenceSource inputFastaSourceFinal = inputFastaSource;
            // FASTA -> GFF3 reads the sequence exclusively through the shared FileSequenceSource,
            // so we avoid opening (and, for gzip, decompressing) the input a second time here.
            try (BufferedReader inputReader = inputFastaSourceFinal != null
                            ? new BufferedReader(new StringReader(""))
                            : createInputReader();
                    BufferedWriter outputWriter =
                            writingToFile ? Files.newBufferedWriter(effectiveOutputPath) : createStdoutWriter()) {
                try (ValidationEngine engine =
                        initValidationEngine(ruleOverrides, compositeProvider, metadataProvider, headerProvider)) {
                    Converter converter = getConverter(engine, fromFileType, toFileType, inputFastaSourceFinal);
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

    /**
     * Fails fast with a clear usage message for inconsistent gap options, instead of deferring to
     * the validation engine (which would report a less obvious VALIDATION_ERROR). Value validity is
     * still enforced downstream by {@code AssemblyGapValidation}.
     */
    private void validateGapOptions() throws CLIException {
        boolean hasGapType = gapType != null && !gapType.isBlank();
        boolean hasLinkageEvidence = linkageEvidence != null && !linkageEvidence.isBlank();
        if (hasLinkageEvidence && !hasGapType) {
            throw new CLIException("--linkage-evidence requires --gap-type to be set");
        }
        if (hasGapType) {
            String normalizedGapType = gapType.trim().toLowerCase();
            boolean requiresLinkage = GAP_TYPES_REQUIRING_LINKAGE.contains(normalizedGapType);
            if (requiresLinkage && !hasLinkageEvidence) {
                throw new CLIException("--gap-type \"" + gapType.trim() + "\" requires --linkage-evidence to be set");
            }
            if (!requiresLinkage && hasLinkageEvidence) {
                throw new CLIException("--linkage-evidence is only valid with --gap-type "
                        + "\"within scaffold\", \"repeat within scaffold\" or \"contamination\"");
            }
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

        boolean gzipped = GzipUtils.isGzipped(inputFilePath);

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
            ValidationEngine engine,
            ConversionFileFormat inputFileType,
            ConversionFileFormat outputFileType,
            FileSequenceSource inputFastaSource)
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
            // inputFastaSource is the same source registered on the engine, so the FASTA is read once.
            return new FastaToGff3Converter(engine, inputFastaSource, minGapLength, gapType, linkageEvidence);
        } else {
            throw new FormatSupportException(fromFileType, toFileType);
        }
    }

    private MasterMetadataProvider buildMetadataProvider(Path masterEntryPath) throws ExitException {
        MasterMetadataProvider provider = new MasterMetadataProvider();
        if (masterEntryPath == null) {
            return provider;
        }
        String ext = getFileExtension(masterEntryPath).orElse("").toLowerCase();
        switch (ext) {
            case "json" -> provider.addSource(parseMasterEntryJson(masterEntryPath));
            case "embl", "ff" -> provider.addSource(new EmblEntryMetadataSource(parseMasterEntryEmbl(masterEntryPath)));
            default ->
                throw new CLIException("Unrecognized --master-entry file extension '." + ext
                        + "'. Supported: .json (MasterEntry JSON), .embl/.ff (EMBL flatfile).");
        }
        return provider;
    }

    private MasterEntryJsonMetadataSource parseMasterEntryJson(Path path) throws ExitException {
        if (!Files.exists(path)) {
            throw new NonExistingFile("The --master-entry file does not exist: " + path, null);
        }
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                    .build();
            MasterMetadata meta = mapper.readValue(path.toFile(), MasterMetadata.class);
            return new MasterEntryJsonMetadataSource(meta);
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The --master-entry file does not exist: " + path, e);
        } catch (IOException e) {
            throw new ReadException(
                    "Failed to read --master-entry JSON file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }

    private Entry parseMasterEntryEmbl(Path path) throws ExitException {
        if (!Files.exists(path)) {
            throw new NonExistingFile("The --master-entry file does not exist: " + path, null);
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            ReaderOptions readerOptions = new ReaderOptions();
            readerOptions.setIgnoreSequence(true);
            EmblEntryReader entryReader =
                    new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "master_reader", readerOptions);
            Entry masterEntry = null;
            while (entryReader.read() != null && entryReader.isEntry()) {
                masterEntry = entryReader.getEntry();
            }
            if (masterEntry == null) {
                throw new CLIException("No entry found in --master-entry EMBL file: " + path);
            }
            return masterEntry;
        } catch (IOException e) {
            throw new ReadException(
                    "Failed to read --master-entry EMBL file '%s': %s".formatted(path, e.getMessage()), e);
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
