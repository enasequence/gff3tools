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

import java.io.BufferedReader;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.fftogff3.GFF3FileFactory;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

/**
 * Round-trip test for regrouping: one written GFF3 document is read back and rewritten as several
 * documents, each holding a different subset of its annotations.
 *
 * <p>This is the shape webin-gff3-stages archives in — a submission split into one object per
 * annotation group, each object a standalone GFF3 carrying its own annotations and exactly their
 * translations.
 *
 * <p>Inputs and expected outputs are cleartext files under {@code src/test/resources/regrouping}:
 *
 * <pre>
 * source.gff3                  5 annotations, one translated CDS each
 *   └─ expected-whole.gff3     all 5, written as one document
 *        ├─ expected-group-1-2.gff3      ACC1.1 ACC2.1
 *        ├─ expected-group-3-4-5.gff3    ACC3.1 ACC4.1 ACC5.1
 *        └─ expected-group-4.gff3        ACC4.1 alone
 * </pre>
 *
 * <p>Each group is written from {@code expected-whole.gff3}, not from the source, so the test
 * exercises the same two-pass path a separate archiving process would: the translations survive
 * only through the document's own {@code ##FASTA}, because {@code TranslationState} is per-run and
 * is empty by the time the document is read back.
 *
 * <p><strong>All groups are produced from a single read.</strong> Annotations are routed into their
 * groups in one pass and every group is then written through the same reader, whose translation
 * offset map is parsed once and cached. Re-opening a reader per group instead costs a full parse
 * and a full validation pass each time — measured at roughly 180&nbsp;ms per pass for 2000
 * annotations, so 100 groups took 2.1&nbsp;s where one pass takes 0.18&nbsp;s. That shape is
 * O(groups × file) and does not survive a TSV submission, where every annotation is its own group.
 * This test is also the reference for how a caller should drive the API.
 */
public class Gff3FileRegroupingTest {

    private static final List<String> ALL = List.of("ACC1.1", "ACC2.1", "ACC3.1", "ACC4.1", "ACC5.1");
    private static final List<String> GROUP_1_2 = List.of("ACC1.1", "ACC2.1");
    private static final List<String> GROUP_3_4_5 = List.of("ACC3.1", "ACC4.1", "ACC5.1");
    private static final List<String> GROUP_4 = List.of("ACC4.1");

    @TempDir
    Path tempDir;

    // ---------------------------------------------------------------------
    // Written output matches the expected documents, byte for byte
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("writes all five annotations as one document")
    void writesTheWholeSubmission() throws Exception {
        assertMatchesExpected("expected-whole.gff3", writeWhole());
    }

    @Test
    @DisplayName("regroups the document into two and three annotations, in one read")
    void regroupsIntoTwoAndThree() throws Exception {
        Map<String, Path> groups = writeGroups(writeWhole(), partition());

        assertMatchesExpected("expected-group-1-2.gff3", groups.get("group-1-2.gff3"));
        assertMatchesExpected("expected-group-3-4-5.gff3", groups.get("group-3-4-5.gff3"));
    }

    @Test
    @DisplayName("regroups the document into a single annotation")
    void regroupsIntoOne() throws Exception {
        Map<String, Path> groups = writeGroups(writeWhole(), Map.of("group-4.gff3", GROUP_4));

        assertMatchesExpected("expected-group-4.gff3", groups.get("group-4.gff3"));
    }

    // ---------------------------------------------------------------------
    // Properties the expected documents are only trustworthy because of
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("every regrouped document reads back as the annotations it was given")
    void everyGroupReadsBack() throws Exception {
        Path whole = writeWhole();
        Map<String, Path> groups = writeGroups(whole, partition());

        assertEquals(ALL, readAccessions(whole));
        assertEquals(GROUP_1_2, readAccessions(groups.get("group-1-2.gff3")));
        assertEquals(GROUP_3_4_5, readAccessions(groups.get("group-3-4-5.gff3")));
    }

    @Test
    @DisplayName("the groups together hold every annotation and every translation, exactly once")
    void regroupingLosesAndDuplicatesNothing() throws Exception {
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
    void regroupingIsByteStable() throws Exception {
        Path whole = writeWhole();

        assertEquals(
                Files.readString(
                        writeGroups(whole, Map.of("a.gff3", GROUP_3_4_5)).get("a.gff3")),
                Files.readString(
                        writeGroups(whole, Map.of("b.gff3", GROUP_3_4_5)).get("b.gff3")),
                "byte-stable output is what lets a consumer skip an unchanged object by checksum");
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

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
     * many groups come out of it, and the translation offset map is built once and reused.
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
