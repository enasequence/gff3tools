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
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FileFastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceSource;

class FastaToGff3ConverterTest {

    private static final Path SINGLE_SEQUENCE = Path.of("src/test/resources/fasta_to_gff3/single_sequence.fasta");

    /**
     * Builds an engine wired with the input FASTA's headers, mirroring the conversion command
     * (and {@link #feedsSequenceLookupAndFastaHeaderContextFromInputFasta()}). Without a populated
     * {@link FastaHeaderProvider}, the header-aware fixes have no header to resolve for the
     * annotation's accession.
     */
    private static ValidationEngine engineWithHeaders(FileSequenceSource source) {
        FastaHeaderProvider headerProvider = new FastaHeaderProvider();
        headerProvider.addSource(new FileFastaHeaderSource(source.getSeqIdToHeader()));
        return new ValidationEngineBuilder().withProvider(headerProvider).build();
    }

    /** Runs the converter and returns the GFF3 output as a string. */
    private static String runConversion(FastaToGff3Converter converter) throws Exception {
        StringWriter output = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new StringReader(""));
                BufferedWriter writer = new BufferedWriter(output)) {
            converter.convert(reader, writer);
        }
        return output.toString();
    }

    @Test
    void convertsFastaToGff3WithGapFeatures() throws Exception {
        Path expected = Path.of("src/test/resources/fasta_to_gff3/single_sequence_expected.gff3");

        FileSequenceSource source = new FileSequenceSource(SINGLE_SEQUENCE, SequenceFormat.fasta, null);
        ValidationEngine engine = engineWithHeaders(source);
        // minGapLength=1 reports every run of N, so both gaps in the fixture are emitted.
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, source, 1);

        String actual = runConversion(converter);
        source.close();

        String expectedContent = Files.readString(expected, StandardCharsets.UTF_8);
        // Trim trailing whitespace, matching the FF->GFF3 converter test convention.
        assertEquals(expectedContent.trim(), actual.trim());
        // By default a plain gap is emitted: gap_type cannot be inferred from a run of Ns.
        assertFalse(actual.contains("gap_type"));
        assertFalse(actual.contains("linkage_evidence"));
    }

    @Test
    void emitsGapTypeAndLinkageEvidenceWhenSupplied() throws Exception {
        FileSequenceSource source = new FileSequenceSource(SINGLE_SEQUENCE, SequenceFormat.fasta, null);
        ValidationEngine engine = engineWithHeaders(source);
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, source, 1, "within scaffold", "unspecified");

        String actual = runConversion(converter);
        source.close();

        assertTrue(actual.contains("gap_type=within scaffold"));
        assertTrue(actual.contains("linkage_evidence=unspecified"));
    }

    @Test
    void minGapLengthFiltersShortGaps() throws Exception {
        // Fixture has a 10bp gap (45..54) and a 4bp gap (99..102).
        FileSequenceSource source = new FileSequenceSource(SINGLE_SEQUENCE, SequenceFormat.fasta, null);
        ValidationEngine engine = engineWithHeaders(source);
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, source, 10);

        String actual = runConversion(converter);
        source.close();

        // 10bp gap is kept (length >= minGapLength); 4bp gap is dropped.
        assertTrue(actual.contains("\tgap\t45\t54\t"));
        assertFalse(actual.contains("\tgap\t99\t102\t"));
        assertTrue(actual.contains("estimated_length=10"));
        assertFalse(actual.contains("estimated_length=4"));
    }

    @Test
    void convertsEmptySequenceWithNoGaps() throws Exception {
        Path fasta = Files.createTempFile("empty", ".fasta");
        Files.writeString(
                fasta,
                ">EMPTY | {\"description\":\"No gaps\", \"molecule_type\":\"GENOMIC DNA\", \"topology\":\"linear\"}\nATGCATGCATGC\n");

        FileSequenceSource source = new FileSequenceSource(fasta, SequenceFormat.fasta, null);
        ValidationEngine engine = engineWithHeaders(source);
        FastaToGff3Converter converter =
                new FastaToGff3Converter(engine, source, FastaToGff3Converter.DEFAULT_MIN_GAP_LENGTH);

        String actual = runConversion(converter);
        source.close();

        assertTrue(actual.contains("##gff-version 3.1.26"));
        assertTrue(actual.contains("##sequence-region EMPTY 1 12"));
        assertFalse(actual.contains("\tgap\t"));

        Files.deleteIfExists(fasta);
    }

    @Test
    void convertsFastaWithSubmissionIdWithoutDot() throws Exception {
        // seqId without a "." should round-trip through the header parser and into the GFF3 seqId.
        Path fasta = Files.createTempFile("no_dot", ".fasta");
        Files.writeString(
                fasta,
                ">TEST01 | {\"description\":\"No dot in id\", \"molecule_type\":\"GENOMIC DNA\", \"topology\":\"linear\"}\nATGCATGCNNNNNNNNNNATGCATGC\n");

        FileSequenceSource source = new FileSequenceSource(fasta, SequenceFormat.fasta, null);
        ValidationEngine engine = engineWithHeaders(source);
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, source, 1);

        String actual = runConversion(converter);
        // Submission ID is extracted with no "." present.
        assertTrue(source.getSeqIdToOrdinal().containsKey("TEST01"));
        assertTrue(actual.contains("##sequence-region TEST01 1 26"));
        assertTrue(actual.contains("TEST01\t.\tgap\t9\t18\t"));
        source.close();

        Files.deleteIfExists(fasta);
    }

    @Test
    void rejectsLinkageEvidenceWithoutGapType() {
        // linkage_evidence is meaningless without a gap_type; reject the inconsistent combination.
        ValidationEngine engine = new ValidationEngineBuilder().build();
        FileSequenceSource source = new FileSequenceSource(SINGLE_SEQUENCE, SequenceFormat.fasta, null);
        assertThrows(
                IllegalArgumentException.class, () -> new FastaToGff3Converter(engine, source, 1, null, "unspecified"));
    }

    @Test
    void assignsUniqueGapIdsAcrossMultipleSequences() throws Exception {
        // Two sequences, each with a single 10bp gap at the same coordinates. The gap IDs must be
        // unique across the whole document, not reset per sequence.
        Path fasta = Files.createTempFile("multi", ".fasta");
        Files.writeString(
                fasta,
                ">SEQ1.1 | {\"description\":\"first\", \"molecule_type\":\"GENOMIC DNA\", \"topology\":\"linear\"}\nATGCNNNNNNNNNNATGC\n"
                        + ">SEQ2.1 | {\"description\":\"second\", \"molecule_type\":\"GENOMIC DNA\", \"topology\":\"linear\"}\nGGGGNNNNNNNNNNGGGG\n");

        FileSequenceSource source = new FileSequenceSource(fasta, SequenceFormat.fasta, null);
        ValidationEngine engine = engineWithHeaders(source);
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, source, 1);

        String actual = runConversion(converter);
        source.close();

        // Both gaps span bases 5..14 within their own sequence.
        assertTrue(actual.contains("SEQ1.1\t.\tgap\t5\t14\t"));
        assertTrue(actual.contains("SEQ2.1\t.\tgap\t5\t14\t"));
        // IDs are unique across the document: "gap" and "gap_1".
        assertTrue(actual.contains("ID=gap;"));
        assertTrue(actual.contains("ID=gap_1;"));

        Files.deleteIfExists(fasta);
    }

    @Test
    void feedsSequenceLookupAndFastaHeaderContextFromInputFasta() throws Exception {
        // Wire the input FASTA into the engine providers exactly as the conversion command does,
        // then verify the engine context exposes the submission IDs, sequence and FASTA header.
        FileSequenceSource source = new FileSequenceSource(SINGLE_SEQUENCE, SequenceFormat.fasta, null);

        CompositeSequenceProvider sequenceProvider = new CompositeSequenceProvider();
        sequenceProvider.addSource(source);
        FastaHeaderProvider headerProvider = new FastaHeaderProvider();
        headerProvider.addSource(new FileFastaHeaderSource(source.getSeqIdToHeader()));

        ValidationEngine engine = new ValidationEngineBuilder()
                .withProvider(sequenceProvider)
                .withProvider(headerProvider)
                .build();
        FastaToGff3Converter converter = new FastaToGff3Converter(engine, source, 1);

        runConversion(converter);

        // Submission ID extracted from the FASTA header (seqIdToOrdinal map).
        assertTrue(source.getSeqIdToOrdinal().containsKey("TEST01.1"));

        ValidationContext context = engine.getContext();

        // SequenceLookup resolves a slice for the submission ID (sequence info reachable).
        SequenceLookup lookup = context.get(SequenceLookup.class);
        assertNotNull(lookup);
        assertEquals("ATGC", lookup.getSequenceSlice("TEST01.1", 1, 4, SequenceRangeOption.WHOLE_SEQUENCE));

        // FASTA header reachable from the context for the same submission ID.
        FastaHeaderProvider providerFromContext = context.get(FastaHeaderProvider.class);
        FastaHeader header = providerFromContext.getHeader("TEST01.1").orElse(null);
        assertNotNull(header);

        // Closing the engine closes the registered source.
        engine.close();
    }
}
