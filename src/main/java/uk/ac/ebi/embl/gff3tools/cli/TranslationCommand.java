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
import uk.ac.ebi.embl.gff3tools.utils.OntologyTerm;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;

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
            defaultValue = "gff3_fasta",
            converter = TranslationMode.Converter.class)
    public TranslationMode translationMode;

    @CommandLine.Option(
            names = "-o",
            description = "Output file path. Defaults to <input-stem>.translated.gff3 or .translation.fasta")
    public Path outputPath;

    @Override
    public void run() {
        Map<String, RuleSeverity> ruleOverrides = getRuleOverrides();
        Path processDir = Optional.ofNullable(inputFilePath.getParent()).orElse(Path.of("."));

        try {
            CompositeSequenceProvider compositeProvider = buildCompositeProvider(sequenceSpecs, sequenceFormat);
            if (compositeProvider == null) {
                throw new RuntimeException(
                        "A sequence source is required. Provide --sequence or ensure a plugin supplies sequences.");
            }

            List<GFF3Annotation> annotations = new ArrayList<>();
            GFF3Header header;

            try (ValidationEngine validationEngine =
                    initValidationEngine(ruleOverrides, processDir, compositeProvider)) {

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
            }

            writeOutput(annotations, header);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
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
