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
import io.vavr.Function0;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.utils.GzipUtils;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
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
        return initValidationEngine(ruleOverrides, Map.of(), additionalProviders);
    }

    /**
     * Builds a {@link ValidationEngine}, additionally toggling individual fixes by their
     * {@code @FixMethod.rule()}. Use it to keep a fix off a command where its output would be
     * discarded.
     */
    protected ValidationEngine initValidationEngine(
            Map<String, RuleSeverity> ruleOverrides,
            Map<String, Boolean> fixOverrides,
            ContextProvider<?>... additionalProviders) {

        ValidationEngineBuilder builder = new ValidationEngineBuilder()
                .overrideMethodRules(ruleOverrides)
                .overrideMethodFixs(fixOverrides)
                .failFast(failFast);

        // Providers gate their own registration via ContextProvider#isActive(). An empty
        // FastaHeaderProvider (no header source supplied) reports inactive and is kept off the
        // context, so header-aware rules such as FASTA_HEADER_MAPPING stay inert unless a caller
        // supplies a real header source.
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

    /** True for the two tokens meaning "use standard I/O instead of a real file": absent (empty) or {@code -}. */
    protected static boolean isStdioSentinel(Path path) {
        String s = path.toString();
        return s.isEmpty() || s.equals("-");
    }

    /**
     * Creates a BufferedReader for {@code filePath}, auto-detecting and transparently
     * decompressing gzip input. An empty or {@code -} path falls back to stdin, matching
     * {@link #getPipe}'s convention.
     */
    protected BufferedReader createInputReader(Path filePath) throws NonExistingFile, ReadException {
        if (filePath == null || isStdioSentinel(filePath)) {
            return new BufferedReader(new InputStreamReader(System.in));
        }
        boolean gzipped = GzipUtils.isGzipped(filePath);
        try {
            InputStream in =
                    gzipped ? new GZIPInputStream(Files.newInputStream(filePath)) : Files.newInputStream(filePath);
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            throw new NonExistingFile("The file does not exist: " + filePath, e);
        } catch (IOException e) {
            throw new ReadException("Error opening file: " + filePath, e);
        }
    }

    protected BufferedWriter createStdoutWriter() {
        // Suppress INFO logs while writing to stdout to avoid mixing log output with file content.
        // WARN/ERROR already route to a dedicated stderr appender (see logback.xml) rather than
        // the stdout one, so they stay visible without needing to be muted here.
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.WARN);
        return new BufferedWriter(new OutputStreamWriter(System.out));
    }

    @FunctionalInterface
    protected interface WriterAction {
        void run(BufferedWriter writer) throws Exception;
    }

    /**
     * Runs {@code action} against a temp file and only moves it into place at {@code outputPath}
     * on success, so a failure never leaves a partial or corrupt file at the destination. The temp
     * file lives in the system temp directory (see -Djava.io.tmpdir) for control in pipeline
     * environments.
     */
    protected void writeAtomically(Path outputPath, WriterAction action) throws Exception {
        Path tempFile = Files.createTempFile("gff3tools-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
                action.run(writer);
            }
            try {
                Files.move(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // ATOMIC_MOVE fails across filesystems; fall back to a regular move
                Files.move(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException deleteEx) {
                log.warn("Failed to delete temporary file: {}", tempFile);
            }
            throw e;
        }
    }

    // ── CLI sequence spec helpers ─────────────────────────────────────────────

    protected record ParsedSequenceSpec(String key, Path path) {}

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

    protected static Optional<String> getFileExtension(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".gz")) {
            name = name.substring(0, name.length() - 3);
        }
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1) ? Optional.of(name.substring(dot + 1)) : Optional.empty();
    }
}
