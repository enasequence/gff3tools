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
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Annotation;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3File;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.gff3.writer.TranslationWriter;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceReaderFactory;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceProvider;

@CommandLine.Command(
        name = "translate",
        description = "Translate CDS features in GFF3 files using nucleotide sequences")
@Slf4j
public class TranslationCommand extends AbstractCommand {

    @CommandLine.Option(
            names = "--sequence",
            description = "Sequence source. Repeatable. Use path for FASTA files (IDs from headers) "
                    + "or key:path for plain sequences (key = GFF3 seqId). "
                    + "Examples: --sequence seqs.fasta --sequence chr1:chr1.seq")
    public List<String> sequenceSpecs;

    @CommandLine.Option(
            names = "--sequence-format",
            description = "Format of the sequence file: fasta, plain. Inferred from extension if omitted.")
    public SequenceFormat sequenceFormat;

    @CommandLine.Option(
            names = "--translation-mode",
            description = "Output mode: gff3-fasta, fasta, attribute (default: gff3-fasta)",
            defaultValue = "gff3_fasta")
    public TranslationMode translationMode;

    @CommandLine.Option(
            names = "-o",
            description = "Output file path. Defaults to <input-stem>.translated.gff3 or .translation.fasta")
    public Path outputPath;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();
        Path processDir = Optional.ofNullable(inputFilePath.getParent()).orElse(Path.of("."));
        List<SequenceReader> openedReaders = new ArrayList<>();

        try {
            if (sequenceSpecs == null || sequenceSpecs.isEmpty()) {
                throw new RuntimeException(
                        "A sequence source is required. Provide --sequence or ensure a plugin supplies sequences.");
            }

            CompositeSequenceProvider compositeProvider = new CompositeSequenceProvider();

            for (String spec : sequenceSpecs) {
                ParsedSequenceSpec parsed = parseSequenceSpec(spec);
                SequenceFormat format = resolveSequenceFormat(parsed.path());
                SequenceReader reader = openSequenceReader(parsed.path(), format, parsed.key());
                openedReaders.add(reader);
                compositeProvider.addSource(new FileSequenceProvider(reader, parsed.key()));
            }

            try {
                ValidationEngine validationEngine = initValidationEngine(ruleOverrides, processDir, compositeProvider);

                List<GFF3Annotation> annotations = new ArrayList<>();
                GFF3Header header;

                try (BufferedReader inputReader = getPipe(
                                Files::newBufferedReader,
                                () -> new BufferedReader(new InputStreamReader(System.in)),
                                inputFilePath);
                        GFF3FileReader gff3Reader = new GFF3FileReader(validationEngine, inputReader, inputFilePath)) {

                    header = gff3Reader.readHeader();
                    gff3Reader.read(annotation -> {
                        annotations.add(annotation);
                        List<ValidationException> warnings = validationEngine.getParsingWarnings();
                        if (warnings != null && !warnings.isEmpty()) {
                            for (ValidationException e : warnings) {
                                log.warn("WARNING: %s".formatted(e.getMessage()));
                            }
                            warnings.clear();
                        }
                    });

                    int errorCount = validationEngine.getCollectedErrors().size();
                    if (errorCount > 0) {
                        log.info("Translation completed with %d error(s)".formatted(errorCount));
                        validationEngine.throwIfErrorsCollected();
                    } else {
                        log.info("Translation completed successfully");
                    }
                }

                writeOutput(annotations, header);
            } finally {
                for (SequenceReader reader : openedReaders) {
                    try {
                        reader.close();
                    } catch (Exception e) {
                        log.warn("Failed to close sequence reader: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Parses a --sequence spec into an optional key and a path.
     *
     * <p>Format: {@code [key:]path}. The key is separated by the first colon that is not
     * part of the path (i.e., the character before the colon contains no path separators).
     */
    private ParsedSequenceSpec parseSequenceSpec(String spec) {
        int colonIdx = spec.indexOf(':');
        if (colonIdx > 0) {
            String possibleKey = spec.substring(0, colonIdx);
            // If the part before the colon has no path separators, treat it as a key
            if (!possibleKey.contains("/") && !possibleKey.contains("\\")) {
                String pathStr = spec.substring(colonIdx + 1);
                return new ParsedSequenceSpec(possibleKey, Path.of(pathStr));
            }
        }
        return new ParsedSequenceSpec(null, Path.of(spec));
    }

    private record ParsedSequenceSpec(String key, Path path) {}

    private SequenceFormat resolveSequenceFormat(Path path) {
        if (sequenceFormat != null) {
            return sequenceFormat;
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

    private SequenceReader openSequenceReader(Path path, SequenceFormat format, String key) throws Exception {
        return switch (format) {
            case fasta -> SequenceReaderFactory.readFasta(path.toFile());
            case plain -> {
                String accessionId = (key != null) ? key : "0";
                yield SequenceReaderFactory.readPlainSequence(path.toFile(), accessionId);
            }
        };
    }

    private void writeOutput(List<GFF3Annotation> annotations, GFF3Header header) throws Exception {
        switch (translationMode) {
            case attribute -> log.info("Translation mode 'attribute': translations set as in-memory attributes only.");
            case fasta -> writeFastaOutput(annotations);
            case gff3_fasta -> writeGff3FastaOutput(annotations, header);
        }
    }

    private Path resolveOutputPath(String suffix) {
        if (outputPath != null) {
            return outputPath;
        }
        String inputName = inputFilePath.getFileName().toString();
        int dot = inputName.lastIndexOf('.');
        String stem = (dot > 0) ? inputName.substring(0, dot) : inputName;
        Path parent = Optional.ofNullable(inputFilePath.getParent()).orElse(Path.of("."));
        return parent.resolve(stem + suffix);
    }

    private void writeFastaOutput(List<GFF3Annotation> annotations) throws Exception {
        Path outPath = resolveOutputPath(".translation.fasta");
        try (BufferedWriter writer = Files.newBufferedWriter(outPath)) {
            writeTranslationEntries(writer, annotations);
        }
        log.info("Translation FASTA written to: {}", outPath);
    }

    private void writeGff3FastaOutput(List<GFF3Annotation> annotations, GFF3Header header) throws Exception {
        Path outPath = resolveOutputPath(".translated.gff3");
        Path tempFasta = Files.createTempFile("gff3-translation", ".fasta");
        try {
            // Write translations to temp FASTA file
            try (BufferedWriter fastaWriter = Files.newBufferedWriter(tempFasta)) {
                writeTranslationEntries(fastaWriter, annotations);
            }

            // Strip translation attributes from features
            for (GFF3Annotation annotation : annotations) {
                for (GFF3Feature feature : annotation.getFeatures()) {
                    feature.removeAttributeList("translation");
                }
            }

            // Build GFF3File with FASTA section and write
            GFF3File gff3File = GFF3File.builder()
                    .header(header)
                    .annotations(annotations)
                    .fastaFilePath(tempFasta)
                    .build();

            try (BufferedWriter outputWriter = Files.newBufferedWriter(outPath)) {
                gff3File.writeGFF3String(outputWriter);
            }
            log.info("GFF3 with FASTA section written to: {}", outPath);
        } finally {
            Files.deleteIfExists(tempFasta);
        }
    }

    private void writeTranslationEntries(BufferedWriter writer, List<GFF3Annotation> annotations) {
        for (GFF3Annotation annotation : annotations) {
            String accession = annotation.getAccession();
            for (GFF3Feature feature : annotation.getFeatures()) {
                if (!OntologyTerm.CDS.name().equals(feature.getName())) {
                    continue;
                }
                Optional<String> translation = feature.getAttribute("translation");
                if (translation.isEmpty() || translation.get().isEmpty()) {
                    continue;
                }
                String featureId = feature.getId().orElse(feature.getName());
                String key = TranslationWriter.getTranslationKey(accession, featureId);
                TranslationWriter.writeTranslation(writer, key, translation.get());
            }
        }
    }
}
