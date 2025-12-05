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
package uk.ac.ebi.embl.gff3tools.fasta;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;

class FastaFileServiceIntegrationTest {


    @Test
    void basicFastaEntryManipulation_test() throws FastaFileException {
        File fasta = FastaTestResources.file("fasta", "example2.txt");
        FastaFileService service = new FastaFileService();
        service.openNewFile(fasta);

        List<FastaEntry> entries = service.getFastaEntries();
        assertEquals(2, entries.size(), "should parse 2 FASTA entries");

        Set<String> ids =
                Set.of(entries.get(0).getSubmissionId(), entries.get(1).getSubmissionId());
        assertTrue(ids.contains("ID1"));
        assertTrue(ids.contains("ID2"));

        Optional<FastaEntry> entry1 = service.getFastaWithSubmissionId("ID1");
        Optional<FastaEntry> entry2 = service.getFastaWithSubmissionId("ID2");
        Optional<FastaEntry> imaginaryEntry = service.getFastaWithSubmissionId("ID3");
        assertTrue(entry1.isPresent(), "index for ID1 must exist");
        assertTrue(entry2.isPresent(), "index for ID2 must exist");
        assertTrue(imaginaryEntry.isEmpty(), "index for ID3 must not exist");

        service.setAccessionId("ID1", "asc1");
        service.setAccessionId("ID2", "asc2");
        assertEquals(entry1.get().accessionId, "asc1");
        assertEquals(entry2.get().accessionId, "asc2");

        // From the sample file above:
        assertEquals(2, entry1.get().leadingNsCount, "ID1 leading Ns");
        assertEquals(2, entry1.get().trailingNsCount,   "ID1 trailing Ns");
        assertEquals(0, entry2.get().leadingNsCount, "ID2 leading Ns");
        assertEquals(0,entry2.get().trailingNsCount,   "ID2 trailing Ns");

        String sequence1 = service.getSequenceRangeAsString(SequenceRangeOption.WHOLE_SEQUENCE, entry1.get().submissionId, 1, entry1.get().totalBases);
        assertEquals("NNACACGTTTNn", sequence1);
        String sequence2 = service.getSequenceRangeAsString(SequenceRangeOption.WHOLE_SEQUENCE, entry2.get().submissionId, 1, entry2.get().totalBases);
        assertEquals("ACGTGGGG", sequence2);

        long adjustedTotalBases = entry1.get().totalBases - entry1.get().leadingNsCount - entry1.get().trailingNsCount;
        String sequence1withoutNbases = service.getSequenceRangeAsString(SequenceRangeOption.WITHOUT_N_BASES, entry1.get().submissionId, 1, adjustedTotalBases);
        assertEquals("ACACGTTT", sequence1withoutNbases);

        service.close();
    }
}
