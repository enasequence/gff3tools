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
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.DuplicateParameterException;
import uk.ac.ebi.embl.gff3tools.exception.ExitException;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.EffectiveRuleState;
import uk.ac.ebi.embl.gff3tools.validation.ParameterDescriptor;
import uk.ac.ebi.embl.gff3tools.validation.ParameterDescriptors;
import uk.ac.ebi.embl.gff3tools.validation.ParameterProvider;
import uk.ac.ebi.embl.gff3tools.validation.ParameterResolutionException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationConfig;
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

    @CommandLine.Option(
            names = "--params",
            paramLabel = "<key:value,key:value>",
            description = "Override tunable rule/fix parameters in the format RULE_NAME.PARAM_NAME:value")
    public CliParamsOption params;

    @CommandLine.Option(
            names = "--list-params",
            description =
                    "List every declared --params key (name, type, mandatory/optional, default, description) and exit")
    public boolean listParams = false;

    @CommandLine.Parameters(
            paramLabel = "[input-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    public Path inputFilePath;

    protected Map<String, RuleSeverity> getRuleOverrides() {
        return Optional.ofNullable(rules).map((r) -> r.rules()).orElse(new HashMap<>());
    }

    protected Map<String, String> getParamOverrides() {
        return Optional.ofNullable(params).map((p) -> p.params()).orElse(new HashMap<>());
    }

    /**
     * Always builds the explicit {@link ParameterProvider} from {@link #getParamOverrides()} (an
     * empty map when --params was not supplied), using the effective {@link ValidationConfig}
     * (properties-file defaults merged with the caller's ruleOverrides/fixOverrides) to resolve
     * OFF-rule detection. Must be called after ruleOverrides/fixOverrides are fully assembled by
     * the caller.
     *
     * @throws CLIException wrapping a {@link ParameterResolutionException} on any fail-fast
     *     violation (unknown key, key for an OFF rule/fix, missing mandatory, bad type), or
     *     wrapping a malformed {@code @Parameter} descriptor defect ({@link IllegalStateException}/
     *     {@link DuplicateParameterException}) so both classes of CLI-startup failure exit
     *     uniformly as USAGE(2) rather than the descriptor defect escaping unhandled as GENERAL(1)
     */
    protected ParameterProvider buildParameterProvider(
            Map<String, RuleSeverity> ruleOverrides, Map<String, Boolean> fixOverrides) throws CLIException {
        ValidationConfig effectiveConfig =
                EffectiveRuleState.mergedConfig(ValidationConfig.loadDefault(), ruleOverrides, Map.of(), fixOverrides);
        ParameterProvider provider = new ParameterProvider();
        try {
            provider.configure(getParamOverrides(), effectiveConfig);
        } catch (ParameterResolutionException e) {
            throw new CLIException(e.getMessage(), e);
        } catch (IllegalStateException | DuplicateParameterException e) {
            throw new CLIException("Malformed @Parameter declaration: " + e.getMessage(), e);
        }
        return provider;
    }

    /**
     * Renders the help listing of every declared --params key (namespaced key, type,
     * mandatory/optional, default, description), omitting any descriptor whose owning rule/fix is
     * effectively OFF under the same three-mechanism check used for fail-fast validation.
     */
    /**
     * Handles {@code --list-params} uniformly across every command: renders the declared-parameter
     * listing and returns {@code true} (signalling the caller to skip file/IO work and return)
     * when {@code --list-params} was supplied, {@code false} otherwise. Callers must check this
     * before any file/IO work begins, mirroring {@link #buildParameterProvider}'s placement.
     */
    protected boolean handleListParams(Map<String, RuleSeverity> ruleOverrides, Map<String, Boolean> fixOverrides) {
        if (!listParams) {
            return false;
        }
        log.info(renderParameterHelp(ruleOverrides, fixOverrides));
        return true;
    }

    protected String renderParameterHelp(Map<String, RuleSeverity> ruleOverrides, Map<String, Boolean> fixOverrides) {
        ValidationConfig effectiveConfig =
                EffectiveRuleState.mergedConfig(ValidationConfig.loadDefault(), ruleOverrides, Map.of(), fixOverrides);
        List<ParameterDescriptor> descriptors = ParameterDescriptors.scan();
        Set<String> offKeys = EffectiveRuleState.computeOffKeys(effectiveConfig, descriptors);

        StringBuilder sb = new StringBuilder("Declared --params keys:\n");
        for (ParameterDescriptor descriptor : descriptors) {
            if (offKeys.contains(descriptor.key())) {
                continue;
            }
            sb.append("  ")
                    .append(descriptor.key())
                    .append(" (")
                    .append(descriptor.type())
                    .append(", ")
                    .append(descriptor.mandatory() ? "mandatory" : "optional, default=" + descriptor.defaultValue())
                    .append("): ")
                    .append(descriptor.description())
                    .append('\n');
        }
        return sb.toString();
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
