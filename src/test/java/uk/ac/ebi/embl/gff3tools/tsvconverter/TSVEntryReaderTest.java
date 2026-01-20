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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.gff3tools.exception.TemplateNotFoundException;

/**
 * Tests for TSVEntryReader.
 */
class TSVEntryReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testTemplateIdExtraction_plainText() throws Exception {
        // Create a simple TSV file with template ID
        String tsvContent = "Checklist ERT000002\n" + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        Path tsvFile = tempDir.resolve("test.tsv");
        Files.writeString(tsvFile, tsvContent);

        try (TSVEntryReader reader = new TSVEntryReader(tsvFile)) {
            assertNotNull(reader.getTemplateInfo());
            assertEquals("rRNA gene", reader.getTemplateInfo().getName());
        }
    }

    @Test
    void testTemplateIdExtraction_gzipped() throws Exception {
        // Create a gzipped TSV file with template ID
        String tsvContent = "Checklist ERT000002\n" + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        Path tsvFile = tempDir.resolve("test.tsv.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(tsvFile));
                OutputStreamWriter osw = new OutputStreamWriter(gzos, StandardCharsets.UTF_8)) {
            osw.write(tsvContent);
        }

        try (TSVEntryReader reader = new TSVEntryReader(tsvFile)) {
            assertNotNull(reader.getTemplateInfo());
            assertEquals("rRNA gene", reader.getTemplateInfo().getName());
        }
    }

    @Test
    void testTemplateNotFound() throws Exception {
        // Create a TSV file with missing template ID line
        String tsvContent =
                "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n" + "Escherichia coli\t16S\tno\tATGC\n";

        Path tsvFile = tempDir.resolve("test_no_template.tsv");
        Files.writeString(tsvFile, tsvContent);

        assertThrows(TemplateNotFoundException.class, () -> {
            new TSVEntryReader(tsvFile);
        });
    }

    @Test
    void testInvalidTemplateId() throws Exception {
        // Create a TSV file with invalid template ID
        String tsvContent = "Checklist INVALID999\n" + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        Path tsvFile = tempDir.resolve("test_invalid_template.tsv");
        Files.writeString(tsvFile, tsvContent);

        assertThrows(TemplateNotFoundException.class, () -> {
            new TSVEntryReader(tsvFile);
        });
    }

    @Test
    void testAlternateTemplateIdFormat() throws Exception {
        // Create a TSV file with alternate template ID format
        String tsvContent = "#template_accession ERT000002\n"
                + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n" + "Escherichia coli\t16S\tno\tATGC\n";

        Path tsvFile = tempDir.resolve("test_alternate.tsv");
        Files.writeString(tsvFile, tsvContent);

        try (TSVEntryReader reader = new TSVEntryReader(tsvFile)) {
            assertNotNull(reader.getTemplateInfo());
            assertEquals("rRNA gene", reader.getTemplateInfo().getName());
        }
    }

    @Test
    void testTemplateIdInLaterLine() throws Exception {
        // Create a TSV file with template ID in a later line (but within first 10)
        String tsvContent = "# Comment line 1\n" + "# Comment line 2\n" + "Checklist ERT000002\n"
                + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        Path tsvFile = tempDir.resolve("test_later_line.tsv");
        Files.writeString(tsvFile, tsvContent);

        try (TSVEntryReader reader = new TSVEntryReader(tsvFile)) {
            assertNotNull(reader.getTemplateInfo());
            assertEquals("rRNA gene", reader.getTemplateInfo().getName());
        }
    }
}
