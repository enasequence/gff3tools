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
package uk.ac.ebi.embl.gff3tools.sequence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.Reader;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;

public class SequenceReaderFactoryTest {

    @Test
    void readFasta_basicStreamingSequenceExample() throws Exception {
        File fasta = TestUtils.getResourceFile("sequence/fasta/fasta_good_example.txt");
        File seqFile = TestUtils.getResourceFile("sequence/plain/plain_short_good_sequence.txt");

        // basic sanity check for fasta
        try (SequenceReader r = SequenceReaderFactory.readFasta(fasta)) {
            assertEquals(SubmissionType.FASTA, r.submissionType());

            var s1 = r.getStats(IdType.SUBMISSION_ID, "ID1");
            String streamed;
            try (Reader rr = r.getSequenceSliceReader(
                    IdType.SUBMISSION_ID, "ID1", 1, s1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {

                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = rr.read(cbuf)) != -1) sb.append(cbuf, 0, n);
                streamed = sb.toString();
            }
            assertEquals("NNACACGTTTNN", streamed);
        }

        // basic sanity check for sequenceReader
        try (SequenceReader r = SequenceReaderFactory.readPlainSequence(seqFile, "ID1")) {
            assertEquals(SubmissionType.PLAIN_SEQUENCE, r.submissionType());

            var s1 = r.getStats(IdType.SUBMISSION_ID, "ID1");
            String streamed;
            try (Reader rr = r.getSequenceSliceReader(
                    IdType.SUBMISSION_ID, "ID1", 1, s1.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE)) {

                StringBuilder sb = new StringBuilder();
                char[] cbuf = new char[8192];
                int n;
                while ((n = rr.read(cbuf)) != -1) sb.append(cbuf, 0, n);
                streamed = sb.toString();
            }
            assertEquals(
                    "nnnNNNNNNCCCGGCGCGGGCAAGAAGCTGCCGCGTCTGCCCAAGTGTGCCCGCTGCCGCAACCACGGC".toUpperCase(), streamed);
        }
    }
}
