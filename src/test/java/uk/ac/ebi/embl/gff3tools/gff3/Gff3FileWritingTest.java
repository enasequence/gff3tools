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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3FileFactory;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

/**
 * Contract tests for the GFF3 documents written by {@link GFF3File}, however they were built —
 * from pre-parsed annotations plus a reader ({@link GFF3FileFactory#fromAnnotationAndReader}), or
 * converted from an EMBL flat file ({@link GFF3FileFactory#from}).
 *
 * <p>For the reader-based route the specification is the javadoc
 * of {@link GFF3FileFactory#fromAnnotationAndReader} as the specification:
 *
 * <ul>
 *   <li>{@code appendTranslationFasta} — "flag indicating whether to append annotation FASTA
 *       output". So {@code false} appends nothing, and {@code true} appends the annotations'
 *       translations.
 *   <li>{@code existingTranslationFilePathFallback} — "will be defaulted to if the
 *       {@link uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState} is not available".
 * </ul>
 *
 * <p>Independent of that flag, any GFF3 document the writer produces must be well formed: the
 * GFF3 specification makes {@code ##FASTA} terminate the feature section, so a document has at
 * most one of them and nothing but sequence data after it, and reading a written document back
 * must recover the annotations that were written.
 *
 * <p>See {@code docs/2609071330_annotation_scoped_fasta_writing.md}.
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

    private static final String FALLBACK_FASTA = ">FALLBACK|CDS_Z\nMMMMMMMM\n";

    @TempDir
    Path tempDir;

    // =====================================================================
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
         * The fallback parameter is documented as supplying translations when {@code
         * TranslationState} is unavailable — but this flag is documented as appending no FASTA at
         * all. The two clauses cannot both hold, so this pins down which one wins: appending
         * nothing.
         */
        @Test
        @DisplayName("ignores the fallback FASTA file")
        void ignoresTheFallbackFile() throws Exception {
            String output = write(TWO_ANNOTATIONS, all(), false, Optional.of(fallbackFile()));

            assertEquals(0, countOf(output, "##FASTA"), "no FASTA output was requested");
            assertFalse(output.contains(">FALLBACK"), "fallback file used despite append=false");
        }
    }

    // =====================================================================
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

        /**
         * Documented behaviour of {@code existingTranslationFilePathFallback}. "Not available" is
         * read as "yields no translations": {@code TranslationState} is always present in the
         * validation context via its auto-discovered provider, so presence alone cannot be the
         * trigger or the parameter could never fire.
         */
        @Test
        @DisplayName("falls back to the supplied FASTA file when TranslationState yields nothing")
        void fallsBackToTheSuppliedFastaFile() throws Exception {
            String withoutTranslations = "##gff-version 3\n"
                    + "##species http://example.org?name=Homo sapiens\n"
                    + "##sequence-region BN000065.1 1 315242\n"
                    + "BN000065.1\t.\tCDS\t1\t315242\t.\t+\t.\tID=CDS_A;gene=RHD;\n\n";

            String output = write(withoutTranslations, all(), true, Optional.of(fallbackFile()));

            assertTrue(output.contains(">FALLBACK"), "the documented fallback file was never read");
            assertTrue(
                    trailingFastaSectionOf(output).contains(">FALLBACK"),
                    "the fallback translations must be appended in the trailing FASTA section");
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

    // =====================================================================
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

    // =====================================================================
    @Nested
    @DisplayName("selecting an annotation's translations")
    class TranslationSelection {

        /**
         * Keys are {@code accession|featureId}, but {@code getTranslationOffsetForAnnotation}
         * selects with {@code key.startsWith(accession)}, so an accession that is a string prefix
         * of another claims the other's translations. The realistic trigger is mixed versioning of
         * seqIds — {@code AB123456} claiming every key of {@code AB123456.1}.
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

    // =====================================================================
    // helpers
    // =====================================================================

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
     * Asserts that {@code output} is a structurally well-formed GFF3 document and returns the body
     * of its FASTA section.
     *
     * <p>The GFF3 specification makes {@code ##FASTA} terminate the feature section, so a document
     * carries at most one of them and every feature line precedes it. Content assertions run
     * against the returned body rather than the whole document, so a translation only counts when
     * it is appended in the one place a reader will look for it.
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
        ValidationEngine engine = engine();
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
}
