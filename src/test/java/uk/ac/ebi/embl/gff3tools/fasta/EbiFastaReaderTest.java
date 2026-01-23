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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.FastaEntry;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;

public class EbiFastaReaderTest {

    @Test
    void doesNotTolerateImproperHeaders() throws IOException {
        // by improper headers, i mean ones not in the EBI spec*/
        File fasta = TestUtils.getResourceFile("./fasta/fasta_improper_header.txt");
        List<String> accessionIds = List.of("acc1");

        assertThrows(FastaHeaderParserException.class, () -> new EbiFastaReader(fasta, accessionIds));
    }

    @Test
    void throwsWhenNumberOfAccessionIdsDiffersFromTheActualNumberOfEntries() throws IOException {
        File fasta = TestUtils.getResourceFile("./fasta/fasta_good_example.txt"); // has 3 entries
        List<String> accessionIds = List.of("acc1", "acc2");

        assertThrows(FastaFileException.class, () -> new EbiFastaReader(fasta, accessionIds));
    }

    @Test
    void basicWorkingExample() throws IOException, FastaHeaderParserException, FastaFileException {
        File fasta = TestUtils.getResourceFile("./fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2", "acc3");

        try (EbiFastaReader service = new EbiFastaReader(fasta, accessionIds)) {

            FastaEntry entry1 = service.getFastaEntryByAccessionId("acc1");
            FastaEntry entry2 = service.getFastaEntryByAccessionId("acc2");
            FastaEntry entry3 = service.getFastaEntryByAccessionId("acc3");
            assertNotNull(entry1);
            assertNotNull(entry2);
            assertNotNull(entry3);
            assertNull(service.getFastaHeaderByAccessionId("nonsense"));

            // From the sample file above:
            assertEquals(2, entry1.leadingNsCount, "ID1 leading Ns");
            assertEquals(2, entry1.trailingNsCount, "ID1 trailing Ns");
            assertEquals(4, entry1.totalNBasesCount, "ID1 total Ns");

            assertEquals(0, entry2.leadingNsCount, "ID2 leading Ns");
            assertEquals(0, entry2.trailingNsCount, "ID2 trailing Ns");
            assertEquals(0, entry2.totalNBasesCount, "ID2 total Ns");

            assertEquals(0, entry3.leadingNsCount, "ID3 leading Ns");
            assertEquals(5, entry3.trailingNsCount, "ID3 trailing Ns");
            assertEquals(13, entry3.totalNBasesCount, "ID3 total Ns");

            String sequence1 =
                    service.getSequenceSlice("acc1", 1, entry1.totalBases, SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("NNACACGTTTNN", sequence1);

            String sequence2 =
                    service.getSequenceSlice("acc2", 1, entry2.totalBases, SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("ACGTGGGG", sequence2);

            String sequence1withoutNbases = service.getSequenceSlice(
                    "acc1", 1, entry1.totalBasesWithoutNBases, SequenceRangeOption.WITHOUT_EDGE_N_BASES);
            assertEquals("ACACGTTT", sequence1withoutNbases);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void basicStreamingSequenceExample() throws IOException, FastaHeaderParserException, FastaFileException {
        File fasta = TestUtils.getResourceFile("./fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2", "acc3");

        try (EbiFastaReader service = new EbiFastaReader(fasta, accessionIds)) {

            FastaEntry entry1 = service.getFastaEntryByAccessionId("acc1");
            FastaEntry entry2 = service.getFastaEntryByAccessionId("acc2");
            FastaEntry entry3 = service.getFastaEntryByAccessionId("acc3");
            assertNotNull(entry1);
            assertNotNull(entry2);
            assertNotNull(entry3);
            assertNull(service.getFastaHeaderByAccessionId("nonsense"));

            // From the sample file above:
            assertEquals(2, entry1.leadingNsCount, "ID1 leading Ns");
            assertEquals(2, entry1.trailingNsCount, "ID1 trailing Ns");
            assertEquals(4, entry1.totalNBasesCount, "ID1 total Ns");

            assertEquals(0, entry2.leadingNsCount, "ID2 leading Ns");
            assertEquals(0, entry2.trailingNsCount, "ID2 trailing Ns");
            assertEquals(0, entry2.totalNBasesCount, "ID2 total Ns");

            assertEquals(0, entry3.leadingNsCount, "ID3 leading Ns");
            assertEquals(5, entry3.trailingNsCount, "ID3 trailing Ns");
            assertEquals(13, entry3.totalNBasesCount, "ID3 total Ns");

            // stream whole sequence with the reader
            String streamedSequence;
            try (java.io.Reader r =
                    service.getSequenceSliceReader("acc1", 1, entry1.totalBases, SequenceRangeOption.WHOLE_SEQUENCE)) {
                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = r.read(cbuf)) != -1) {
                    sb.append(cbuf, 0, n);
                }
                streamedSequence = sb.toString();
            }
            // compare
            assertEquals("NNACACGTTTNN", streamedSequence);

            // stream whole sequence with the reader
            String streamedSequenceWithoutNbases;
            try (java.io.Reader r = service.getSequenceSliceReader(
                    "acc1", 1, entry1.totalBasesWithoutNBases, SequenceRangeOption.WITHOUT_EDGE_N_BASES)) {
                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = r.read(cbuf)) != -1) {
                    sb.append(cbuf, 0, n);
                }
                streamedSequenceWithoutNbases = sb.toString();
            }
            // compare
            assertEquals("ACACGTTT", streamedSequenceWithoutNbases);

            // stream sequence with the reader
            String streamedSequence2;
            try (java.io.Reader r =
                    service.getSequenceSliceReader("acc2", 1, entry2.totalBases, SequenceRangeOption.WHOLE_SEQUENCE)) {
                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = r.read(cbuf)) != -1) {
                    sb.append(cbuf, 0, n);
                }
                streamedSequence2 = sb.toString();
            }
            // compare
            assertEquals("ACGTGGGG", streamedSequence2);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
