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
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.exception.FastaFileException;

class FastaFileServiceIntegrationTest {

    @Test
    void readingMalformedFastaJsonFailure() throws IOException { // more tests like this in the JsonHeaderParserTest
        File fasta = FastaTestResources.file("fasta", "malformed_json_fasta.txt");
        FastaFileService service = new FastaFileService();

        assertThrows(FastaFileException.class, () -> {
            service.openNewFile(fasta);
        });

        service.close();
    }

    @Test
    void readingMalformedFastaSequenceFailure() throws IOException {
        File fasta = FastaTestResources.file("fasta", "malformed_fasta.txt");
        FastaFileService service = new FastaFileService();

        assertThrows(FastaFileException.class, () -> {
            service.openNewFile(fasta);
        });

        service.close();
    }

    @Test
    void gettingSequenceSliceAsStringReturnsCorrectly() throws IOException, FastaFileException {
        File fasta = FastaTestResources.file("fasta", "example2.txt");
        FastaFileService service = new FastaFileService();
        service.openNewFile(fasta);

        List<FastaEntry> entries = service.getFastaEntries();
        assertEquals(2, entries.size(), "should parse 2 FASTA entries");

        Set<String> ids = entries.stream().map(e -> e.getSubmissionId()).collect(Collectors.toSet());
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
        assertEquals(2, entry1.get().trailingNsCount, "ID1 trailing Ns");
        assertEquals(0, entry2.get().leadingNsCount, "ID2 leading Ns");
        assertEquals(0, entry2.get().trailingNsCount, "ID2 trailing Ns");

        String sequence1 = service.getSequenceSliceString(
                SequenceRangeOption.WHOLE_SEQUENCE, entry1.get().submissionId, 1, entry1.get().totalBases);
        assertEquals("NNACACGTTTNn", sequence1);
        String sequence2 = service.getSequenceSliceString(
                SequenceRangeOption.WHOLE_SEQUENCE, entry2.get().submissionId, 1, entry2.get().totalBases);
        assertEquals("ACGTGGGG", sequence2);

        String sequence1withoutNbases = service.getSequenceSliceString(
                SequenceRangeOption.WITHOUT_N_BASES,
                entry1.get().submissionId,
                1,
                entry1.get().totalBasesWithoutNBases);
        assertEquals("ACACGTTT", sequence1withoutNbases);

        service.close();
    }

    @Test
    void gettingSequenceViaReaderGivesCorrectResult() throws IOException, FastaFileException {
        File fasta = FastaTestResources.file("fasta", "example2.txt");
        FastaFileService service = new FastaFileService();
        service.openNewFile(fasta);

        List<FastaEntry> entries = service.getFastaEntries();
        assertEquals(2, entries.size(), "should parse 2 FASTA entries");

        Set<String> ids = entries.stream().map(e -> e.getSubmissionId()).collect(Collectors.toSet());
        assertTrue(ids.contains("ID1"));
        assertTrue(ids.contains("ID2"));
        Optional<FastaEntry> entry1 = service.getFastaWithSubmissionId("ID1");
        Optional<FastaEntry> entry2 = service.getFastaWithSubmissionId("ID2");

        // stream whole sequence with the reader
        String streamedSequence;
        try (java.io.Reader r = service.getSequenceSliceReader(
                SequenceRangeOption.WHOLE_SEQUENCE, entry1.get().submissionId, 1, entry1.get().totalBases)) {
            StringBuilder sb = new StringBuilder();
            char[] cbuf = new char[8192];
            int n;
            while ((n = r.read(cbuf)) != -1) {
                sb.append(cbuf, 0, n);
            }
            streamedSequence = sb.toString();
        }
        // compare
        assertEquals("NNACACGTTTNn", streamedSequence);

        // stream whole sequence with the reader
        String streamedSequenceWithoutNbases;
        try (java.io.Reader r = service.getSequenceSliceReader(
                SequenceRangeOption.WITHOUT_N_BASES,
                entry1.get().submissionId,
                1,
                entry1.get().totalBasesWithoutNBases)) {
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
                SequenceRangeOption.WHOLE_SEQUENCE, entry2.get().submissionId, 1, entry2.get().totalBases)) {
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

        service.close();
    }

    @Test
    void gettingStringAsAStringVsStreamProducesSameResultSlices() throws IOException, FastaFileException {
        File fasta = FastaTestResources.file("fasta", "example2.txt");
        FastaFileService service = new FastaFileService();
        service.openNewFile(fasta);

        List<FastaEntry> entries = service.getFastaEntries();
        assertEquals(2, entries.size(), "should parse 2 FASTA entries");

        Set<String> ids = entries.stream().map(e -> e.getSubmissionId()).collect(Collectors.toSet());
        assertTrue(ids.contains("ID1"));
        assertTrue(ids.contains("ID2"));
        Optional<FastaEntry> entry1 = service.getFastaWithSubmissionId("ID1");
        Optional<FastaEntry> entry2 = service.getFastaWithSubmissionId("ID2");

        for (long end = 2; end <= entry1.get().totalBases; end++) {
            // get slice as string
            String sequence = service.getSequenceSliceString(
                    SequenceRangeOption.WHOLE_SEQUENCE, entry1.get().submissionId, 1, end);
            // stream sequence with the reader
            String streamedSequence;
            try (java.io.Reader r = service.getSequenceSliceReader(
                    SequenceRangeOption.WHOLE_SEQUENCE, entry1.get().submissionId, 1, end)) {
                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = r.read(cbuf)) != -1) {
                    sb.append(cbuf, 0, n);
                }
                streamedSequence = sb.toString();
            }
            // compare
            assertEquals(sequence, streamedSequence);
        }

        for (long end = 2; end <= entry2.get().totalBases; end++) {
            // get slice as string
            String sequence2 = service.getSequenceSliceString(
                    SequenceRangeOption.WHOLE_SEQUENCE, entry2.get().submissionId, 1, end);
            // stream sequence with the reader
            String streamedSequence2;
            try (java.io.Reader r = service.getSequenceSliceReader(
                    SequenceRangeOption.WHOLE_SEQUENCE, entry2.get().submissionId, 1, end)) {
                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = r.read(cbuf)) != -1) {
                    sb.append(cbuf, 0, n);
                }
                streamedSequence2 = sb.toString();
            }
            // compare
            assertEquals(sequence2, streamedSequence2);
        }

        service.close();
    }

    // to run this, curl the sequence with: curl -o single_fasta_large_sequence.txt
    // https://www.ebi.ac.uk/ena/cram/md5/11398cc4b68f2cceb4fd50b742d4b1ec
    // then to add the fasta header run something like :
    //
    // tmp="$(mktemp "${TMPDIR:-/tmp}/prepend.XXXXXX")" &&
    // { printf '%s\n' '>ID1 | {"description":"x", "molecule_type":"dna", "topology":"linear"}'; cat --
    // single_fasta_large_sequence.txt; } >"$tmp" &&
    // mv -f -- "$tmp" single_fasta_large_sequence.txt
    //
    // then just move the fasta into whatever/gff3tools/src/test/resources/fasta/
    // and run the test
    // @Test
    void readBigSequenceSuccessfully() throws IOException, FastaFileException {
        File fasta = FastaTestResources.file("fasta", "single_fasta_large_sequence.txt");
        FastaFileService service = new FastaFileService();
        service.openNewFile(fasta);

        List<FastaEntry> entries = service.getFastaEntries();
        assertEquals(1, entries.size(), "should parse 1 FASTA entry");

        Set<String> ids = entries.stream().map(e -> e.getSubmissionId()).collect(Collectors.toSet());
        assertTrue(ids.contains("ID1"));
        Optional<FastaEntry> entry1 = service.getFastaWithSubmissionId("ID1");

        // get first 16 chars
        String sequenceStart =
                service.getSequenceSliceString(SequenceRangeOption.WHOLE_SEQUENCE, entry1.get().submissionId, 1, 16);
        assertEquals(sequenceStart, "GGGCTTTAAATGGCTC");

        // get last 16 chars
        String sequenceEnd = service.getSequenceSliceString(
                SequenceRangeOption.WHOLE_SEQUENCE,
                entry1.get().submissionId,
                entry1.get().totalBases - 15,
                entry1.get().totalBases);
        assertEquals(sequenceEnd, "GAATTCTGATGGCTGT");

        service.close();
    }
}
