package uk.ac.ebi.embl.gff3tools.sequence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.fastareader.exception.SequenceFileException;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.FastaHeaderParserException;
import uk.ac.ebi.embl.gff3tools.exception.FastaAccessionAssignmentException;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionSequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.headerutils.FastaHeader;

public class SubmissionReadersTest {

    // ---------------- FASTA ----------------

    @Test
    void openFasta_doesNotTolerateImproperHeaders() throws IOException {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_improper_header.txt");

        assertThrows(FastaHeaderParserException.class, () -> {
            try (SubmissionSequenceReader r = SubmissionReaders.openFasta(fasta)) {
                // no-op
            }
        });
    }

    @Test
    void openFasta_throwsWhenThereAreMultipleIdenticalSubmissionIds() throws IOException {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_duplicate_submission_id.txt");

        FastaFileException ex = assertThrows(FastaFileException.class, () -> {
            try (SubmissionSequenceReader r = SubmissionReaders.openFasta(fasta)) {
                // no-op
            }
        });

        assertTrue(
                ex.getMessage().contains("Duplicate submission ID detected: ID1"),
                "Expected duplicate submission ID to be ID1 but was: " + ex.getMessage());
    }

    @Test
    void openFasta_throwsWhenNumberOfAccessionIdsDiffersFromNumberOfEntries() throws Exception {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2"); // file has 3 entries

        try (SubmissionSequenceReader r = SubmissionReaders.openFasta(fasta)) {
            assertThrows(FastaAccessionAssignmentException.class, () -> r.setAccessionIds(accessionIds));
        }
    }

    @Test
    void openFasta_basicWorkingExampleWithSubmissionIds() throws Exception {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");

        try (SubmissionSequenceReader r = SubmissionReaders.openFasta(fasta)) {

            // stats via submission ids
            var s1 = r.getStats(RecordIdType.SUBMISSION_ID, "ID1");
            var s2 = r.getStats(RecordIdType.SUBMISSION_ID, "ID2");
            var s3 = r.getStats(RecordIdType.SUBMISSION_ID, "ID3");

            assertNotNull(s1);
            assertNotNull(s2);
            assertNotNull(s3);

            // Header may exist for FASTA (depends on your file); "nonsense" should not exist
            assertTrue(r.getHeader(RecordIdType.SUBMISSION_ID, "nonsense").isEmpty());

            // From your sample expectations:
            assertEquals(2, s1.leadingNsCount(), "ID1 leading Ns");
            assertEquals(2, s1.trailingNsCount(), "ID1 trailing Ns");
            assertEquals(4, s1.baseCount().get('N'), "ID1 total Ns");

            assertEquals(0, s2.leadingNsCount(), "ID2 leading Ns");
            assertEquals(0, s2.trailingNsCount(), "ID2 trailing Ns");
            assertEquals(0, s2.baseCount().get('N'), "ID2 total Ns");

            assertEquals(0, s3.leadingNsCount(), "ID3 leading Ns");
            assertEquals(5, s3.trailingNsCount(), "ID3 trailing Ns");
            assertEquals(13, s3.baseCount().get('N'), "ID3 total Ns");

            String seq1 = r.getSequenceSlice(
                    RecordIdType.SUBMISSION_ID, "ID1", 1, s1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("NNACACGTTTNN", seq1);

            String seq2 = r.getSequenceSlice(
                    RecordIdType.SUBMISSION_ID, "ID2", 1, s2.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("ACGTGGGG", seq2);

            String seq1WithoutNs = r.getSequenceSlice(
                    RecordIdType.SUBMISSION_ID,
                    "ID1",
                    1,
                    s1.totalBasesWithoutNBases(),
                    SequenceRangeOption.WITHOUT_EDGE_N_BASES);
            assertEquals("ACACGTTT", seq1WithoutNs);
        }
    }

    @Test
    void openFasta_basicWorkingExampleWithAccessionIds() throws Exception {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");
        List<String> accessionIds = List.of("acc1", "acc2", "acc3");

        try (SubmissionSequenceReader r = SubmissionReaders.openFasta(fasta)) {
            r.setAccessionIds(accessionIds);

            var s1 = r.getStats(RecordIdType.ACCESSION_ID, "acc1");
            var s2 = r.getStats(RecordIdType.ACCESSION_ID, "acc2");

            assertNotNull(s1);
            assertNotNull(s2);

            // Unlike your old test that expects NullPointerException from resolveId,
            // the unified interface *should* ideally throw something nicer.
            // If you kept the NullPointerException behavior, change to assertThrows(NullPointerException.class, ...).
            assertThrows(RuntimeException.class, () -> r.getHeader(RecordIdType.ACCESSION_ID, "nonsense"));

            String seq1 = r.getSequenceSlice(
                    RecordIdType.ACCESSION_ID, "acc1", 1, s1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("NNACACGTTTNN", seq1);

            String seq2 = r.getSequenceSlice(
                    RecordIdType.ACCESSION_ID, "acc2", 1, s2.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);
            assertEquals("ACGTGGGG", seq2);
        }
    }

    @Test
    void openFasta_basicStreamingSequenceExample() throws Exception {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");

        try (SubmissionSequenceReader r = SubmissionReaders.openFasta(fasta)) {
            var s1 = r.getStats(RecordIdType.SUBMISSION_ID, "ID1");

            String streamed;
            try (Reader rr = r.getSequenceSliceReader(
                    RecordIdType.SUBMISSION_ID, "ID1", 1, s1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {

                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = rr.read(cbuf)) != -1) sb.append(cbuf, 0, n);
                streamed = sb.toString();
            }

            assertEquals("NNACACGTTTNN", streamed);
        }
    }

    // ---------------- PLAIN SEQUENCE ----------------

    @Test
    void openPlainSequence_basicWorkingExample_withoutHeader() throws Exception {
        File seqFile = TestUtils.getResourceFile("sequence/plain/plain_good_sequence.txt");
        String accessionId = "acc1";

        try (SubmissionSequenceReader r = SubmissionReaders.openPlainSequence(seqFile, accessionId, null)) {
            // optional header absent
            assertTrue(r.getHeader(RecordIdType.ACCESSION_ID, "acc1").isEmpty());

            var stats = r.getStats(RecordIdType.ACCESSION_ID, "acc1");
            assertNotNull(stats);

            // Basic sanity: slice whole sequence equals streaming whole sequence
            String whole = r.getSequenceSlice(
                    RecordIdType.ACCESSION_ID, "acc1", 1, stats.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE);

            String streamed;
            try (Reader rr = r.getSequenceSliceReader(
                    RecordIdType.ACCESSION_ID, "acc1", 1, stats.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {

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

        try (SubmissionSequenceReader r = SubmissionReaders.openPlainSequence(seqFile, accessionId, null)) {
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
            try (SubmissionSequenceReader r = SubmissionReaders.openPlainSequence(nonUtf8, "acc1", null)) {
                // no-op
            }
        });
    }

    @Test
    void openPlainSequence_rejectsEmptyFile() throws IOException {
        File empty = TestUtils.getResourceFile("sequence/plain/plain_empty.txt");

        assertThrows(SequenceFileException.class, () -> {
            try (SubmissionSequenceReader r = SubmissionReaders.openPlainSequence(empty, "acc1", null)) {
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

        try (SubmissionSequenceReader r = SubmissionReaders.openPlainSequence(seqFile, "acc1", h)) {
            var got = r.getHeader(RecordIdType.ACCESSION_ID, "acc1");
            assertTrue(got.isPresent());
            assertEquals("desc", got.get().getDescription());
            assertEquals("DNA", got.get().getMoleculeType());
            assertEquals("linear", got.get().getTopology());
        }
    }
}
