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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.Gff3ProviderFactory;
import uk.ac.ebi.embl.gff3tools.exception.CLIException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.fftogff3.FastaToGff3Converter;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.utils.GapOptionsValidator;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.fix.GapRegenerationFix;
import uk.ac.ebi.embl.gff3tools.validation.meta.Fix;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

@CommandLine.Command(
        name = "fix",
        description = "Runs the full fix pipeline (including gap regeneration) over a GFF3 + FASTA pair "
                + "and writes the corrected GFF3")
@Slf4j
public class FileFixCommand extends AbstractCommand {

    @CommandLine.Option(names = "-gff3", description = "Gff3 input file", required = true)
    public Path gff3InputFile;

    @CommandLine.Option(names = "-o", description = "Fixed gff3 output file", required = true)
    public Path outputFilePath;

    @CommandLine.Option(
            names = {"--min-gap-length", "-mgl"},
            description = "Minimum run of N bases reported as a gap feature. Default: ${DEFAULT-VALUE}.")
    public int minGapLength = FastaToGff3Converter.DEFAULT_MIN_GAP_LENGTH;

    @CommandLine.Option(
            names = {"--gap-type", "-gt"},
            description = "Optional INSDC gap_type for regenerated gap features. When set, gaps map to "
                    + "assembly_gap; otherwise a plain gap is emitted.")
    public String gapType;

    @CommandLine.Option(
            names = {"--linkage-evidence", "-le"},
            description = "Optional INSDC linkage_evidence for regenerated gap features. Only valid with a "
                    + "gap_type that requires it (e.g. \"within scaffold\").")
    public String linkageEvidence;

    @CommandLine.Mixin
    public SequenceOptions sequenceOptions;

    private int warningCount = 0;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();
        Path tempFile = null;

        try {
            // Fail fast on invalid gap options before touching any files.
            GapOptionsValidator.validate(gapType, linkageEvidence);

            validateFile(gff3InputFile, ConversionFileFormat.gff3.name());
            validateOutputFile(outputFilePath);

            List<FileSequenceSource> sources =
                    buildFastaSourceList(sequenceOptions.sequenceSpecs, sequenceOptions.sequenceFormat);
            if (sources.isEmpty()) {
                throw new CLIException(
                        "--sequence is required for the fix command; gap regeneration needs a FASTA source.");
            }

            CompositeSequenceProvider compositeProvider = Gff3ProviderFactory.buildCompositeProvider(sources);
            FastaHeaderProvider headerProvider =
                    Gff3ProviderFactory.buildHeaderProvider(sources, sequenceOptions.fastaHeaderPath);

            List<Fix> extraFixes = List.of(new GapRegenerationFix(minGapLength, gapType, linkageEvidence));

            tempFile = Files.createTempFile("gff3tools-fix-", ".tmp");
            final Path effectiveOutputPath = tempFile;

            try (BufferedReader reader = Files.newBufferedReader(gff3InputFile);
                    BufferedWriter writer = Files.newBufferedWriter(effectiveOutputPath)) {
                try (ValidationEngine validationEngine =
                                initValidationEngine(ruleOverrides, extraFixes, compositeProvider, headerProvider);
                        GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, reader, gff3InputFile)) {

                    GFF3Header header = gff3Reader.readHeader();
                    if (header != null) {
                        header.writeGFF3String(writer);
                    }

                    gff3Reader.read(annotation -> {
                        annotation.writeGFF3String(writer);
                        List<ValidationException> warnings = validationEngine.getParsingWarnings();
                        if (warnings != null && !warnings.isEmpty()) {
                            for (ValidationException w : warnings) {
                                log.warn("WARNING: %s".formatted(w.getMessage()));
                            }
                            warningCount += warnings.size();
                            warnings.clear();
                        }
                    });

                    int errorCount = validationEngine.getCollectedErrors().size();
                    if (errorCount > 0) {
                        log.info("Fix completed with %d error(s)".formatted(errorCount));
                        validationEngine.throwIfErrorsCollected();
                    } else if (warningCount > 0) {
                        log.info("Fix completed with %d warnings".formatted(warningCount));
                    } else {
                        log.info("Fix completed successfully");
                    }
                }
            }

            // Fix succeeded - move temp file to final destination atomically.
            try {
                Files.move(
                        tempFile, outputFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, outputFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            tempFile = null;
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

    protected void validateFile(Path filePath, String fileExtension) throws CLIException {

        if (!Files.exists(filePath)) {
            throw new CLIException("File does not exist: " + filePath);
        }

        String actualExtension = getFileExtension(filePath)
                .orElseThrow(() -> new CLIException("File has no extension: " + filePath.getFileName()));

        if (!fileExtension.equalsIgnoreCase(actualExtension)) {
            throw new CLIException("Invalid %s file: %s".formatted(fileExtension, filePath.getFileName()));
        }

        if (!Files.isReadable(filePath)) {
            throw new CLIException("File is not readable: " + filePath);
        }
    }

    protected void validateOutputFile(Path filePath) throws CLIException {

        String fileExtension = getFileExtension(filePath)
                .orElseThrow(() -> new CLIException("File has no extension: " + filePath.getFileName()));

        if (!fileExtension.equalsIgnoreCase(ConversionFileFormat.gff3.name())) {
            throw new CLIException("Invalid output file format %s, Expected gff3".formatted(fileExtension));
        }

        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            throw new CLIException("Output directory does not exist: " + parent);
        }

        if (Files.exists(filePath) && !Files.isWritable(filePath)) {
            throw new CLIException("Output file is not writable: " + filePath);
        }
    }
}
