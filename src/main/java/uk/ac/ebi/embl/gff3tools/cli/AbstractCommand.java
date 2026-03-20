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

import io.vavr.Function0;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceReaderFactory;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

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

    @CommandLine.Option(names = "-m", description = "Optional master file")
    public Path masterFilePath;

    @CommandLine.Parameters(
            paramLabel = "[input-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path inputFilePath;

    protected Map<String, RuleSeverity> getRuleOverrides() {
        return Optional.ofNullable(rules).map((r) -> r.rules()).orElse(new HashMap<>());
    }

    protected ValidationEngine initValidationEngine(Map<String, RuleSeverity> ruleOverrides, Path processDir) {

        if (!Files.isDirectory(processDir) || !Files.isWritable(processDir)) {
            throw new RuntimeException(String.format("The directory {%s} is not writable.", processDir));
        }

        log.info("Running with process directory: {}", processDir);
        return new ValidationEngineBuilder()
                .overrideMethodRules(ruleOverrides)
                .failFast(failFast)
                .build();
    }

    protected ValidationEngine initValidationEngine(
            Map<String, RuleSeverity> ruleOverrides, Path processDir, ContextProvider<?>... additionalProviders) {

        if (!Files.isDirectory(processDir) || !Files.isWritable(processDir)) {
            throw new RuntimeException(String.format("The directory {%s} is not writable.", processDir));
        }

        log.info("Running with process directory: {}", processDir);
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

    // ── Sequence helpers shared by translate / validation / conversion ──

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
        String lower = ext.toLowerCase();
        if (lower.equals("fasta") || lower.equals("fa") || lower.equals("fna")) {
            return SequenceFormat.fasta;
        } else if (lower.equals("seq")) {
            return SequenceFormat.plain;
        }
        throw new RuntimeException("Unrecognized sequence file extension: ." + ext
                + ". Use --sequence-format to specify the format explicitly.");
    }

    protected SequenceReader openSequenceReader(Path path, SequenceFormat format, String key) throws Exception {
        return switch (format) {
            case fasta -> SequenceReaderFactory.readFasta(path.toFile());
            case plain -> {
                String accessionId = (key != null) ? key : "0";
                yield SequenceReaderFactory.readPlainSequence(path.toFile(), accessionId);
            }
        };
    }
}
