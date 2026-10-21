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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderProvider;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;

/**
 * Runs GFF3 through the full validation engine with a header provider registered that has no
 * header for some accessions. Every sequence is available and clean, so the only thing wrong with
 * the input is the missing headers: with FASTA_HEADER_MAPPING on that must be reported, and with it
 * off nothing else in the engine may trip over the missing headers.
 */
class FastaHeaderMappingOffTest {

    private static final String RULE = "FASTA_HEADER_MAPPING";
    private static final Map<String, RuleSeverity> RULE_OFF = Map.of(RULE, RuleSeverity.OFF);
    private static final int SEQUENCE_LENGTH = 1000;

    @TempDir
    Path tempDir;

    @Test
    void reportsEveryAnnotationWithoutHeaderWhenRuleIsOn() throws Exception {
        String gff3 = gff3(gene("SEQ1"), gene("SEQ2"), gene("SEQ3"));

        ValidationEngine engine = run(gff3, Map.of(), false, "SEQ1");

        List<ValidationException> errors = engine.getCollectedErrors();
        assertEquals(2, errors.size(), "Errors: " + errors);
        assertTrue(errors.stream().allMatch(e -> RULE.equals(e.getValidationRule())), "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().contains("SEQ2"));
        assertTrue(errors.get(1).getMessage().contains("SEQ3"));
    }

    @Test
    void singleAnnotationWithoutHeaderPasses() throws Exception {
        assertPassesWithRuleOff(gff3(gene("SEQ2")), "SEQ1");
    }

    @Test
    void providerWithNoHeadersAtAllPasses() throws Exception {
        assertPassesWithRuleOff(gff3(gene("SEQ1"), gene("SEQ2"), gene("SEQ3")));
    }

    @Test
    void firstAnnotationWithoutHeaderPasses() throws Exception {
        assertPassesWithRuleOff(gff3(gene("SEQ2"), gene("SEQ1")), "SEQ1");
    }

    @Test
    void lastAnnotationWithoutHeaderPasses() throws Exception {
        // The last annotation is only flushed at EOF, through a different branch of the reader.
        assertPassesWithRuleOff(gff3(gene("SEQ1"), gene("SEQ2")), "SEQ1");
    }

    @Test
    void annotationWithoutHeaderBetweenAnnotationsWithHeadersPasses() throws Exception {
        assertPassesWithRuleOff(gff3(gene("SEQ1"), gene("SEQ2"), gene("SEQ3")), "SEQ1", "SEQ3");
    }

    @Test
    void geneModelWithoutHeaderPasses() throws Exception {
        assertPassesWithRuleOff(gff3(geneModel("SEQ2")), "SEQ1");
    }

    @Test
    void severalFeaturesOnSequenceWithoutHeaderPasses() throws Exception {
        String features = String.join(
                "",
                feature("SEQ2", "gene", 10, 200, "ID=gene1;gene=geneA;"),
                feature("SEQ2", "gene", 300, 500, "ID=gene2;gene=geneB;"),
                feature("SEQ2", "gene", 600, 900, "ID=gene3;gene=geneC;"));
        assertPassesWithRuleOff(gff3(features), "SEQ1");
    }

    @Test
    void featureSpanningWholeSequenceWithoutHeaderPasses() throws Exception {
        assertPassesWithRuleOff(gff3(feature("SEQ2", "gene", 1, SEQUENCE_LENGTH, "ID=gene1;gene=geneA;")), "SEQ1");
    }

    @Test
    void annotationsSplitByDirectiveWithoutHeaderPass() throws Exception {
        // "###" closes an annotation early; the reader merges same-accession annotations back.
        String features = gene("SEQ2") + "###\n" + feature("SEQ2", "gene", 600, 900, "ID=gene2;gene=geneB;");
        assertPassesWithRuleOff(gff3(features), "SEQ1");
    }

    @Test
    void failFastEngineDoesNotThrowWithoutHeaders() throws Exception {
        String gff3 = gff3(gene("SEQ1"), gene("SEQ2"), gene("SEQ3"));

        ValidationEngine engine = assertDoesNotThrow(() -> run(gff3, RULE_OFF, true, "SEQ1"));

        assertTrue(engine.getCollectedErrors().isEmpty(), "Errors: " + engine.getCollectedErrors());
    }

    private void assertPassesWithRuleOff(String gff3, String... accessionsWithHeader) throws Exception {
        ValidationEngine engine = run(gff3, RULE_OFF, false, accessionsWithHeader);

        assertTrue(engine.getCollectedErrors().isEmpty(), "Errors: " + engine.getCollectedErrors());
        assertTrue(engine.getParsingWarnings().isEmpty(), "Warnings: " + engine.getParsingWarnings());
        assertDoesNotThrow(engine::throwIfErrorsCollected);
    }

    /**
     * Reads {@code gff3} through a full (classpath-scanned) engine. Every sequence-region accession
     * gets a clean sequence; only {@code accessionsWithHeader} get a FASTA header.
     */
    private ValidationEngine run(
            String gff3, Map<String, RuleSeverity> rules, boolean failFast, String... accessionsWithHeader)
            throws Exception {
        Map<String, String> sequences = new LinkedHashMap<>();
        for (String accession : List.of("SEQ1", "SEQ2", "SEQ3")) {
            sequences.put(accession, "ACGT".repeat(SEQUENCE_LENGTH / 4));
        }
        Map<String, FastaHeader> headers = new HashMap<>();
        for (String accession : accessionsWithHeader) {
            headers.put(accession, validHeader());
        }

        Path file = tempDir.resolve("input.gff3");
        Files.writeString(file, gff3);

        ValidationEngine engine = new ValidationEngineBuilder()
                .withProvider(new StubSequenceProvider(sequences))
                .withProvider(new StubFastaHeaderProvider(headers))
                .overrideMethodRules(rules)
                // Mirrors GFF3 conversion: gap generation only belongs to FASTA -> GFF3.
                .overrideMethodFixs(Map.of("GAP_GENERATION", false))
                .failFast(failFast)
                .build();
        try (GFF3FileReader reader = new GFF3FileReader(engine, file)) {
            reader.readHeader();
            reader.read(annotation -> {});
        }
        return engine;
    }

    private static String gff3(String... features) {
        StringBuilder sb = new StringBuilder("##gff-version 3.1.26\n");
        for (String accession : List.of("SEQ1", "SEQ2", "SEQ3")) {
            sb.append("##sequence-region ")
                    .append(accession)
                    .append(" 1 ")
                    .append(SEQUENCE_LENGTH)
                    .append('\n');
        }
        for (String feature : features) {
            sb.append(feature);
        }
        return sb.toString();
    }

    private static String gene(String accession) {
        return feature(accession, "gene", 100, 500, "ID=gene_" + accession + ";gene=gene" + accession + ";");
    }

    private static String geneModel(String accession) {
        return feature(accession, "gene", 100, 500, "ID=gene1;gene=geneA;")
                + feature(accession, "mRNA", 100, 500, "ID=mrna1;Parent=gene1;gene=geneA;")
                + feature(accession, "exon", 100, 200, "ID=exon1;Parent=mrna1;gene=geneA;")
                + feature(accession, "exon", 300, 500, "ID=exon2;Parent=mrna1;gene=geneA;");
    }

    private static String feature(String accession, String type, int start, int end, String attributes) {
        return String.join("\t", accession, ".", type, String.valueOf(start), String.valueOf(end), ".", "+", ".") + "\t"
                + attributes + "\n";
    }

    private static FastaHeader validHeader() {
        FastaHeader header = new FastaHeader();
        header.setDescription("Test sequence");
        header.setMoleculeType("genomic DNA");
        header.setTopology("linear");
        return header;
    }

    /** Header provider that is always registered but only knows the headers it was given. */
    private static class StubFastaHeaderProvider extends FastaHeaderProvider {
        private final Map<String, FastaHeader> headers;

        StubFastaHeaderProvider(Map<String, FastaHeader> headers) {
            this.headers = headers;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Optional<FastaHeader> getHeader(String seqId) {
            return Optional.ofNullable(headers.get(seqId));
        }
    }

    /** Sequence provider backed by in-memory, N-free sequences. */
    private static class StubSequenceProvider implements ContextProvider<SequenceLookup>, SequenceLookup {
        private final Map<String, String> sequences;

        StubSequenceProvider(Map<String, String> sequences) {
            this.sequences = sequences;
        }

        @Override
        public SequenceLookup get(ValidationContext context) {
            return this;
        }

        @Override
        public Class<SequenceLookup> type() {
            return SequenceLookup.class;
        }

        @Override
        public String getSequenceSlice(String seqId, long fromBase, long toBase) {
            return sequences.get(seqId).substring((int) fromBase - 1, (int) toBase);
        }

        @Override
        public long getSequenceLength(String seqId) {
            return sequences.get(seqId).length();
        }

        @Override
        public SequenceStats getSequenceStats(String seqId) {
            String sequence = sequences.get(seqId);
            Map<Character, Long> baseCount = new HashMap<>();
            for (char base : sequence.toCharArray()) {
                baseCount.merge(base, 1L, Long::sum);
            }
            return new SequenceStats(sequence.length(), sequence.length(), 0, 0, baseCount);
        }

        @Override
        public List<GapRegion> getGapRegions(String seqId) {
            return new ArrayList<>();
        }

        @Override
        public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase) {
            return new ArrayList<>();
        }

        @Override
        public boolean hasSequence(String seqId) {
            return sequences.containsKey(seqId);
        }

        @Override
        public Set<String> knownSeqIds() {
            return sequences.keySet();
        }

        @Override
        public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase) {
            return new StringReader(getSequenceSlice(seqId, fromBase, toBase));
        }
    }
}
