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
package uk.ac.ebi.embl.gff3tools.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.EntryFactory;
import uk.ac.ebi.embl.api.entry.sequence.Sequence;
import uk.ac.ebi.embl.api.entry.sequence.SequenceFactory;

class ConversionUtilsTest {

    @Test
    void testGetEffectiveAccession_withSequenceAccession() {
        EntryFactory entryFactory = new EntryFactory();
        SequenceFactory sequenceFactory = new SequenceFactory();

        Entry entry = entryFactory.createEntry();
        Sequence sequence = sequenceFactory.createSequence();
        sequence.setAccession("ABC12345.1");
        entry.setSequence(sequence);
        entry.setSubmitterAccession("ENTRY_1");

        // Should prefer sequence accession over submitter accession
        assertEquals("ABC12345.1", ConversionUtils.getEffectiveAccession(entry));
    }

    @Test
    void testGetEffectiveAccession_fallbackToSubmitterAccession() {
        EntryFactory entryFactory = new EntryFactory();
        SequenceFactory sequenceFactory = new SequenceFactory();

        Entry entry = entryFactory.createEntry();
        Sequence sequence = sequenceFactory.createSequence();
        // No accession set on sequence
        entry.setSequence(sequence);
        entry.setSubmitterAccession("ENTRY_1");

        // Should fall back to submitter accession
        assertEquals("ENTRY_1", ConversionUtils.getEffectiveAccession(entry));
    }

    @Test
    void testGetEffectiveAccession_emptySequenceAccession() {
        EntryFactory entryFactory = new EntryFactory();
        SequenceFactory sequenceFactory = new SequenceFactory();

        Entry entry = entryFactory.createEntry();
        Sequence sequence = sequenceFactory.createSequence();
        sequence.setAccession("");
        entry.setSequence(sequence);
        entry.setSubmitterAccession("ENTRY_2");

        // Should fall back to submitter accession when accession is empty
        assertEquals("ENTRY_2", ConversionUtils.getEffectiveAccession(entry));
    }

    @Test
    void testGetEffectiveAccession_nullEntry() {
        assertNull(ConversionUtils.getEffectiveAccession(null));
    }

    @Test
    void testGetEffectiveAccession_nullSequence() {
        EntryFactory entryFactory = new EntryFactory();

        Entry entry = entryFactory.createEntry();
        entry.setSubmitterAccession("ENTRY_3");
        // No sequence set

        // Should fall back to submitter accession
        assertEquals("ENTRY_3", ConversionUtils.getEffectiveAccession(entry));
    }

    @Test
    void testGetEffectiveAccession_noAccessionAvailable() {
        EntryFactory entryFactory = new EntryFactory();
        SequenceFactory sequenceFactory = new SequenceFactory();

        Entry entry = entryFactory.createEntry();
        Sequence sequence = sequenceFactory.createSequence();
        entry.setSequence(sequence);
        // No submitter accession set either

        assertNull(ConversionUtils.getEffectiveAccession(entry));
    }
}
