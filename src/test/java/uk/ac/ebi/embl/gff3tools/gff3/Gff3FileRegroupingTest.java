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
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3FileFactory;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.tsvconverter.TSVToGFF3Converter;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

/**
 * Round-trip tests for regrouping: one written GFF3 document is read back and rewritten as several
 * documents, each holding a different subset of its annotations. This is the shape
 * webin-gff3-stages archives in — a submission split into one object per annotation group, each
 * object a standalone GFF3 carrying its own annotations and exactly their translations.
 *
 * <p>Two ways of splitting are covered, because the pipeline needs both: into <em>groups</em>
 * (chromosomes split out, the remainder bundled) and into <em>one document per annotation</em>
 * (every TSV annotation archived separately).
 *
 * <p>Inputs and expected outputs are cleartext files under {@code src/test/resources/regrouping}:
 *
 * <pre>
 * source.gff3                    5 annotations, one translated CDS each
 * expected-whole.gff3            all 5 written as one document; the input both nests regroup
 * groups/expected-1-2.gff3       ACC1.1 ACC2.1
 * groups/expected-3-4-5.gff3     ACC3.1 ACC4.1 ACC5.1
 * each/expected-ACC1_1.gff3 …    one document per annotation
 * tsv/cds-three-entries.tsv      3 CDS entries, the only TSV shape that yields translations
 * tsv/expected-converted.gff3    that TSV converted to GFF3
 * tsv/expected-1_1.gff3 …        the converted document, one annotation per file
 * </pre>
 *
 * <p>Groups are written from {@code expected-whole.gff3}, not from the source, so the tests
 * exercise the same two-pass path a separate archiving process would: translations survive only
 * through the document's own {@code ##FASTA}, because {@code TranslationState} is per-run and is
 * empty by the time the document is read back.
 *
 * <p><strong>All groups are produced from a single read.</strong> Annotations are routed into
 * their groups in one pass and every group is then written through the same reader, whose
 * translation offset map is parsed and bucketed once. Re-opening a reader per group instead costs
 * a full parse and a full validation pass each time — measured at roughly 180&nbsp;ms per pass for
 * 2000 annotations, so 100 groups took 2.1&nbsp;s where the one-read shape takes 0.09&nbsp;s
 * regardless of group count. That matters most for the per-annotation case below, where the group
 * count equals the annotation count. This test is also the reference for how a caller should drive
 * the API.
 */
public class Gff3FileRegroupingTest {

    private static final List<String> ALL = List.of("ACC1.1", "ACC2.1", "ACC3.1", "ACC4.1", "ACC5.1");
    private static final List<String> GROUP_1_2 = List.of("ACC1.1", "ACC2.1");
    private static final List<String> GROUP_3_4_5 = List.of("ACC3.1", "ACC4.1", "ACC5.1");

    /** Accessions the TSV fixture converts to — the entry number becomes the accession. */
    private static final List<String> TSV_ACCESSIONS = List.of("1.1", "2.1", "3.1");

    /** Each converted annotation's generated translation, distinct by design of the fixture. */
    private static final Map<String, String> TSV_TRANSLATIONS = Map.of(
            "1.1", "MAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "2.1", "MCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
            "3.1", "MDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");

    @TempDir
    Path tempDir;

    // =====================================================================
    @Nested
    @DisplayName("regrouped into groups of annotations")
    class IntoGroups {

        @Test
        @DisplayName("writes all five annotations as one document")
        void writesTheWholeSubmission() throws Exception {
            assertMatchesExpected("expected-whole.gff3", writeWhole());
        }

        @Test
        @DisplayName("splits the document into a group of two and a group of three")
        void splitsIntoTwoAndThree() throws Exception {
            Map<String, Path> groups = writeGroups(writeWhole(), partition());

            assertMatchesExpected("groups/expected-1-2.gff3", groups.get("group-1-2.gff3"));
            assertMatchesExpected("groups/expected-3-4-5.gff3", groups.get("group-3-4-5.gff3"));
        }

        @Test
        @DisplayName("every group reads back as the annotations it was given")
        void everyGroupReadsBack() throws Exception {
            Path whole = writeWhole();
            Map<String, Path> groups = writeGroups(whole, partition());

            assertEquals(ALL, readAccessions(whole));
            assertEquals(GROUP_1_2, readAccessions(groups.get("group-1-2.gff3")));
            assertEquals(GROUP_3_4_5, readAccessions(groups.get("group-3-4-5.gff3")));
        }

        @Test
        @DisplayName("the groups together hold every annotation and translation, exactly once")
        void losesAndDuplicatesNothing() throws Exception {
            Map<String, Path> groups = writeGroups(writeWhole(), partition());

            List<String> regrouped = new ArrayList<>();
            regrouped.addAll(readAccessions(groups.get("group-1-2.gff3")));
            regrouped.addAll(readAccessions(groups.get("group-3-4-5.gff3")));
            assertEquals(ALL, regrouped, "the groups together must hold every annotation, exactly once");

            String combined =
                    Files.readString(groups.get("group-1-2.gff3")) + Files.readString(groups.get("group-3-4-5.gff3"));
            for (int i = 1; i <= 5; i++) {
                assertEquals(
                        1,
                        countOf(combined, "CDS_%d\n%s".formatted(i, protein(i))),
                        "the translation of ACC%d.1 must appear in exactly one group".formatted(i));
            }
        }

        @Test
        @DisplayName("the same group written twice is byte-identical")
        void isByteStable() throws Exception {
            Path whole = writeWhole();

            assertEquals(
                    Files.readString(
                            writeGroups(whole, Map.of("a.gff3", GROUP_3_4_5)).get("a.gff3")),
                    Files.readString(
                            writeGroups(whole, Map.of("b.gff3", GROUP_3_4_5)).get("b.gff3")),
                    "byte-stable output is what lets a consumer skip an unchanged object by checksum");
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("regrouped into one document per annotation")
    class IntoOnePerAnnotation {

        @Test
        @DisplayName("writes one document per annotation")
        void writesOneDocumentEach() throws Exception {
            assertEquals(ALL.size(), eachOnItsOwn().size());
        }

        @Test
        @DisplayName("each matches its expected document")
        void eachMatchesItsExpectedDocument() throws Exception {
            Map<String, Path> each = eachOnItsOwn();

            for (String accession : ALL) {
                assertMatchesExpected("each/expected-%s.gff3".formatted(fileStem(accession)), each.get(accession));
            }
        }

        @Test
        @DisplayName("each holds exactly its own annotation")
        void eachHoldsOneAnnotation() throws Exception {
            Map<String, Path> each = eachOnItsOwn();

            for (String accession : ALL) {
                assertEquals(List.of(accession), readAccessions(each.get(accession)));
            }
        }

        @Test
        @DisplayName("each carries its own translation and no other")
        void eachCarriesOnlyItsOwnTranslation() throws Exception {
            Map<String, Path> each = eachOnItsOwn();

            for (int i = 1; i <= 5; i++) {
                String document = Files.readString(each.get("ACC%d.1".formatted(i)));
                assertTrue(document.contains(protein(i)), "ACC%d.1 lost its translation".formatted(i));
                for (int other = 1; other <= 5; other++) {
                    if (other != i) {
                        assertFalse(
                                document.contains(protein(other)),
                                "ACC%d.1 received the translation of ACC%d.1".formatted(i, other));
                    }
                }
            }
        }

        /** One document per annotation, all from a single read, keyed by accession. */
        private Map<String, Path> eachOnItsOwn() throws Exception {
            Map<String, List<String>> groups = new LinkedHashMap<>();
            ALL.forEach(accession -> groups.put(fileStem(accession) + ".gff3", List.of(accession)));

            Map<String, Path> written = writeGroups(writeWhole(), groups);
            Map<String, Path> byAccession = new LinkedHashMap<>();
            ALL.forEach(accession -> byAccession.put(accession, written.get(fileStem(accession) + ".gff3")));
            return byAccession;
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("converted from TSV, then one document per annotation")
    class FromTsv {

        @Test
        @DisplayName("converts the TSV to a GFF3 carrying its translations")
        void convertsWithTranslations() throws Exception {
            assertMatchesExpected("tsv/expected-converted.gff3", convertTsv());
        }

        @Test
        @DisplayName("writes one document per converted annotation")
        void writesOneDocumentEach() throws Exception {
            assertEquals(TSV_ACCESSIONS.size(), eachConvertedOnItsOwn().size());
        }

        @Test
        @DisplayName("each matches its expected document")
        void eachMatchesItsExpectedDocument() throws Exception {
            Map<String, Path> each = eachConvertedOnItsOwn();

            for (String accession : TSV_ACCESSIONS) {
                assertMatchesExpected("tsv/expected-%s.gff3".formatted(fileStem(accession)), each.get(accession));
            }
        }

        @Test
        @DisplayName("each carries its own translation and no other")
        void eachCarriesOnlyItsOwnTranslation() throws Exception {
            Map<String, Path> each = eachConvertedOnItsOwn();

            for (Map.Entry<String, String> entry : TSV_TRANSLATIONS.entrySet()) {
                String document = Files.readString(each.get(entry.getKey()));
                assertTrue(document.contains(entry.getValue()), entry.getKey() + " lost its translation");
                TSV_TRANSLATIONS.forEach((other, translation) -> {
                    if (!other.equals(entry.getKey())) {
                        assertFalse(
                                document.contains(translation),
                                "%s received the translation of %s".formatted(entry.getKey(), other));
                    }
                });
            }
        }

        /** The converted document split one annotation per file, all from a single read. */
        private Map<String, Path> eachConvertedOnItsOwn() throws Exception {
            Map<String, List<String>> groups = new LinkedHashMap<>();
            TSV_ACCESSIONS.forEach(accession -> groups.put(fileStem(accession) + ".gff3", List.of(accession)));

            Map<String, Path> written = writeGroups(convertTsv(), groups);
            Map<String, Path> byAccession = new LinkedHashMap<>();
            TSV_ACCESSIONS.forEach(accession -> byAccession.put(accession, written.get(fileStem(accession) + ".gff3")));
            return byAccession;
        }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    /**
     * Converts the TSV fixture to GFF3. Translations are generated during conversion and written
     * from {@code TranslationState}; they reach the per-annotation documents below only through
     * this file's own {@code ##FASTA}, since the state does not outlive the conversion.
     */
    private Path convertTsv() throws Exception {
        Path output = tempDir.resolve("converted.gff3");
        try (BufferedReader tsv = Files.newBufferedReader(resource("tsv/cds-three-entries.tsv"));
                BufferedWriter gff3 = Files.newBufferedWriter(output);
                ValidationEngine engine = engine()) {
            new TSVToGFF3Converter(engine).convert(tsv, gff3);
        }
        return output;
    }

    /** The two groups that partition the source between them. */
    private static Map<String, List<String>> partition() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("group-1-2.gff3", GROUP_1_2);
        groups.put("group-3-4-5.gff3", GROUP_3_4_5);
        return groups;
    }

    /** The five-annotation source, written out as one document. */
    private Path writeWhole() throws Exception {
        return writeGroups(resource("source.gff3"), Map.of("whole.gff3", ALL)).get("whole.gff3");
    }

    /**
     * Reads {@code source} <strong>once</strong> and writes each group as a standalone document.
     *
     * <p>Annotations are routed into their groups during the single pass, and every group is
     * written through that same reader — so the file is parsed once and validated once however
     * many groups come out of it, and the translation offset map is bucketed once and reused.
     *
     * @param source the document to regroup
     * @param groups output file name to the accessions that document should contain
     * @return each group's written path, by output file name
     */
    private Map<String, Path> writeGroups(Path source, Map<String, List<String>> groups) throws Exception {
        Map<String, List<GFF3Annotation>> selected = new LinkedHashMap<>();
        groups.keySet().forEach(name -> selected.put(name, new ArrayList<>()));
        Map<String, Path> written = new LinkedHashMap<>();

        try (BufferedReader text = Files.newBufferedReader(source);
                GFF3FileReader reader = new GFF3FileReader(engine(), text, source)) {
            reader.readHeader();
            reader.read(annotation -> groups.forEach((name, wanted) -> {
                if (wanted.contains(annotation.getAccession())) {
                    selected.get(name).add(annotation);
                }
            }));

            // Written inside the try: the reader still owns the file the translations are read from.
            for (Map.Entry<String, List<GFF3Annotation>> group : selected.entrySet()) {
                StringWriter writer = new StringWriter();
                GFF3FileFactory.fromAnnotationAndReader(group.getValue(), reader, true, Optional.empty())
                        .writeGFF3String(writer);
                Path output = tempDir.resolve(group.getKey());
                Files.writeString(output, writer.toString(), Charset.defaultCharset());
                written.put(group.getKey(), output);
            }
        }
        return written;
    }

    private List<String> readAccessions(Path file) throws Exception {
        List<String> accessions = new ArrayList<>();
        try (BufferedReader text = Files.newBufferedReader(file);
                GFF3FileReader reader = new GFF3FileReader(engine(), text, file)) {
            reader.readHeader();
            reader.read(annotation -> accessions.add(annotation.getAccession()));
        }
        return accessions;
    }

    private void assertMatchesExpected(String expectedResource, Path actual) throws Exception {
        assertEquals(
                Files.readString(resource(expectedResource)),
                Files.readString(actual),
                "written document does not match " + expectedResource);
    }

    private static Path resource(String name) {
        return TestUtils.getResourceFile("./regrouping/" + name).toPath();
    }

    /** {@code ACC1.1} becomes {@code ACC1_1}: dots are awkward in a file stem. */
    private static String fileStem(String accession) {
        return accession.replace('.', '_');
    }

    /**
     * Distinct translations, letters only: {@code GFF3TranslationReader.isValidSequence} accepts
     * A-Z and {@code *} alone, and a stray digit would abort its backwards scan of the FASTA
     * section.
     */
    private static String protein(int i) {
        return "MK" + "ACDEF".charAt(i - 1) + "PQRW";
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
