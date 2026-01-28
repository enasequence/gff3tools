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
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.gff3tools.exception.TSVParseException;
import uk.ac.ebi.embl.gff3tools.exception.TemplateNotFoundException;

/**
 * Tests for TSVEntryReader.
 */
class TSVEntryReaderTest {

    @Test
    void testTemplateIdExtraction_plainText() throws Exception {
        // Create a simple TSV content with template ID
        String tsvContent = "Checklist ERT000002\n"
                + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent));
                TSVEntryReader tsvReader = new TSVEntryReader(reader)) {
            assertNotNull(tsvReader.getTemplateInfo());
            assertEquals("rRNA gene", tsvReader.getTemplateInfo().getName());
        }
    }

    @Test
    void testTemplateNotFound() {
        // Create a TSV content with missing template ID line
        String tsvContent =
                "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n" + "Escherichia coli\t16S\tno\tATGC\n";

        assertThrows(TemplateNotFoundException.class, () -> {
            try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent))) {
                new TSVEntryReader(reader);
            }
        });
    }

    @Test
    void testInvalidTemplateId() {
        // Create a TSV content with invalid template ID
        String tsvContent = "Checklist INVALID999\n"
                + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        assertThrows(TemplateNotFoundException.class, () -> {
            try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent))) {
                new TSVEntryReader(reader);
            }
        });
    }

    @Test
    void testAlternateTemplateIdFormat() throws Exception {
        // Create a TSV content with alternate template ID format
        String tsvContent = "#template_accession ERT000002\n"
                + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent));
                TSVEntryReader tsvReader = new TSVEntryReader(reader)) {
            assertNotNull(tsvReader.getTemplateInfo());
            assertEquals("rRNA gene", tsvReader.getTemplateInfo().getName());
        }
    }

    @Test
    void testTemplateIdInLaterLine() throws Exception {
        // Create a TSV content with template ID in a later line (but within first 10)
        String tsvContent = "# Comment line 1\n"
                + "# Comment line 2\n"
                + "Checklist ERT000002\n"
                + "Organism\tSedimentation coefficient\tENV_SAMPLE\tSequence\n"
                + "Escherichia coli\t16S\tno\tATGC\n";

        try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent));
                TSVEntryReader tsvReader = new TSVEntryReader(reader)) {
            assertNotNull(tsvReader.getTemplateInfo());
            assertEquals("rRNA gene", tsvReader.getTemplateInfo().getName());
        }
    }

    // Sequence must be at least 100 bp for rRNA template
    private static final String SEQUENCE_100BP =
            "ATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGCATGC";

    @Test
    void testReadEntry_singleEntry() throws Exception {
        // ERT000002 is the rRNA gene template
        // Column names must match template token names exactly (only required fields)
        String tsvContent = "Checklist ERT000002\n"
                + "ENTRYNUMBER\tORGANISM_NAME\tSEDIMENT\tENV_SAMPLE\tSEQUENCE\n"
                + "entry1\tEscherichia coli\t16S\tno\t" + SEQUENCE_100BP + "\n";

        try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent));
                TSVEntryReader tsvReader = new TSVEntryReader(reader)) {

            Entry entry = tsvReader.read();
            assertNotNull(entry, "Should read one entry");
            assertEquals("entry1", entry.getSubmitterAccession());

            Entry secondEntry = tsvReader.read();
            assertNull(secondEntry, "Should return null after last entry");
        }
    }

    @Test
    void testReadEntry_multipleEntries() throws Exception {
        String tsvContent = "Checklist ERT000002\n"
                + "ENTRYNUMBER\tORGANISM_NAME\tSEDIMENT\tENV_SAMPLE\tSEQUENCE\n"
                + "entry1\tEscherichia coli\t16S\tno\t" + SEQUENCE_100BP + "\n"
                + "entry2\tBacillus subtilis\t23S\tno\t" + SEQUENCE_100BP + "\n";

        try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent));
                TSVEntryReader tsvReader = new TSVEntryReader(reader)) {

            Entry entry1 = tsvReader.read();
            assertNotNull(entry1);
            assertEquals("entry1", entry1.getSubmitterAccession());

            Entry entry2 = tsvReader.read();
            assertNotNull(entry2);
            assertEquals("entry2", entry2.getSubmitterAccession());

            assertNull(tsvReader.read(), "Should return null after all entries");
        }
    }

    @Test
    void testReadEntry_lineNumberTracking() throws Exception {
        // Missing required field should throw TSVParseException with line number
        String tsvContent = "Checklist ERT000002\n"
                + "ENTRYNUMBER\tORGANISM_NAME\tSEDIMENT\tENV_SAMPLE\tSEQUENCE\n"
                + "entry1\t\t16S\tno\t" + SEQUENCE_100BP + "\n"; // Missing organism

        try (BufferedReader reader = new BufferedReader(new StringReader(tsvContent));
                TSVEntryReader tsvReader = new TSVEntryReader(reader)) {

            TSVParseException exception = assertThrows(TSVParseException.class, tsvReader::read);
            assertTrue(exception.getLineNumber() > 0, "Line number should be tracked");
            assertTrue(
                    exception.getMessage().contains("Line " + exception.getLineNumber()),
                    "Message should contain line number");
        }
    }
}
