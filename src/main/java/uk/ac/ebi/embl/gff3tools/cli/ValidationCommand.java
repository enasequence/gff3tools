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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.Gff3ProviderFactory;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.utils.GapOptionsValidator;
import uk.ac.ebi.embl.gff3tools.utils.GzipUtils;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

// Using pandoc CLI interface conventions
@CommandLine.Command(name = "validation", description = "Performs validations on gff3 files")
@Slf4j
public class ValidationCommand extends AbstractCommand {

    @CommandLine.Mixin
    public SequenceOptions sequenceOptions;

    @CommandLine.Option(
            names = {"--min-gap-length", "-mgl"},
            description = "Minimum run of N bases reported as a gap feature (only used when an output "
                    + "argument is given). Default: ${DEFAULT-VALUE}.")
    public int minGapLength = AnalysisContextProvider.DEFAULT_MIN_GAP_SIZE;

    @CommandLine.Option(
            names = {"--gap-type", "-gt"},
            description = "Optional INSDC gap_type for generated gap features (only used when an output "
                    + "argument is given). When set, gaps map to assembly_gap; otherwise a plain gap is emitted.")
    public String gapType;

    @CommandLine.Option(
            names = {"--linkage-evidence", "-le"},
            description = "Optional INSDC linkage_evidence for generated gap features (only used when an output "
                    + "argument is given). Only valid with a gap_type that requires it (e.g. \"within scaffold\").")
    public String linkageEvidence;

    /**
     * Absent (the default) means report-only: no gff3 is written, and this stays backward
     * compatible with the pre-existing behaviour. {@code -} means write the fixed gff3 to stdout.
     * Anything else is a file path to write the fixed gff3 to.
     */
    @CommandLine.Parameters(
            paramLabel = "[output-file]",
            defaultValue = "",
            showDefaultValue = CommandLine.Help.Visibility.NEVER,
            description = "Optional. Absent (default): report-only, nothing written. '-': write the fixed "
                    + "gff3 to stdout. Any other value: write the fixed gff3 to that file path. Writing "
                    + "output enables the GAP_GENERATION fix (see --min-gap-length/--gap-type/"
                    + "--linkage-evidence).")
    public Path outputFilePath;

    private int warningCount = 0;

    private void addToWarnCount(int c) {
        this.warningCount += c;
    }

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();

        String outputArg = outputFilePath.toString();
        boolean outputRequested = !outputArg.isEmpty();
        boolean toStdout = outputArg.equals("-");
        boolean writingToFile = outputRequested && !toStdout;

        try {
            if (outputRequested) {
                validateGapOptions();
            }

            List<FileSequenceSource> sources =
                    buildFastaSourceList(sequenceOptions.sequenceSpecs, sequenceOptions.sequenceFormat);
            CompositeSequenceProvider compositeProvider = Gff3ProviderFactory.buildCompositeProvider(sources);

            // GAP_GENERATION only matters to callers that keep the fixed annotation; when no output
            // is requested here, the fixed annotation is discarded so the fix is inert.
            Map<String, Boolean> fixOverrides = outputRequested ? Map.of() : Map.of("GAP_GENERATION", false);

            // Registered explicitly so --min-gap-length / --gap-type / --linkage-evidence reach
            // GapGenerationFix, overriding the classpath-scanned default instance. Only registered
            // when output is requested: elsewhere the options are inert and unvalidated, so they
            // must not reach AnalysisContext's constructor.
            AnalysisContextProvider analysisContextProvider = outputRequested
                    ? new AnalysisContextProvider(AnalysisType.UNKNOWN, minGapLength, gapType, linkageEvidence)
                    : null;
            ContextProvider<?>[] providers = analysisContextProvider != null
                    ? new ContextProvider<?>[] {compositeProvider, analysisContextProvider}
                    : new ContextProvider<?>[] {compositeProvider};

            if (writingToFile) {
                writeAtomically(
                        outputFilePath,
                        writer -> runValidation(ruleOverrides, fixOverrides, providers, true, writer, false));
            } else if (toStdout) {
                try (BufferedWriter stdout = createStdoutWriter()) {
                    runValidation(ruleOverrides, fixOverrides, providers, true, stdout, true);
                }
            } else {
                runValidation(ruleOverrides, fixOverrides, providers, false, null, false);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void runValidation(
            Map<String, RuleSeverity> ruleOverrides,
            Map<String, Boolean> fixOverrides,
            ContextProvider<?>[] providers,
            boolean outputRequested,
            BufferedWriter outputWriter,
            boolean toStdout)
            throws Exception {

        try (ValidationEngine validationEngine = initValidationEngine(ruleOverrides, fixOverrides, providers)) {

            // Re-reading the FASTA/translation section back out of the input requires reopening
            // it by path; that is impossible when reading from stdin, so it is skipped there.
            boolean hasRealInputFile = !isStdioSentinel(inputFilePath);
            Path effectiveInputPath = inputFilePath;
            Path decompressedTempFile = null;

            if (outputRequested && hasRealInputFile && GzipUtils.isGzipped(inputFilePath)) {
                // GFF3TranslationReader seeks directly against the input file's raw bytes to read
                // the FASTA/translation section, which cannot work against gzip-compressed bytes.
                // Decompress up front so gzip input round-trips the FASTA section like any other
                // file input, instead of silently losing it.
                decompressedTempFile = GzipUtils.decompressToTempFile(inputFilePath, "gff3tools-validation-", ".gff3");
                effectiveInputPath = decompressedTempFile;
            } else if (outputRequested && !hasRealInputFile) {
                log.warn("Reading from stdin: the FASTA/translation section, if any, cannot be "
                        + "re-read from a non-seekable stream and will be omitted from the output.");
            }

            try {
                try (BufferedReader inputReader = createInputReader(effectiveInputPath);
                        GFF3FileReader gff3Reader =
                                new GFF3FileReader(validationEngine, inputReader, effectiveInputPath)) {
                    GFF3Header header = gff3Reader.readHeader();
                    List<GFF3Annotation> annotations = outputRequested ? new ArrayList<>() : null;
                    gff3Reader.read(annotation -> {
                        // The reader replays a final null annotation at EOF when the file has no
                        // annotations at all (e.g. a header-only file); nothing to collect there.
                        if (outputRequested && annotation != null) {
                            annotations.add(annotation);
                        }
                        List<ValidationException> warnings = validationEngine.getParsingWarnings();
                        if (warnings != null && !warnings.isEmpty()) {
                            for (ValidationException e : warnings) {
                                log.warn("WARNING: %s".formatted(e.getMessage()));
                            }
                            addToWarnCount(warnings.size());
                            warnings.clear();
                        }
                    });

                    // Check for collected errors at end of processing
                    int errorCount = validationEngine.getCollectedErrors().size();
                    if (errorCount > 0) {
                        reportSummary("Validation completed with %d error(s)".formatted(errorCount), toStdout);
                        validationEngine.throwIfErrorsCollected();
                    } else if (warningCount > 0) {
                        reportSummary("The file passed validations with %d warnings".formatted(warningCount), toStdout);
                    } else {
                        reportSummary("The file has passed all validations!", toStdout);
                    }

                    if (outputRequested) {
                        // TranslationFix captures pre-existing translations and computes new ones
                        // into TranslationState during validation; without wiring it through here,
                        // fixed output would silently lose them instead of writing a ##FASTA section.
                        // Only used when it actually holds something: an empty state (e.g. no
                        // --sequence given, so TranslationFix never ran) must not pre-empt the
                        // raw-offset fallback that re-reads an input file's own ##FASTA section.
                        TranslationState contextTranslationState =
                                validationEngine.getContext().contains(TranslationState.class)
                                        ? validationEngine.getContext().get(TranslationState.class)
                                        : null;
                        TranslationState translationState =
                                contextTranslationState != null && contextTranslationState.hasResolvedTranslations()
                                        ? contextTranslationState
                                        : null;
                        GFF3File gff3File = new GFF3File(
                                header,
                                gff3Reader.gff3Species,
                                annotations,
                                hasRealInputFile ? gff3Reader : null,
                                null,
                                false,
                                null,
                                translationState);
                        gff3File.writeGFF3String(outputWriter);
                    }
                }
            } finally {
                if (decompressedTempFile != null) {
                    try {
                        Files.deleteIfExists(decompressedTempFile);
                    } catch (IOException e) {
                        log.warn("Failed to delete temporary file: {}", decompressedTempFile);
                    }
                }
            }
        }
    }

    /**
     * Logs {@code message} at INFO as usual, and when writing fixed output to stdout also prints
     * it directly to stderr: in that mode the root logger is floored to WARN (see
     * {@link #createStdoutWriter()}) to keep stdout clean, which would otherwise silently drop
     * this pass/fail/warning-count summary along with it.
     */
    private void reportSummary(String message, boolean toStdout) {
        log.info(message);
        if (toStdout) {
            System.err.println(message);
        }
    }

    /**
     * Fails fast with a clear usage message for invalid gap options, instead of surfacing the
     * {@code IllegalArgumentException} that {@code AnalysisContext} would otherwise throw when the
     * provider is constructed. The same rules are enforced there as a backstop.
     */
    private void validateGapOptions() throws CLIException {
        if (minGapLength < 1) {
            throw new CLIException("--min-gap-length must be at least 1, but was " + minGapLength);
        }
        Optional<String> problem = GapOptionsValidator.validate(gapType, linkageEvidence);
        if (problem.isPresent()) {
            throw new CLIException(problem.get() + " (see --gap-type / --linkage-evidence)");
        }
    }
}
