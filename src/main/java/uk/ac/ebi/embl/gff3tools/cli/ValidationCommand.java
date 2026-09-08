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
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.AnalysisType;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

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
            showDefaultValue = CommandLine.Help.Visibility.NEVER)
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
                        outputFilePath, writer -> runValidation(ruleOverrides, fixOverrides, providers, true, writer));
            } else if (toStdout) {
                try (BufferedWriter stdout = createStdoutWriter()) {
                    runValidation(ruleOverrides, fixOverrides, providers, true, stdout);
                }
            } else {
                runValidation(ruleOverrides, fixOverrides, providers, false, null);
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
            BufferedWriter outputWriter)
            throws Exception {

        try (ValidationEngine validationEngine = initValidationEngine(ruleOverrides, fixOverrides, providers)) {

            try (BufferedReader inputReader = createInputReader(inputFilePath);
                    GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, inputReader, inputFilePath)) {
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
                    log.info("Validation completed with %d error(s)".formatted(errorCount));
                    validationEngine.throwIfErrorsCollected();
                } else if (warningCount > 0) {
                    log.info("The file passed validations with %d warnings".formatted(warningCount));
                } else {
                    log.info("The file has passed all validations!");
                }

                if (outputRequested) {
                    // Re-reading the FASTA/translation section back out of the input requires
                    // reopening it by path, which is impossible when reading from stdin - skip it
                    // rather than crash in that case.
                    boolean hasRealInputFile = !inputFilePath.toString().isEmpty();
                    GFF3File gff3File = new GFF3File(
                            header,
                            gff3Reader.gff3Species,
                            annotations,
                            hasRealInputFile ? gff3Reader : null,
                            null,
                            false,
                            null,
                            null);
                    gff3File.writeGFF3String(outputWriter);
                }
            }
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
