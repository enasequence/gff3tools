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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.vavr.Function0;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.metadata.*;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.CliFastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FileFastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

@Slf4j
public abstract class AbstractCommand implements Runnable {

    @CommandLine.Option(
            names = "--fail-fast",
            description = "Stop processing on first error instead of collecting all errors")
    public boolean failFast = false;

    @CommandLine.Option(
            names = "--rules",
            paramLabel = "<key:value,key:value>",
            description = "Specify rules in the format key:value")
    public CliRulesOption rules;

    @CommandLine.Option(names = "-f", description = "The type of the file to be converted")
    public ConversionFileFormat fromFileType;

    @CommandLine.Option(names = "-t", description = "The type of the file to convert to")
    public ConversionFileFormat toFileType;

    @CommandLine.Option(
            names = {"--master-entry", "-m"},
            description = "Optional master entry file. Accepts MasterEntry JSON (.json) or EMBL flatfile (.embl/.ff).")
    public Path masterFilePath;

    @CommandLine.Parameters(
            paramLabel = "[input-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path inputFilePath;

    protected Map<String, RuleSeverity> getRuleOverrides() {
        return Optional.ofNullable(rules).map((r) -> r.rules()).orElse(new HashMap<>());
    }

    protected ValidationEngine initValidationEngine(
            Map<String, RuleSeverity> ruleOverrides, ContextProvider<?>... additionalProviders) {

        ValidationEngineBuilder builder =
                new ValidationEngineBuilder().overrideMethodRules(ruleOverrides).failFast(failFast);

        for (ContextProvider<?> provider : additionalProviders) {
            builder.withProvider(provider);
        }

        return builder.build();
    }

    @FunctionalInterface
    interface NewPipeFunction<T> {
        T apply(Path p, Charset c) throws IOException;
    }

    protected <T> T getPipe(NewPipeFunction<T> newFilePipe, Function0<T> newStdPipe, Path filePath)
            throws ExitException {
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

    protected static Optional<String> getFileExtension(Path path) {
        String name = path.getFileName().toString();

        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }

        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? Optional.of(name.substring(dot + 1)) : Optional.empty();
    }

    // -- Sequence helpers shared by translate / validation / conversion --

    protected record ParsedSequenceSpec(String key, Path path) {}

    /**
     * Parses a {@code --sequence} spec into an optional key and a path.
     *
     * <p>Format: {@code [key:]path}. The key is separated by the first colon that is not
     * part of the path (i.e., the character before the colon contains no path separators).
     */
    protected ParsedSequenceSpec parseSequenceSpec(String spec) {
        int colonIdx = spec.indexOf(':');
        if (colonIdx > 0) {
            String possibleKey = spec.substring(0, colonIdx);
            if (!possibleKey.contains("/") && !possibleKey.contains("\\")) {
                String pathStr = spec.substring(colonIdx + 1);
                return new ParsedSequenceSpec(possibleKey, Path.of(pathStr));
            }
        }
        return new ParsedSequenceSpec(null, Path.of(spec));
    }

    /**
     * Resolve the sequence format from an explicit override or the file extension.
     */
    protected SequenceFormat resolveSequenceFormat(Path path, SequenceFormat explicitFormat) {
        if (explicitFormat != null) {
            return explicitFormat;
        }
        String ext = getFileExtension(path)
                .orElseThrow(() -> new RuntimeException("Cannot infer sequence format from file extension. "
                        + "Use --sequence-format to specify the format explicitly."));
        return switch (ext.toLowerCase()) {
            case "fasta", "fa", "fna" -> SequenceFormat.fasta;
            case "seq" -> SequenceFormat.plain;
            default ->
                throw new RuntimeException("Unrecognized sequence file extension: ." + ext
                        + ". Use --sequence-format to specify the format explicitly.");
        };
    }

    /**
     * Builds a list of {@link FileSequenceSource} instances from the parsed {@code --sequence} specs.
     * Returns an empty list if no specs are provided. Sources are created but not yet initialized.
     */
    protected List<FileSequenceSource> buildFastaSourceList(List<String> sequenceSpecs, SequenceFormat sequenceFormat) {
        if (sequenceSpecs == null || sequenceSpecs.isEmpty()) {
            return List.of();
        }
        List<FileSequenceSource> sources = new ArrayList<>();
        for (String spec : sequenceSpecs) {
            ParsedSequenceSpec parsed = parseSequenceSpec(spec);
            SequenceFormat resolvedFormat = resolveSequenceFormat(parsed.path(), sequenceFormat);
            sources.add(new FileSequenceSource(parsed.path(), resolvedFormat, parsed.key()));
        }
        return sources;
    }

    /**
     * Builds a {@link CompositeSequenceProvider} from parsed {@code --sequence} specs.
     * Returns an empty provider (no sources) if no specs are provided.
     */
    protected CompositeSequenceProvider buildCompositeProvider(
            List<String> sequenceSpecs, SequenceFormat sequenceFormat) {
        return buildCompositeProvider(buildFastaSourceList(sequenceSpecs, sequenceFormat));
    }

    /**
     * Builds a {@link CompositeSequenceProvider} from pre-built sequence sources.
     */
    protected CompositeSequenceProvider buildCompositeProvider(List<FileSequenceSource> sources) {
        CompositeSequenceProvider compositeProvider = new CompositeSequenceProvider();
        for (FileSequenceSource source : sources) {
            compositeProvider.addSource(source);
        }
        return compositeProvider;
    }

    /**
     * Builds a {@link FastaHeaderProvider} from pre-built sequence sources and optional
     * {@code --fasta-header} path. FASTA-embedded headers take precedence over the CLI header.
     *
     * <p>Callers should pass the same sources list they used for
     * {@link #buildCompositeProvider(List)} so each FASTA file is opened only once.
     */
    protected FastaHeaderProvider buildHeaderProvider(List<FileSequenceSource> sources, Path fastaHeaderPath)
            throws CLIException {
        FastaHeaderProvider headerProvider = new FastaHeaderProvider();

        // Register FASTA-embedded header sources (highest priority).
        // getSeqIdToHeader() returns an empty map for non-FASTA sources, so no format check needed.
        for (FileSequenceSource fss : sources) {
            Map<String, FastaHeader> headerMap = fss.getSeqIdToHeader();
            if (!headerMap.isEmpty()) {
                headerProvider.addSource(new FileFastaHeaderSource(headerMap));
            }
        }

        // Register CLI global header source (lowest priority / fallback)
        if (fastaHeaderPath != null) {
            FastaHeader cliHeader = parseFastaHeaderJson(fastaHeaderPath);
            headerProvider.addSource(new CliFastaHeaderSource(cliHeader));
        }

        return headerProvider;
    }

    /**
     * Builds an {@link AnnotationMetadataProvider} from pre-built sequence sources and optional
     * {@code --fasta-header} and {@code --master-entry} paths.
     *
     * <p>Priority order (highest first):
     * <ol>
     *   <li>MasterEntry JSON or EMBL flatfile (--master-entry)</li>
     *   <li>FASTA-embedded headers (per-seqId)</li>
     *   <li>CLI --fasta-header JSON (global fallback)</li>
     * </ol>
     *
     * <p>The first source that returns metadata for a given seqId wins entirely;
     * no field-level merging is performed.
     */
    protected AnnotationMetadataProvider buildMetadataProvider(
            List<FileSequenceSource> sources, Path fastaHeaderPath, Path masterEntryPath) throws CLIException {
        AnnotationMetadataProvider provider = new AnnotationMetadataProvider();

        // Register --master-entry source (highest priority)
        if (masterEntryPath != null) {
            provider.addSource(parseMasterEntrySource(masterEntryPath));
        }

        // Register FASTA-embedded header sources (medium priority)
        for (FileSequenceSource fss : sources) {
            Map<String, FastaHeader> headerMap = fss.getSeqIdToHeader();
            if (!headerMap.isEmpty()) {
                provider.addSource(new EmbeddedFastaMetadataSource(headerMap));
            }
        }

        // Register CLI --fasta-header JSON (lowest priority)
        if (fastaHeaderPath != null) {
            FastaHeader cliHeader = parseFastaHeaderJson(fastaHeaderPath);
            provider.addSource(new CliJsonMetadataSource(cliHeader));
        }

        return provider;
    }

    /**
     * Parses a master entry file based on its extension.
     * .json -> MasterEntry JSON deserialized into AnnotationMetadata
     * .embl/.ff -> EMBL flatfile parsed into Entry and adapted to AnnotationMetadata
     */
    protected AnnotationMetadataSource parseMasterEntrySource(Path path) throws CLIException {
        String ext = getFileExtension(path).orElse("").toLowerCase();
        return switch (ext) {
            case "json" -> parseMasterEntryJson(path);
            case "embl", "ff" -> parseMasterEntryEmbl(path);
            default ->
                throw new CLIException("Unrecognized --master-entry file extension '." + ext
                        + "'. Supported: .json (MasterEntry JSON), .embl/.ff (EMBL flatfile).");
        };
    }

    /**
     * Parses a MasterEntry JSON file into an AnnotationMetadata.
     */
    private MasterEntryJsonMetadataSource parseMasterEntryJson(Path path) throws CLIException {
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                    .build();
            AnnotationMetadata meta = mapper.readValue(path.toFile(), AnnotationMetadata.class);
            return new MasterEntryJsonMetadataSource(meta);
        } catch (Exception e) {
            throw new CLIException(
                    "Failed to parse --master-entry JSON file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }

    /**
     * Parses an EMBL flatfile master entry into an EmblEntryMetadataSource adapter.
     */
    private EmblEntryMetadataSource parseMasterEntryEmbl(Path path) throws CLIException {
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
            return new EmblEntryMetadataSource(masterEntry);
        } catch (CLIException e) {
            throw e;
        } catch (Exception e) {
            throw new CLIException(
                    "Failed to parse --master-entry EMBL file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }

    /**
     * Parses a JSON file at the given path into a {@link FastaHeader}.
     * Fails fast with a descriptive error if the file is missing or malformed.
     */
    private FastaHeader parseFastaHeaderJson(Path path) throws CLIException {
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                    .build();
            return mapper.readValue(path.toFile(), FastaHeader.class);
        } catch (Exception e) {
            throw new CLIException(
                    "Failed to parse --fasta-header JSON file '%s': %s".formatted(path, e.getMessage()), e);
        }
    }
}
