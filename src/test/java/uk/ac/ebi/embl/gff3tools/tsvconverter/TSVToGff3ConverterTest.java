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
package uk.ac.ebi.embl.gff3tools.tsvconverter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.feature.SourceFeature;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReader;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReaderFactory;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.JsonHeaderParser;
import uk.ac.ebi.embl.gff3tools.utils.SourceFeatureDTO;
import uk.ac.ebi.embl.gff3tools.utils.SourceFeatureUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

public class TSVToGff3ConverterTest {

    Path inputDir = Path.of("src/test/resources/tsvtogff3/all_templates");
    final String[] allTemplates = {
        "ERT000002-rRNA.tsv",
        "ERT000003-EST-1.tsv",
        "ERT000006-SCM.tsv",
        "ERT000009-ITS.tsv",
        "ERT000020-COI.tsv",
        "ERT000024-GSS-1.tsv",
        "ERT000028-SVC.tsv",
        "ERT000029-SCGD.tsv",
        "ERT000030-MHC1.tsv",
        "ERT000032-matK.tsv",
        "ERT000034-Dloop.tsv",
        "ERT000035-IGS.tsv",
        "ERT000036-MHC2.tsv",
        "ERT000037-intron.tsv",
        "ERT000038-hyloMarker.tsv",
        "ERT000039-Sat.tsv",
        "ERT000042-ncRNA.tsv",
        "ERT000047-betasat.tsv",
        "ERT000050-ISR.tsv",
        "ERT000051-poly.tsv",
        "ERT000052-ssRNA.tsv",
        "ERT000053-ETS.tsv",
        "ERT000055-STS.tsv",
        "ERT000056-mobele.tsv",
        "ERT000057-alphasat.tsv",
        "ERT000058-MLmarker.tsv",
        "ERT000060-vUTR.tsv",
        // Test validation with entry number column
        "with-entrynumber.tsv",
        // Test validation without entry number column
        "without-entrynumber.tsv",
        // Test with Checklist template id
        "ERT000002-rRNA-with-checklist-line.tsv"
    };

    @Test
    void testTsvToGff3AndDefaultHeaderFasta_forAllTemplates_conversionSuccessful()
            throws IOException, ReadException, WriteException, ValidationException {

        for (String file : allTemplates) {
            Path inputFile = inputDir.resolve(file);
            Path outputGff3 = Files.createTempFile("output", ".gff3");
            Path outputFasta = Files.createTempFile("output", ".fasta");

            try {
                convertAndValidateTsv(
                        inputFile, outputGff3, outputFasta, TSVToGFF3Converter.FastaHeaderType.DEFAULT, null);

                assertAll(
                        "output files for " + file,
                        () -> assertTrue(Files.exists(outputGff3), "GFF3 output file should exist"),
                        () -> assertTrue(Files.size(outputGff3) > 0, "GFF3 output file should not be empty"),
                        () -> assertTrue(Files.exists(outputFasta), "FASTA output file should exist"),
                        () -> assertTrue(Files.size(outputFasta) > 0, "FASTA output file should not be empty"));

                String gff3Content = Files.readString(outputGff3);
                assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");

            } finally {
                Files.deleteIfExists(outputGff3);
                Files.deleteIfExists(outputFasta);
            }
        }
    }

    @Test
    void testTsvToGff3AndJsonHeaderFasta_forAllTemplates_createsReadableFastas() throws Exception {
        JsonHeaderParser headerParser = new JsonHeaderParser();

        for (String file : allTemplates) {
            Path inputFile = inputDir.resolve(file);
            Path outputGff3 = Files.createTempFile("output", ".gff3");
            Path outputFasta = Files.createTempFile("output", ".fasta");

            try {
                convertAndValidateTsv(
                        inputFile, outputGff3, outputFasta, TSVToGFF3Converter.FastaHeaderType.JSON_HEADER, null);

                // assert nontrivial output files exist
                assertAll(
                        "output files for " + file,
                        () -> assertTrue(Files.exists(outputGff3), "GFF3 output file should exist"),
                        () -> assertTrue(Files.size(outputGff3) > 0, "GFF3 output file should not be empty"),
                        () -> assertTrue(Files.exists(outputFasta), "FASTA output file should exist"),
                        () -> assertTrue(Files.size(outputFasta) > 0, "FASTA output file should not be empty"));

                // assert gff3 is formatted ok
                String gff3Content = Files.readString(outputGff3);
                assertAll(
                        "GFF3 content for " + file,
                        () -> assertTrue(
                                gff3Content.startsWith("##gff-version"), "GFF3 should start with version header"),
                        () -> assertTrue(gff3Content.contains("\n"), "GFF3 should contain at least one newline"));

                // assertFasta created with Json headers is readable and the headers are parseable
                try (SequenceFormatReader reader = SequenceFormatReaderFactory.readFasta(outputFasta.toFile())) {
                    var ids = reader.getOrderedIds();

                    assertFalse(ids.isEmpty(), "FASTA should contain at least one sequence");

                    for (var id : ids) {
                        Optional<String> header = reader.getHeaderline(id);

                        assertAll(
                                "FASTA header for id " + id,
                                () -> assertTrue(header.isPresent(), "Header should be present"),
                                () -> assertFalse(header.get().isBlank(), "Header should not be blank"));

                        assertDoesNotThrow(
                                () -> headerParser.parse(header.get()),
                                "Header should be parseable as JSON for id " + id);
                    }
                }
            } finally {
                Files.deleteIfExists(outputGff3);
                Files.deleteIfExists(outputFasta);
            }
        }
    }

    @Test
    void testTsvWithSourceFeature_forAllTemplates_GeneratesReadableSourceFeatureFiles()
            throws IOException, ReadException, WriteException, ValidationException {

        for (String file : allTemplates) {
            Path inputFile = inputDir.resolve(file);
            Path outputGff3 = Files.createTempFile("output", ".gff3");
            Path outputFasta = Files.createTempFile("output", ".fasta");
            Path outputSource = Files.createTempFile("source", ".txt");

            try {
                convertAndValidateTsv(
                        inputFile, outputGff3, outputFasta, TSVToGFF3Converter.FastaHeaderType.DEFAULT, outputSource);

                // assert files exist
                assertAll(
                        "source feature outputs for " + file,
                        () -> assertTrue(Files.exists(outputGff3), "GFF3 output file should exist"),
                        () -> assertTrue(Files.size(outputGff3) > 0, "GFF3 output file should not be empty"),
                        () -> assertTrue(Files.exists(outputFasta), "FASTA output file should exist"),
                        () -> assertTrue(Files.size(outputFasta) > 0, "FASTA output file should not be empty"),
                        () -> assertTrue(Files.exists(outputSource), "Source feature file should exist"),
                        () -> assertTrue(Files.size(outputSource) > 0, "Source feature file should not be empty"));

                List<SourceFeatureDTO> sourceFeatureDTOs = SourceFeatureUtils.loadSourceFeatureDto(outputSource);
                List<SourceFeature> sourceFeatures = sourceFeatureDTOs.stream()
                        .map(SourceFeatureDTO::toSourceFeature)
                        .collect(Collectors.toList());
                assertFalse(sourceFeatures.isEmpty(), "Source feature file should contain at least one entry");

            } finally {
                Files.deleteIfExists(outputGff3);
                Files.deleteIfExists(outputFasta);
                Files.deleteIfExists(outputSource);
            }
        }
    }

    protected void convertAndValidateTsv(
            Path inputTsv,
            Path outputGff3,
            Path fastaOutputPath,
            TSVToGFF3Converter.FastaHeaderType fastaHeaderType,
            Path sourceOutputPath)
            throws IOException, ReadException, WriteException, ValidationException {
        try (ValidationEngine engine = createValidationEngine();
                BufferedReader reader = Files.newBufferedReader(inputTsv);
                BufferedWriter writer = Files.newBufferedWriter(outputGff3)) {

            TSVToGFF3Converter converter =
                    new TSVToGFF3Converter(engine, fastaOutputPath, fastaHeaderType, sourceOutputPath);
            converter.convert(reader, writer);
        }
    }

    protected ValidationEngine createValidationEngine() {
        Map<String, RuleSeverity> overriddenRules = Map.of(
                "REQUIRED_ATTRIBUTES",
                RuleSeverity.OFF // remove once this validation is fixed/removed, template 39 has trouble here
                );
        return new ValidationEngineBuilder()
                .overrideMethodRules(overriddenRules)
                .failFast(true)
                .build();
    }
}
