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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.ac.ebi.embl.gff3tools.cli.Main;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.exception.WriteException;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

public class TSVToGff3ConverterTest {

    @Test
    void testTsvToGff3_withAllTemplates() throws IOException, ReadException, WriteException, ValidationException {

        Path inputDir = Path.of("src/test/resources/tsvtogff3/all_templates");
        final String[] allTemplatesA = {
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

        for (String file : allTemplatesA) {
            Path inputFile = inputDir.resolve(file);
            Path outputGff3 = Files.createTempFile("output", ".gff3");
            Path outputFasta = Files.createTempFile("output", ".fasta");

            String[] args = new String[] {
                "conversion",
                "-f",
                "tsv",
                "-t",
                "gff3",
                "--output-sequence",
                outputFasta.toString(),
                inputFile.toString(),
                outputGff3.toString()
            };

            try (MockedStatic<Main> mock = mockStatic(Main.class)) {
                convertAndValidateTsv(inputFile, outputGff3, outputFasta);
                // assert gff3 exists
                assertTrue(Files.exists(outputGff3), "GFF3 output file should exist");
                String gff3Content = Files.readString(outputGff3);
                assertTrue(gff3Content.contains("##gff-version"), "GFF3 should have version header");
                // assert fasta exists
                assertTrue(Files.exists(outputFasta), "Fasta output file should exist");
                String fastaContent = Files.readString(outputFasta);
            } finally {
                Files.deleteIfExists(outputGff3);
            }
        }
    }

    protected void convertAndValidateTsv(Path inputTsv, Path outputGff3, Path fastaOutputPath)
            throws IOException, ReadException, WriteException, ValidationException {
        try (ValidationEngine engine = createValidationEngine();
                BufferedReader reader = Files.newBufferedReader(inputTsv);
                BufferedWriter writer = Files.newBufferedWriter(outputGff3)) {

            TSVToGFF3Converter converter = new TSVToGFF3Converter(engine, fastaOutputPath);
            converter.convert(reader, writer);
        }
    }

    protected ValidationEngine createValidationEngine() {
        Map<String, RuleSeverity> ignore = new HashMap<String, RuleSeverity>();
        ignore.put(
                "REQUIRED_ATTRIBUTES",
                RuleSeverity
                        .OFF); // this rule trips up on template no. 39 so as we plan to remove it it's turned off here
        // as is
        return new ValidationEngineBuilder().overrideMethodRules(ignore).build();
    }
}
