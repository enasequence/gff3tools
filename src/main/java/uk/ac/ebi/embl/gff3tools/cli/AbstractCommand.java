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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
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
        return initValidationEngine(ruleOverrides, List.of(), additionalProviders);
    }

    /**
     * Builds a {@link ValidationEngine}, additionally registering explicit {@link Fix} instances
     * (e.g. a CLI-parameterised {@code GapRegenerationFix}) that override any classpath-discovered
     * fix registered under the same {@code @Gff3Fix} name.
     */
    protected ValidationEngine initValidationEngine(
            Map<String, RuleSeverity> ruleOverrides, List<Fix> extraFixes, ContextProvider<?>... additionalProviders) {

        ValidationEngineBuilder builder =
                new ValidationEngineBuilder().overrideMethodRules(ruleOverrides).failFast(failFast);

        for (Fix fix : extraFixes) {
            builder.withFix(fix);
        }

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
