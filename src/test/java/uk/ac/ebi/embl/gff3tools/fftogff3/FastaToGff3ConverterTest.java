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
package uk.ac.ebi.embl.gff3tools.fftogff3;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

class FastaToGff3ConverterTest {

    @Test
    void convertsFastaToGff3WithGapFeatures() throws Exception {
        Path fasta = Path.of("src/test/resources/fasta_to_gff3/single_sequence.fasta");
        Path expected = Path.of("src/test/resources/fasta_to_gff3/single_sequence_expected.gff3");

        ValidationEngine engine = new ValidationEngineBuilder().build();
        // minGapLength=1 reports every run of N, so both gaps in the fixture are emitted.
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, fasta, SequenceFormat.fasta, 1);

        StringWriter output = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new StringReader(""));
                BufferedWriter writer = new BufferedWriter(output)) {
            converter.convert(reader, writer);
        }

        String actual = output.toString();
        String expectedContent = Files.readString(expected, StandardCharsets.UTF_8);
        assertEquals(expectedContent, actual);
    }

    @Test
    void minGapLengthFiltersShortGaps() throws Exception {
        // Fixture has a 10bp gap (45..54) and a 4bp gap (99..102).
        Path fasta = Path.of("src/test/resources/fasta_to_gff3/single_sequence.fasta");

        ValidationEngine engine = new ValidationEngineBuilder().build();
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, fasta, SequenceFormat.fasta, 10);

        StringWriter output = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new StringReader(""));
                BufferedWriter writer = new BufferedWriter(output)) {
            converter.convert(reader, writer);
        }

        String actual = output.toString();
        // 10bp gap is kept (length >= minGapLength); 4bp gap is dropped.
        assertTrue(actual.contains("\tgap\t45\t54\t"));
        assertFalse(actual.contains("\tgap\t99\t102\t"));
        assertTrue(actual.contains("estimated_length=10"));
        assertFalse(actual.contains("estimated_length=4"));
    }

    @Test
    void convertsEmptySequenceWithNoGaps() throws Exception {
        Path fasta = Files.createTempFile("empty", ".fasta");
        Files.writeString(fasta, ">EMPTY | {\"description\":\"No gaps\"}\nATGCATGCATGC\n");

        ValidationEngine engine = new ValidationEngineBuilder().build();
        FastaToGff3Converter converter = new FastaToGff3Converter(
                engine, fasta, SequenceFormat.fasta, FastaToGff3Converter.DEFAULT_MIN_GAP_LENGTH);

        StringWriter output = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new StringReader(""));
                BufferedWriter writer = new BufferedWriter(output)) {
            converter.convert(reader, writer);
        }

        String actual = output.toString();
        assertTrue(actual.contains("##gff-version 3.1.26"));
        assertTrue(actual.contains("##sequence-region EMPTY 1 12"));
        assertFalse(actual.contains("\tgap\t"));

        Files.deleteIfExists(fasta);
    }
}
