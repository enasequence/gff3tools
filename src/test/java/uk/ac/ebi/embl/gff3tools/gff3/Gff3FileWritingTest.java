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
package uk.ac.ebi.embl.gff3tools.gff3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3FileFactory;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3Header;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

/**
 * Contract tests for the documents {@link GFF3File} writes, built either from parsed annotations
 * plus a reader ({@link GFF3FileFactory#fromAnnotationAndReader}) or from an EMBL flat file
 * ({@link GFF3FileFactory#from}).
 *
 * <p>Every document, whichever route built it, must satisfy two rules: at most one
 * {@code ##FASTA} section, after the last feature line with only sequence data below it; and
 * reading the document back recovers the annotations that were written.
 *
 * <p>The rest is about translations: {@code appendTranslationFasta} decides whether they are
 * written at all, {@code existingTranslationFilePathFallback} supplies them when the
 * {@link uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState} has none, and a document
 * carries only the translations of the annotations it holds.
 */
public class Gff3FileWritingTest {

    /** Two annotations, one CDS each, translations in a single trailing FASTA section. */
    private static final String TWO_ANNOTATIONS = "##gff-version 3\n"
            + "##species http://example.org?name=Homo sapiens\n"
            + "##sequence-region BN000065.1 1 315242\n"
            + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_A;gene=RHD;\n\n"
            + "##sequence-region BN000066.1 1 315242\n"
            + "BN000066.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_B;gene=RHD;\n\n"
            + "##FASTA\n"
            + ">BN000065.1|CDS_A\n"
            + "MSSKYPRSVRRCLPLWALTLE\n\n"
            + ">BN000066.1|CDS_B\n"
            + "AALILLFYFFTHYDASLE\n\n";

    /** Two accessions where one is a string prefix of the other. */
    private static final String PREFIX_COLLIDING_ACCESSIONS = "##gff-version 3\n"
            + "##species http://example.org?name=Homo sapiens\n"
            + "##sequence-region AB123.1 1 315242\n"
            + "AB123.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_SHORT;gene=RHD;\n\n"
            + "##sequence-region AB123.10 1 315242\n"
            + "AB123.10\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_LONG;gene=RHD;\n\n"
            + "##FASTA\n"
            + ">AB123.1|CDS_SHORT\n"
            + "MSSKYPRSVRRCLPLWALTLE\n\n"
            + ">AB123.10|CDS_LONG\n"
            + "AALILLFYFFTHYDASLE\n\n";

    /** A fallback file holding one record for BN000065.1 and one for an accession nobody here has. */
    private static final String FALLBACK_FASTA =
            ">BN000065.1|CDS_FALLBACK\nMMMMMMMM\n>ZZ999.9|CDS_STRANGER\nWWWWWWWW\n";

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("appendTranslationFasta = false — appends no FASTA output")
    class WhenNotAppendingTranslationFasta {

        @Test
        @DisplayName("writes no ##FASTA directive")
        void writesNoFastaDirective() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), false, Optional.empty());

            assertEquals(0, countOf(output, "##FASTA"), "no FASTA output was requested");
        }

        @Test
        @DisplayName("writes no translation records")
        void writesNoTranslationRecords() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), false, Optional.empty());

            assertFalse(output.contains(">BN000065.1|CDS_A"), "translation written despite append=false");
            assertFalse(output.contains(">BN000066.1|CDS_B"), "translation written despite append=false");
        }

        @Test
        @DisplayName("still writes every annotation's features")
        void stillWritesEveryAnnotation() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), false, Optional.empty());

            assertTrue(output.contains("BN000065.1\t.\tCDS"), "missing features of the first annotation");
            assertTrue(output.contains("BN000066.1\t.\tCDS"), "missing features of the second annotation");
        }

        @Test
        @DisplayName("round-trips every annotation it wrote")
        void roundTripsEveryAnnotation() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), false, Optional.empty());

            assertEquals(2, readAnnotations(output).size(), "every annotation written must be readable back");
        }

        /**
         * A caller can ask for no FASTA and still pass a fallback file. The flag wins: nothing is
         * appended, and the fallback file is never read.
         */
        @Test
        @DisplayName("ignores the fallback FASTA file")
        void ignoresTheFallbackFile() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), false, Optional.of(fallbackFile()));

            assertEquals(0, countOf(output, "##FASTA"), "no FASTA output was requested");
            assertFalse(output.contains("MMMMMMMM"), "fallback file used despite append=false");
        }
    }

    @Nested
    @DisplayName("appendTranslationFasta = true — appends the annotations' FASTA output")
    class WhenAppendingTranslationFasta {

        @Test
        @DisplayName("appends a translation for every annotation, in the single trailing section")
        void appendsATranslationForEveryAnnotation() throws Exception {
            String fastaSection = trailingFastaSectionOf(write(TWO_ANNOTATIONS, all(), true, Optional.empty()));

            assertTrue(fastaSection.contains(">BN000065.1|CDS_A"), "missing translation for BN000065.1");
            assertTrue(fastaSection.contains(">BN000066.1|CDS_B"), "missing translation for BN000066.1");
        }

        @Test
        @DisplayName("appends only the translations of the annotations in the file")
        void appendsOnlyThisFilesTranslations() throws Exception {
            String fastaSection =
                    trailingFastaSectionOf(write(TWO_ANNOTATIONS, accession("BN000066.1"), true, Optional.empty()));

            assertTrue(fastaSection.contains(">BN000066.1|CDS_B"), "expected this file's own translation");
            assertFalse(
                    fastaSection.contains(">BN000065.1|CDS_A"),
                    "translation of an annotation not in this file leaked in");
        }

        @Test
        @DisplayName("appends at most one ##FASTA section")
        void appendsAtMostOneFastaSection() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), true, Optional.empty());

            assertEquals(1, countOf(output, "##FASTA"), "a GFF3 document must not contain two FASTA sections");
        }

        @Test
        @DisplayName("writes no feature lines after the ##FASTA section")
        void writesNoFeatureLinesAfterTheFastaSection() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), true, Optional.empty());

            int fastaStart = output.indexOf("##FASTA");
            assertTrue(fastaStart >= 0, "expected a FASTA section");
            String afterFasta = output.substring(fastaStart + "##FASTA".length());
            assertFalse(
                    afterFasta.contains("\t"),
                    "no tab-separated feature lines may appear after ##FASTA, but found:\n" + afterFasta);
        }

        @Test
        @DisplayName("round-trips every annotation it wrote")
        void roundTripsEveryAnnotation() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), true, Optional.empty());

            assertEquals(2, readAnnotations(output).size(), "every annotation written must be readable back");
        }

        @Test
        @DisplayName("writes no ##FASTA directive when there is nothing to append")
        void writesNoFastaDirectiveWhenThereIsNothingToAppend() throws Exception {
            String withoutTranslations = "##gff-version 3\n"
                    + "##species http://example.org?name=Homo sapiens\n"
                    + "##sequence-region BN000065.1 1 315242\n"
                    + "BN000065.1\t.\tgene\t1\t315242\t.\t+\t.\tID=gene_A;gene=RHD;\n\n";

            String output = write(withoutTranslations, all(), true, Optional.empty());

            assertEquals(0, countOf(output, "##FASTA"), "an empty FASTA section should not be written");
        }

        /** The fallback fires when {@code TranslationState} yields nothing, not when it is absent. */
        @Test
        @DisplayName("falls back to the supplied FASTA file when TranslationState yields nothing")
        void fallsBackToTheSuppliedFastaFile() throws Exception {
            String withoutTranslations = "##gff-version 3\n"
                    + "##species http://example.org?name=Homo sapiens\n"
                    + "##sequence-region BN000065.1 1 315242\n"
                    + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_A;gene=RHD;\n\n";

            String output = write(withoutTranslations, all(), true, Optional.of(fallbackFile()));

            assertTrue(output.contains(">BN000065.1|CDS_FALLBACK"), "the fallback file was never read");
            assertTrue(
                    trailingFastaSectionOf(output).contains(">BN000065.1|CDS_FALLBACK"),
                    "the fallback translations must be appended in the trailing FASTA section");
        }

        /**
         * A path that does not exist is a source with nothing in it, not a failure — otherwise a
         * stale path would abort a write that has already emitted its feature lines.
         */
        @Test
        @DisplayName("falls through when the fallback file does not exist")
        void fallsThroughWhenTheFallbackFileDoesNotExist() throws Exception {
            String withoutTranslations = "##gff-version 3\n"
                    + "##species http://example.org?name=Homo sapiens\n"
                    + "##sequence-region BN000065.1 1 315242\n"
                    + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_A;gene=RHD;\n\n";

            String output = write(withoutTranslations, all(), true, Optional.of(tempDir.resolve("absent.fasta")));

            assertTrue(output.contains("BN000065.1\t.\tCDS"), "the document itself must still be written");
            assertEquals(0, countOf(output, "##FASTA"), "a missing fallback file supplies no translations");
        }

        @Test
        @DisplayName("keeps the document's own translations when the fallback file is missing")
        void keepsItsOwnTranslationsWhenTheFallbackFileIsMissing() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), true, Optional.of(tempDir.resolve("absent.fasta")));

            String fasta = trailingFastaSectionOf(output);
            assertTrue(fasta.contains(">BN000065.1|CDS_A"), "expected the document's own translations");
            assertTrue(fasta.contains(">BN000066.1|CDS_B"), "expected the document's own translations");
        }

        /**
         * The fallback file is filtered like every other source: a document takes the records whose
         * header names one of its own accessions and leaves the rest, so a file covering a whole
         * submission can be handed to a document holding part of it.
         */
        @Test
        @DisplayName("copies only the fallback records belonging to this document")
        void copiesOnlyTheFallbackRecordsBelongingToThisDocument() throws Exception {
            String withoutTranslations = "##gff-version 3\n"
                    + "##species http://example.org?name=Homo sapiens\n"
                    + "##sequence-region BN000065.1 1 315242\n"
                    + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_A;gene=RHD;\n\n";

            String fasta = trailingFastaSectionOf(write(withoutTranslations, all(), true, Optional.of(fallbackFile())));

            assertTrue(fasta.contains(">BN000065.1|CDS_FALLBACK"), "expected this document's own fallback record");
            assertTrue(fasta.contains("MMMMMMMM"), "expected this document's own fallback translation");
            assertFalse(fasta.contains("ZZ999.9"), "a record for another accession leaked out of the fallback file");
            assertFalse(fasta.contains("WWWWWWWW"), "a translation for another accession leaked out");
        }

        @Test
        @DisplayName("falls through when no fallback record belongs to this document")
        void fallsThroughWhenNoFallbackRecordBelongs() throws Exception {
            String withoutTranslations = "##gff-version 3\n"
                    + "##species http://example.org?name=Homo sapiens\n"
                    + "##sequence-region BN000065.1 1 315242\n"
                    + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_A;gene=RHD;\n\n";

            Path strangers = tempDir.resolve("strangers.fasta");
            Files.writeString(strangers, ">ZZ999.9|CDS_STRANGER\nWWWWWWWW\n", Charset.defaultCharset());

            String output = write(withoutTranslations, all(), true, Optional.of(strangers));

            assertEquals(0, countOf(output, "##FASTA"), "no fallback record belongs here, so nothing is appended");
        }
    }

    /** Two EMBL entries, each with one translated CDS, and differing organisms. */
    private static final String TWO_FLAT_FILE_ENTRIES = flatFileEntry("BN000065", "Homo sapiens", "RHD", "MSSKYPRSVRR")
            + flatFileEntry("BN000066", "Mus musculus", "matK", "AALILLFYFFT");

    /** One EMBL entry whose CDS carries no {@code /translation} qualifier. */
    private static final String ENTRY_WITHOUT_TRANSLATION = flatFileEntry("BN000065", "Homo sapiens", "RHD", null);

    private static String flatFileEntry(String accession, String organism, String gene, String translation) {
        StringBuilder entry = new StringBuilder()
                .append("ID   ")
                .append(accession)
                .append("; SV 1; linear; genomic DNA; STD; HUM; 315242 BP.\n")
                .append("XX\n")
                .append("AC   ")
                .append(accession)
                .append(";\n")
                .append("XX\n")
                .append("FH   Key             Location/Qualifiers\n")
                .append("FH\n")
                .append("FT   source          1..315242\n")
                .append("FT                   /mol_type=\"genomic DNA\"\n")
                .append("FT                   /organism=\"")
                .append(organism)
                .append("\"\n")
                .append("FT   gene            100..200\n")
                .append("FT                   /gene=\"")
                .append(gene)
                .append("\"\n")
                .append("FT   CDS             100..200\n")
                .append("FT                   /gene=\"")
                .append(gene)
                .append("\"\n")
                .append("FT                   /protein_id=\"CAD29848.1\"\n");
        if (translation != null) {
            entry.append("FT                   /translation=\"")
                    .append(translation)
                    .append("\"\n");
        }
        return entry.append("XX\n//\n").toString();
    }

    @Nested
    @DisplayName("converting an EMBL flat file")
    class WhenConvertingAFlatFile {

        @Test
        @DisplayName("writes every entry as an annotation")
        void writesEveryEntryAsAnAnnotation() throws Exception {
            String output = convert(TWO_FLAT_FILE_ENTRIES);

            assertTrue(output.contains("BN000065.1\t.\tCDS"), "missing features of the first entry");
            assertTrue(output.contains("BN000066.1\t.\tCDS"), "missing features of the second entry");
            assertEquals(2, readAnnotations(output).size(), "every entry written must be readable back");
        }

        @Test
        @DisplayName("writes one trailing FASTA section holding every entry's translations")
        void writesOneTrailingFastaSectionForAllEntries() throws Exception {
            String fastaSection = trailingFastaSectionOf(convert(TWO_FLAT_FILE_ENTRIES));

            assertTrue(fastaSection.contains("MSSKYPRSVRR"), "missing the first entry's translation");
            assertTrue(fastaSection.contains("AALILLFYFFT"), "missing the second entry's translation");
        }

        @Test
        @DisplayName("writes no FASTA section when no entry carries a translation")
        void writesNoFastaSectionWithoutTranslations() throws Exception {
            String output = convert(ENTRY_WITHOUT_TRANSLATION);

            assertEquals(0, countOf(output, "##FASTA"), "no translations exist, so no FASTA section should be written");
        }

        /**
         * Characterisation, not a requirement: the factory stamps its own {@code HEADER_VERSION}
         * rather than carrying anything over from the source. Undocumented today.
         */
        @Test
        @DisplayName("stamps the spec version in the header")
        void stampsTheSpecVersionHeader() throws Exception {
            String output = convert(TWO_FLAT_FILE_ENTRIES);

            assertTrue(output.startsWith("##gff-version 3.1.26"), "expected the spec version header, got:\n" + output);
        }

        /**
         * Characterisation, not a requirement: {@code ##species} is a file-level directive, so the
         * factory keeps the first entry's organism and silently discards the rest. Worth pinning
         * because a mixed-organism flat file loses information with no diagnostic.
         */
        @Test
        @DisplayName("takes the species directive from the first entry only")
        void takesSpeciesFromTheFirstEntryOnly() throws Exception {
            String output = convert(TWO_FLAT_FILE_ENTRIES);

            assertEquals(1, countOf(output, "##species"), "##species is a file-level directive");
            assertTrue(output.contains("name=Homo sapiens"), "expected the first entry's organism, got:\n" + output);
            assertFalse(output.contains("Mus musculus"), "the second entry's organism should have been discarded");
        }
    }

    @Nested
    @DisplayName("selecting an annotation's translations")
    class TranslationSelection {

        /**
         * Translation keys are {@code accession|featureId}, so picking an annotation's keys by
         * prefix would give {@code AB123.1} everything belonging to {@code AB123.10} as well. The
         * whole accession must match, so each one gets only its own.
         */
        @Test
        @DisplayName("matches accessions exactly, not by prefix")
        void matchesAccessionsExactlyNotByPrefix() throws Exception {
            Path source = tempDir.resolve("prefix.gff3");
            Files.writeString(source, PREFIX_COLLIDING_ACCESSIONS, Charset.defaultCharset());

            try (GFF3FileReader reader =
                    new GFF3FileReader(engine(), new StringReader(PREFIX_COLLIDING_ACCESSIONS), source)) {
                reader.readHeader();
                List<GFF3Annotation> annotations = new ArrayList<>();
                reader.read(annotations::add);

                GFF3Annotation shortAccession = annotations.stream()
                        .filter(a -> "AB123.1".equals(a.getAccession()))
                        .findFirst()
                        .orElseThrow();

                assertEquals(
                        Set.of("AB123.1|CDS_SHORT"),
                        reader.getTranslationOffsetForAnnotation(shortAccession).keySet(),
                        "AB123.10's translation was claimed by AB123.1");
            }
        }

        @Test
        @DisplayName("does not leak a prefix-colliding accession's translation into a written file")
        void doesNotLeakPrefixCollidingTranslations() throws Exception {
            String output = write(PREFIX_COLLIDING_ACCESSIONS, accession("AB123.1"), true, Optional.empty());

            assertTrue(output.contains(">AB123.1|CDS_SHORT"), "expected this file's own translation");
            assertFalse(output.contains(">AB123.10|CDS_LONG"), "AB123.10 leaked into a file scoped to AB123.1");
        }
    }

    // ---tests for which translation is written after validation (TTENA-339)

    @Nested
    @DisplayName("translations after validation: submitted ##FASTA, attribute and generated protein")
    class TranslationsAfterValidation {

        @ParameterizedTest(name = "{0}")
        @MethodSource("uk.ac.ebi.embl.gff3tools.gff3.Gff3FileWritingTest#translationCases")
        @DisplayName("fromAnnotationAndReader writes the expected protein, with the expected reports")
        void writesTheExpectedTranslation(TranslationCase translationCase) throws Exception {
            Written written = writeAfterValidation(translationCase);
            Map<String, String> fasta = fastaRecordsOf(written.factoryOutput());

            assertEquals(translationCase.expectedCds1(), fasta.get(CDS1_KEY), "cds1's written protein");
            // TranslationState is written alone, and an orphan never reaches it
            assertFalse(fasta.containsKey(ORPHAN_KEY), "the orphan ##FASTA record is not written");
            assertEquals(
                    translationCase.mismatchWarnings(),
                    countRule(written.warnings(), "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH"),
                    "TRANSLATION_ATTRIBUTE_FASTA_MISMATCH warnings");
            assertEquals(
                    translationCase.comparisonErrors(),
                    countRule(written.errors(), "TRANSLATION_COMPARISON"),
                    "TRANSLATION_COMPARISON errors");
            assertFalse(
                    featureLinesOf(written.factoryOutput()).contains("translation="),
                    "the translation attribute is never written back");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("uk.ac.ebi.embl.gff3tools.gff3.Gff3FileWritingTest#translationCases")
        @DisplayName("the GFF3File ValidationCommand builds writes the same ##FASTA")
        void validationCommandWritesTheSameFasta(TranslationCase translationCase) throws Exception {
            Written written = writeAfterValidation(translationCase);

            assertEquals(fastaRecordsOf(written.factoryOutput()), fastaRecordsOf(written.commandOutput()));
        }
    }

    @Nested
    @DisplayName("translations when converting a flat file: /translation, sequences and exception")
    class TranslationsFromAFlatFile {

        @ParameterizedTest(name = "{0}")
        @MethodSource("uk.ac.ebi.embl.gff3tools.gff3.Gff3FileWritingTest#flatFileTranslationCases")
        @DisplayName("GFF3FileFactory.from writes the expected protein, with the expected reports")
        void writesTheExpectedTranslation(FlatFileCase flatFileCase) throws Exception {
            ValidationEngine engine = flatFileCase.sequences()
                    ? validatingEngine(Map.of(FLAT_FILE_ACCESSION, SEQUENCE))
                    : validatingEngine(Map.of());

            String output =
                    convert(translationFlatFileEntry(flatFileCase.translation(), flatFileCase.exception()), engine);
            Map<String, String> fasta = fastaRecordsOf(output);

            assertEquals(
                    flatFileCase.expected() == null ? Set.of() : Set.of(flatFileCase.expected()),
                    Set.copyOf(fasta.values()),
                    "the written protein of the one CDS");
            assertEquals(
                    flatFileCase.comparisonErrors(),
                    countRule(engine.getCollectedErrors(), "TRANSLATION_COMPARISON"),
                    "TRANSLATION_COMPARISON errors");
            assertFalse(featureLinesOf(output).contains("translation="), "/translation is not kept as an attribute");
        }
    }

    // helpers

    private static Predicate<GFF3Annotation> all() {
        return a -> true;
    }

    private static Predicate<GFF3Annotation> accession(String accession) {
        return a -> accession.equals(a.getAccession());
    }

    private Path fallbackFile() throws Exception {
        Path fallback = tempDir.resolve("fallback.fasta");
        Files.writeString(fallback, FALLBACK_FASTA, Charset.defaultCharset());
        return fallback;
    }

    /**
     * Asserts that {@code output} holds exactly one FASTA section with every feature line before
     * it, and returns that section's body — so a translation counts only where a reader looks.
     */
    private String trailingFastaSectionOf(String output) {
        assertEquals(1, countOf(output, "##FASTA"), "a GFF3 document must not contain two FASTA sections");

        int marker = output.indexOf("##FASTA");
        assertTrue(marker >= 0, "expected a FASTA section, but the document has none:\n" + output);
        assertTrue(
                lastFeatureLineStart(output) < marker,
                "the FASTA section must follow every feature line, but features appear after it:\n" + output);

        return output.substring(marker + "##FASTA".length());
    }

    /** Start offset of the last tab-separated feature line, or -1 when there are none. */
    private static int lastFeatureLineStart(String output) {
        int last = -1;
        int lineStart = 0;
        for (String line : output.split("\n", -1)) {
            if (line.contains("\t")) {
                last = lineStart;
            }
            lineStart += line.length() + 1;
        }
        return last;
    }

    /** Writes the annotations selected by {@code select} the way the pipeline does. */
    private String write(
            String input, Predicate<GFF3Annotation> select, boolean appendTranslationFasta, Optional<Path> fallback)
            throws Exception {
        Path source = tempDir.resolve("input.gff3");
        Files.writeString(source, input, Charset.defaultCharset());

        StringWriter writer = new StringWriter();
        try (GFF3FileReader reader = new GFF3FileReader(engine(), new StringReader(input), source)) {
            reader.readHeader();
            List<GFF3Annotation> annotations = new ArrayList<>();
            reader.read(annotation -> {
                if (select.test(annotation)) {
                    annotations.add(annotation);
                }
            });
            GFF3FileFactory.fromAnnotationAndReader(annotations, reader, appendTranslationFasta, fallback)
                    .writeGFF3String(writer);
        }
        return writer.toString();
    }

    /** Converts an EMBL flat file the way {@code FFToGff3Converter} does. */
    private String convert(String flatFile) throws Exception {
        return convert(flatFile, engine());
    }

    /** Converts an EMBL flat file the way {@code FFToGff3Converter} does, with the given engine. */
    private String convert(String flatFile, ValidationEngine engine) throws Exception {
        ReaderOptions readerOptions = new ReaderOptions();
        readerOptions.setIgnoreSequence(true);

        StringWriter writer = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new StringReader(flatFile))) {
            EmblEntryReader entryReader =
                    new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "", readerOptions);
            new GFF3FileFactory(engine).from(entryReader, null).writeGFF3String(writer);
        }
        return writer.toString();
    }

    private List<GFF3Annotation> readAnnotations(String content) throws Exception {
        Path source = tempDir.resolve("roundtrip.gff3");
        Files.writeString(source, content, Charset.defaultCharset());

        List<GFF3Annotation> annotations = new ArrayList<>();
        try (GFF3FileReader reader = new GFF3FileReader(engine(), new StringReader(content), source)) {
            reader.readHeader();
            reader.read(annotations::add);
        }
        return annotations;
    }

    private ValidationEngine engine() {
        return new ValidationEngineBuilder().build();
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        int from = 0;
        int at;
        while ((at = haystack.indexOf(needle, from)) != -1) {
            count++;
            from = at + needle.length();
        }
        return count;
    }

    // helpers for the translation matrix

    /** ATG, 29 TTT codons and TAA (1-93) translate to {@link #RIGHT}; 7 more bases make it 100. */
    private static final String SEQUENCE = "ATG" + "TTT".repeat(29) + "TAA" + "TTTTTTT";

    private static final String RIGHT = "M" + "F".repeat(29);
    private static final String WRONG = "M" + "K".repeat(29);
    private static final String ORPHAN = "MORPHAN";
    private static final String CDS1_KEY = "seq1|cds1";
    private static final String ORPHAN_KEY = "seq1|cds99";
    private static final String FLAT_FILE_ACCESSION = "BN000065.1";

    enum CdsKind {
        NORMAL(""),
        EXCEPTION(";exception=ribosomal slippage"),
        PSEUDO(";pseudo=true");

        final String attributes;

        CdsKind(String attributes) {
            this.attributes = attributes;
        }
    }

    /** The inputs a-g of the TTENA-339 design: ##FASTA, attribute, sequences and engine. */
    enum TranslationInput {
        A_FASTA_RIGHT("a: ##FASTA right", RIGHT, null, true, false, false),
        B_FASTA_WRONG("b: ##FASTA wrong", WRONG, null, true, false, false),
        C_FASTA_WRONG_NO_SEQUENCES("c: ##FASTA wrong, no sequences", WRONG, null, false, false, false),
        D_NO_FASTA("d: no ##FASTA", null, null, true, false, false),
        E_FASTA_RIGHT_ATTRIBUTE_WRONG("e: ##FASTA right, attribute wrong", RIGHT, WRONG, true, false, false),
        F_FASTA_RIGHT_READ_ONLY_ENGINE("f: ##FASTA right, no fixes or validations", RIGHT, null, true, true, false),
        G_ORPHAN_FASTA_ONLY("g: ##FASTA only for a feature not in the file", null, null, true, false, true);

        final String description;
        final String fasta;
        final String attribute;
        final boolean sequences;
        final boolean readOnly;
        final boolean orphan;

        TranslationInput(
                String description,
                String fasta,
                String attribute,
                boolean sequences,
                boolean readOnly,
                boolean orphan) {
            this.description = description;
            this.fasta = fasta;
            this.attribute = attribute;
            this.sequences = sequences;
            this.readOnly = readOnly;
            this.orphan = orphan;
        }
    }

    /** One cell of the matrix: what is written for {@code cds1}, or null for no record. */
    record TranslationCase(
            CdsKind kind, TranslationInput input, String expectedCds1, int mismatchWarnings, int comparisonErrors) {
        @Override
        public String toString() {
            return kind + " CDS, " + input.description;
        }
    }

    static Stream<TranslationCase> translationCases() {
        return Stream.of(
                // A normal CDS is translated; the generated protein is written, and compared with the
                // submitted one when there is one.
                new TranslationCase(CdsKind.NORMAL, TranslationInput.A_FASTA_RIGHT, RIGHT, 0, 0),
                new TranslationCase(CdsKind.NORMAL, TranslationInput.B_FASTA_WRONG, RIGHT, 0, 1),
                new TranslationCase(CdsKind.NORMAL, TranslationInput.C_FASTA_WRONG_NO_SEQUENCES, WRONG, 0, 0),
                new TranslationCase(CdsKind.NORMAL, TranslationInput.D_NO_FASTA, RIGHT, 0, 0),
                new TranslationCase(CdsKind.NORMAL, TranslationInput.E_FASTA_RIGHT_ATTRIBUTE_WRONG, RIGHT, 1, 0),
                new TranslationCase(CdsKind.NORMAL, TranslationInput.F_FASTA_RIGHT_READ_ONLY_ENGINE, RIGHT, 0, 0),
                new TranslationCase(CdsKind.NORMAL, TranslationInput.G_ORPHAN_FASTA_ONLY, RIGHT, 0, 0),
                // An exception CDS is not translated; the submitted protein is accepted and written.
                new TranslationCase(CdsKind.EXCEPTION, TranslationInput.A_FASTA_RIGHT, RIGHT, 0, 0),
                new TranslationCase(CdsKind.EXCEPTION, TranslationInput.B_FASTA_WRONG, WRONG, 0, 0),
                new TranslationCase(CdsKind.EXCEPTION, TranslationInput.C_FASTA_WRONG_NO_SEQUENCES, WRONG, 0, 0),
                new TranslationCase(CdsKind.EXCEPTION, TranslationInput.D_NO_FASTA, null, 0, 0),
                new TranslationCase(CdsKind.EXCEPTION, TranslationInput.E_FASTA_RIGHT_ATTRIBUTE_WRONG, RIGHT, 1, 0),
                new TranslationCase(CdsKind.EXCEPTION, TranslationInput.F_FASTA_RIGHT_READ_ONLY_ENGINE, RIGHT, 0, 0),
                new TranslationCase(CdsKind.EXCEPTION, TranslationInput.G_ORPHAN_FASTA_ONLY, null, 0, 0),
                // A pseudo CDS is not translated either, but a submitted protein with sequences given
                // fails with "No new translation" (open question Q4); the submitted protein is written.
                new TranslationCase(CdsKind.PSEUDO, TranslationInput.A_FASTA_RIGHT, RIGHT, 0, 1),
                new TranslationCase(CdsKind.PSEUDO, TranslationInput.B_FASTA_WRONG, WRONG, 0, 1),
                new TranslationCase(CdsKind.PSEUDO, TranslationInput.C_FASTA_WRONG_NO_SEQUENCES, WRONG, 0, 0),
                new TranslationCase(CdsKind.PSEUDO, TranslationInput.D_NO_FASTA, null, 0, 0),
                new TranslationCase(CdsKind.PSEUDO, TranslationInput.E_FASTA_RIGHT_ATTRIBUTE_WRONG, RIGHT, 1, 1),
                new TranslationCase(CdsKind.PSEUDO, TranslationInput.F_FASTA_RIGHT_READ_ONLY_ENGINE, RIGHT, 0, 0),
                new TranslationCase(CdsKind.PSEUDO, TranslationInput.G_ORPHAN_FASTA_ONLY, null, 0, 0));
    }

    /** One flat-file conversion: the {@code /translation} given, and what is written for the CDS. */
    record FlatFileCase(
            String description,
            String translation,
            boolean exception,
            boolean sequences,
            String expected,
            int comparisonErrors) {
        @Override
        public String toString() {
            return description;
        }
    }

    static Stream<FlatFileCase> flatFileTranslationCases() {
        return Stream.of(
                new FlatFileCase("/translation right, sequences", RIGHT, false, true, RIGHT, 0),
                new FlatFileCase("/translation wrong, sequences", WRONG, false, true, RIGHT, 1),
                new FlatFileCase("/translation wrong, no sequences", WRONG, false, false, WRONG, 0),
                new FlatFileCase("no /translation, sequences", null, false, true, RIGHT, 0),
                new FlatFileCase("exception CDS, /translation wrong, sequences", WRONG, true, true, WRONG, 0));
    }

    /** What one matrix cell wrote, through the factory and as ValidationCommand builds it. */
    private record Written(
            String factoryOutput,
            String commandOutput,
            List<ValidationException> warnings,
            List<ValidationException> errors) {}

    /** Reads, validates and writes one matrix cell the way the pipeline does. */
    private Written writeAfterValidation(TranslationCase translationCase) throws Exception {
        TranslationInput input = translationCase.input();
        String gff3 = "##gff-version 3\n##sequence-region seq1 1 100\n"
                + "seq1\t.\tCDS\t1\t93\t.\t+\t0\tID=cds1" + translationCase.kind().attributes
                + (input.attribute != null ? ";translation=" + input.attribute : "") + "\n";
        if (input.fasta != null || input.orphan) {
            gff3 += "##FASTA\n"
                    + (input.fasta != null ? ">" + CDS1_KEY + "\n" + input.fasta + "\n" : "")
                    + (input.orphan ? ">" + ORPHAN_KEY + "\n" + ORPHAN + "\n" : "");
        }
        Path source = tempDir.resolve("translations.gff3");
        Files.writeString(source, gff3, Charset.defaultCharset());

        ValidationEngine engine = input.readOnly
                ? new ValidationEngineBuilder()
                        .disableAutodetectValidationsAndFixes()
                        .disableAutodetectContextProviders()
                        .build()
                : validatingEngine(input.sequences ? Map.of("seq1", SEQUENCE) : Map.of());

        try (GFF3FileReader reader = new GFF3FileReader(engine, new StringReader(gff3), source)) {
            GFF3Header header = reader.readHeader();
            List<GFF3Annotation> annotations = new ArrayList<>();
            reader.read(annotation -> {
                if (annotation != null) {
                    annotations.add(annotation);
                }
            });

            StringWriter factoryOutput = new StringWriter();
            GFF3FileFactory.fromAnnotationAndReader(annotations, reader, true, Optional.empty())
                    .writeGFF3String(factoryOutput);

            TranslationState state = engine.getContext().contains(TranslationState.class)
                    ? engine.getContext().get(TranslationState.class)
                    : null;
            StringWriter commandOutput = new StringWriter();
            new GFF3File(header, reader.gff3Species, annotations, reader, null, true, null, state)
                    .writeGFF3String(commandOutput);

            return new Written(
                    factoryOutput.toString(),
                    commandOutput.toString(),
                    List.copyOf(engine.getParsingWarnings()),
                    List.copyOf(engine.getCollectedErrors()));
        }
    }

    /** The full engine, with the given sequences when there are any. */
    private static ValidationEngine validatingEngine(Map<String, String> sequences) {
        ValidationEngineBuilder builder = new ValidationEngineBuilder();
        if (!sequences.isEmpty()) {
            CompositeSequenceProvider provider = new CompositeSequenceProvider();
            provider.addSource(TestUtils.inMemorySequenceSource(sequences));
            builder.withProvider(provider);
        }
        return builder.build();
    }

    /** One EMBL entry of 100 bp with one CDS over 1..93 and the given /translation, if any. */
    private static String translationFlatFileEntry(String translation, boolean exception) {
        StringBuilder entry = new StringBuilder()
                .append("ID   BN000065; SV 1; linear; genomic DNA; STD; HUM; 100 BP.\n")
                .append("XX\n")
                .append("AC   BN000065;\n")
                .append("XX\n")
                .append("FH   Key             Location/Qualifiers\n")
                .append("FH\n")
                .append("FT   source          1..100\n")
                .append("FT                   /mol_type=\"genomic DNA\"\n")
                .append("FT                   /organism=\"Homo sapiens\"\n")
                .append("FT   CDS             1..93\n")
                .append("FT                   /protein_id=\"CAD29848.1\"\n");
        if (exception) {
            entry.append("FT                   /exception=\"ribosomal slippage\"\n");
        }
        if (translation != null) {
            entry.append("FT                   /translation=\"")
                    .append(translation)
                    .append("\"\n");
        }
        return entry.append("XX\n//\n").toString();
    }

    /** The records of the document's ##FASTA section, header to protein, lines joined. */
    private static Map<String, String> fastaRecordsOf(String output) {
        Map<String, String> records = new LinkedHashMap<>();
        int marker = output.indexOf("##FASTA");
        if (marker < 0) {
            return records;
        }
        String header = null;
        for (String line : output.substring(marker + "##FASTA".length()).split("\n")) {
            if (line.startsWith(">")) {
                header = line.substring(1).trim();
                records.put(header, "");
            } else if (header != null && !line.isBlank()) {
                records.merge(header, line.trim(), String::concat);
            }
        }
        return records;
    }

    /** Everything above the ##FASTA section. */
    private static String featureLinesOf(String output) {
        int marker = output.indexOf("##FASTA");
        return marker < 0 ? output : output.substring(0, marker);
    }

    private static long countRule(List<ValidationException> reports, String rule) {
        return reports.stream()
                .filter(report -> rule.equals(report.getValidationRule()))
                .count();
    }
}
