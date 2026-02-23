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
package uk.ac.ebi.embl.gff3tools.sequence.readers;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.exception.SequenceFileException;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.fasta.header.utils.FastaHeader;

public class PlainSequenceSubmissionReaderTest {

    @Test
    void openPlainSequence_basicWorkingExample_withoutHeader() throws Exception {
        File seqFile = TestUtils.getResourceFile("sequence/plain/plain_good_sequence.txt");
        String accessionId = "acc1";

        try (var r = new PlainSequenceSubmissionReader(seqFile, accessionId, null)) {
            // optional header absent
            assertTrue(r.getHeader(IdType.ACCESSION_ID, "acc1").isEmpty());

            var stats = r.getStats(IdType.ACCESSION_ID, "acc1");
            assertNotNull(stats);

            // Basic sanity: slice whole sequence equals streaming whole sequence
            String whole = r.getSequenceSlice(
                    IdType.ACCESSION_ID, "acc1", 1, stats.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);

            String streamed;
            try (Reader rr = r.getSequenceSliceReader(
                    IdType.ACCESSION_ID, "acc1", 1, stats.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {

                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = rr.read(cbuf)) != -1) sb.append(cbuf, 0, n);
                streamed = sb.toString();
            }

            assertEquals(whole, streamed);
        }
    }

    @Test
    void openPlainSequence_allowsSetAccessionIdsButValidatesSingleId() throws Exception {
        File seqFile = TestUtils.getResourceFile("sequence/plain/plain_good_sequence.txt");
        String accessionId = "acc1";

        try (var r = new PlainSequenceSubmissionReader(seqFile, accessionId, null)) {
            r.setAccessionIds(List.of("acc1"));
            // wrong count
            assertThrows(IllegalArgumentException.class, () -> r.setAccessionIds(List.of("acc1", "acc2")));
            // wrong id
            assertThrows(IllegalArgumentException.class, () -> r.setAccessionIds(List.of("nope")));
        }
    }

    @Test
    void openPlainSequence_rejectsNonUtf8File() throws IOException {
        File nonUtf8 = TestUtils.getResourceFile("sequence/plain/plain_not_utf8.txt");

        assertThrows(SequenceFileException.class, () -> {
            try (var r = new PlainSequenceSubmissionReader(nonUtf8, "acc1", null)) {
                // no-op
            }
        });
    }

    @Test
    void openPlainSequence_rejectsEmptyFile() throws IOException {
        File empty = TestUtils.getResourceFile("sequence/plain/plain_empty.txt");

        assertThrows(SequenceFileException.class, () -> {
            try (var r = new PlainSequenceSubmissionReader(empty, "acc1", null)) {
                // no-op
            }
        });
    }

    @Test
    void openPlainSequence_canReturnOptionalHeaderWhenProvided() throws Exception {
        File seqFile = TestUtils.getResourceFile("sequence/plain/plain_good_sequence.txt");

        FastaHeader h = new FastaHeader();
        h.setDescription("desc");
        h.setMoleculeType("DNA");
        h.setTopology("linear");

        try (var r = new PlainSequenceSubmissionReader(seqFile, "acc1", h)) {
            var got = r.getHeader(IdType.ACCESSION_ID, "acc1");
            assertTrue(got.isPresent());
            assertEquals("desc", got.get().getDescription());
            assertEquals("DNA", got.get().getMoleculeType());
            assertEquals("linear", got.get().getTopology());
        }
    }
}
