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

import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;

public class FastaSubmissionReaderTest {

    @Test
    void doesNotTolerateImproperHeaders() {
        // by improper headers, i mean ones not in the EBI spec*/
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_improper_header.txt");

        assertThrows(FastaHeaderParserException.class, () -> new FastaSubmissionReader(fasta));
    }

    @Test
    void throwsWhenThereAreMultipleIdenticalSubmissionIds() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_duplicate_submission_id.txt"); // has 3 entries

        FastaFileException ex = assertThrows(FastaFileException.class, () -> new FastaSubmissionReader(fasta));

        assertTrue(
                ex.getMessage().contains("Duplicate submission ID detected: ID1"),
                "Expected duplicate submission ID to be ID1 but was: " + ex.getMessage());
    }

    @Test
    void throwsWhenNumberOfAccessionIdsDiffersFromTheActualNumberOfEntries() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt"); // has 3 entries
        List<String> accessionIds = List.of("acc1", "acc2");

        try (var reader = new FastaSubmissionReader(fasta)) {
            assertThrows(FastaFileException.class, () -> reader.setAccessionIds(accessionIds));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void basicWorkingMappingExample() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2", "acc3");

        try (var service = new FastaSubmissionReader(fasta)) {
            service.setAccessionIds(accessionIds);
            assertEquals("ID1", service.getSubmissionIdByAccessionId("acc1"));
            assertEquals("ID2", service.getSubmissionIdByAccessionId("acc2"));
            assertEquals("ID3", service.getSubmissionIdByAccessionId("acc3"));
            assertNull(service.getSubmissionIdByAccessionId("hehe"));
            assertEquals("acc1", service.getAccessionIdBySubmissionId("ID1"));
            assertEquals("acc2", service.getAccessionIdBySubmissionId("ID2"));
            assertEquals("acc3", service.getAccessionIdBySubmissionId("ID3"));
            assertNull(service.getAccessionIdBySubmissionId("hehe"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void basicWorkingMappingExampleWithConstructor() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2", "acc3");

        try (var service = new FastaSubmissionReader(fasta)) {
            service.setAccessionIds(accessionIds);
            assertEquals("ID1", service.getSubmissionIdByAccessionId("acc1"));
            assertEquals("ID2", service.getSubmissionIdByAccessionId("acc2"));
            assertEquals("ID3", service.getSubmissionIdByAccessionId("acc3"));
            assertNull(service.getSubmissionIdByAccessionId("hehe"));
            assertEquals("acc1", service.getAccessionIdBySubmissionId("ID1"));
            assertEquals("acc2", service.getAccessionIdBySubmissionId("ID2"));
            assertEquals("acc3", service.getAccessionIdBySubmissionId("ID3"));
            assertNull(service.getAccessionIdBySubmissionId("hehe"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void basicWorkingExample() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");

        try (var service = new FastaSubmissionReader(fasta)) {

            var entry1 = service.getStats(IdType.SUBMISSION_ID, "ID1");
            var entry2 = service.getStats(IdType.SUBMISSION_ID, "ID2");
            var entry3 = service.getStats(IdType.SUBMISSION_ID, "ID3");
            assertNotNull(entry1);
            assertNotNull(entry2);
            assertNotNull(entry3);
            assertThrows(IllegalArgumentException.class, () -> service.getStats(IdType.SUBMISSION_ID, "nonsense"));

            // From the sample file above:
            assertEquals(2, entry1.leadingNsCount(), "ID1 leading Ns");
            assertEquals(2, entry1.trailingNsCount(), "ID1 trailing Ns");
            assertEquals(4, entry1.baseCount().get('N'), "ID1 total Ns");

            assertEquals(0, entry2.leadingNsCount(), "ID2 leading Ns");
            assertEquals(0, entry2.trailingNsCount(), "ID2 trailing Ns");
            assertEquals(0, entry2.baseCount().get('N'), "ID2 total Ns");

            assertEquals(0, entry3.leadingNsCount(), "ID3 leading Ns");
            assertEquals(5, entry3.trailingNsCount(), "ID3 trailing Ns");
            assertEquals(13, entry3.baseCount().get('N'), "ID3 total Ns");

            String sequence1 = service.getSequenceSlice(
                    IdType.SUBMISSION_ID, "ID1", 1, entry1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("NNACACGTTTNN", sequence1);

            String sequence2 = service.getSequenceSlice(
                    IdType.SUBMISSION_ID, "ID2", 1, entry2.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("ACGTGGGG", sequence2);

            String sequence1withoutNbases = service.getSequenceSlice(
                    IdType.SUBMISSION_ID,
                    "ID1",
                    1,
                    entry1.totalBasesWithoutNBases(),
                    SequenceRangeOption.WITHOUT_EDGE_N_BASES);
            assertEquals("ACACGTTT", sequence1withoutNbases);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void basicWorkingExampleWithAccessionIds() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2", "acc3");

        try (var service = new FastaSubmissionReader(fasta)) {

            service.setAccessionIds(accessionIds);
            var entry1 = service.getStats(IdType.ACCESSION_ID, "acc1");
            var entry2 = service.getStats(IdType.ACCESSION_ID, "acc2");
            var entry3 = service.getStats(IdType.ACCESSION_ID, "acc3");
            assertNotNull(entry1);
            assertNotNull(entry2);
            assertNotNull(entry3);
            assertThrows(NullPointerException.class, () -> service.getStats(IdType.ACCESSION_ID, "nonsense"));

            // From the sample file above:
            assertEquals(2, entry1.leadingNsCount(), "ID1 leading Ns");
            assertEquals(2, entry1.trailingNsCount(), "ID1 trailing Ns");
            assertEquals(4, entry1.baseCount().get('N'), "ID1 total Ns");

            assertEquals(0, entry2.leadingNsCount(), "ID2 leading Ns");
            assertEquals(0, entry2.trailingNsCount(), "ID2 trailing Ns");
            assertEquals(0, entry2.baseCount().get('N'), "ID2 total Ns");

            assertEquals(0, entry3.leadingNsCount(), "ID3 leading Ns");
            assertEquals(5, entry3.trailingNsCount(), "ID3 trailing Ns");
            assertEquals(13, entry3.baseCount().get('N'), "ID3 total Ns");

            String sequence1 = service.getSequenceSlice(
                    IdType.ACCESSION_ID, "acc1", 1, entry1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("NNACACGTTTNN", sequence1);

            String sequence2 = service.getSequenceSlice(
                    IdType.ACCESSION_ID, "acc2", 1, entry2.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("ACGTGGGG", sequence2);

            String sequence1withoutNbases = service.getSequenceSlice(
                    IdType.ACCESSION_ID,
                    "acc1",
                    1,
                    entry1.totalBasesWithoutNBases(),
                    SequenceRangeOption.WITHOUT_EDGE_N_BASES);
            assertEquals("ACACGTTT", sequence1withoutNbases);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void basicStreamingSequenceExample() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");

        try (FastaSubmissionReader service = new FastaSubmissionReader(fasta)) {

            var entry1 = service.getStats(IdType.SUBMISSION_ID, "ID1");
            var entry2 = service.getStats(IdType.SUBMISSION_ID, "ID2");
            var entry3 = service.getStats(IdType.SUBMISSION_ID, "ID3");
            assertNotNull(entry1);
            assertNotNull(entry2);
            assertNotNull(entry3);
            assertEquals(Optional.empty(), service.getHeader(IdType.SUBMISSION_ID, "nonsense"));

            // From the sample file above:
            assertEquals(2, entry1.leadingNsCount(), "ID1 leading Ns");
            assertEquals(2, entry1.trailingNsCount(), "ID1 trailing Ns");
            assertEquals(4, entry1.baseCount().get('N'), "ID1 total Ns");

            assertEquals(0, entry2.leadingNsCount(), "ID2 leading Ns");
            assertEquals(0, entry2.trailingNsCount(), "ID2 trailing Ns");
            assertEquals(0, entry2.baseCount().get('N'), "ID2 total Ns");

            assertEquals(0, entry3.leadingNsCount(), "ID3 leading Ns");
            assertEquals(5, entry3.trailingNsCount(), "ID3 trailing Ns");
            assertEquals(13, entry3.baseCount().get('N'), "ID3 total Ns");

            // stream whole sequence with the reader
            String streamedSequence;
            try (java.io.Reader r = service.getSequenceSliceReader(
                    IdType.SUBMISSION_ID, "ID1", 1, entry1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {
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
                    IdType.SUBMISSION_ID,
                    "ID1",
                    1,
                    entry1.totalBasesWithoutNBases(),
                    SequenceRangeOption.WITHOUT_EDGE_N_BASES)) {
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
            try (java.io.Reader r = service.getSequenceSliceReader(
                    IdType.SUBMISSION_ID, "ID2", 1, entry2.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {
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

    @Test
    void basicStreamingSequenceExampleWithAccessionIds() {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2", "acc3");

        try (var service = new FastaSubmissionReader(fasta)) {

            service.setAccessionIds(accessionIds);
            var entry1 = service.getStats(IdType.ACCESSION_ID, "acc1");
            var entry2 = service.getStats(IdType.ACCESSION_ID, "acc2");
            var entry3 = service.getStats(IdType.ACCESSION_ID, "acc3");
            assertNotNull(entry1);
            assertNotNull(entry2);
            assertNotNull(entry3);
            assertThrows(NullPointerException.class, () -> service.getStats(IdType.ACCESSION_ID, "nonsense"));

            // From the sample file above:
            assertEquals(2, entry1.leadingNsCount(), "ID1 leading Ns");
            assertEquals(2, entry1.trailingNsCount(), "ID1 trailing Ns");
            assertEquals(4, entry1.baseCount().get('N'), "ID1 total Ns");

            assertEquals(0, entry2.leadingNsCount(), "ID2 leading Ns");
            assertEquals(0, entry2.trailingNsCount(), "ID2 trailing Ns");
            assertEquals(0, entry2.baseCount().get('N'), "ID2 total Ns");

            assertEquals(0, entry3.leadingNsCount(), "ID3 leading Ns");
            assertEquals(5, entry3.trailingNsCount(), "ID3 trailing Ns");
            assertEquals(13, entry3.baseCount().get('N'), "ID3 total Ns");

            // stream whole sequence with the reader
            String streamedSequence;
            try (java.io.Reader r = service.getSequenceSliceReader(
                    IdType.ACCESSION_ID, "acc1", 1, entry1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {
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
                    IdType.ACCESSION_ID,
                    "acc1",
                    1,
                    entry1.totalBasesWithoutNBases(),
                    SequenceRangeOption.WITHOUT_EDGE_N_BASES)) {
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
            try (java.io.Reader r = service.getSequenceSliceReader(
                    IdType.ACCESSION_ID, "acc2", 1, entry2.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {
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
