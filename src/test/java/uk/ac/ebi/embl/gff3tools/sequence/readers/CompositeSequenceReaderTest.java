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
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.validation.provider.FileSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.SequenceSource;

class CompositeSequenceReaderTest {

    @Test
    void delegatesToCorrectSource() throws Exception {
        SequenceReader reader1 = mock(SequenceReader.class);
        when(reader1.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader1.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq1"));

        SequenceReader reader2 = mock(SequenceReader.class);
        when(reader2.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader2.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq2"));
        when(reader2.getSequenceSlice(IdType.SUBMISSION_ID, "seq2", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn("ATGAAATAA");

        SequenceSource source1 = new FileSequenceProvider(reader1);
        SequenceSource source2 = new FileSequenceProvider(reader2);

        CompositeSequenceReader composite = new CompositeSequenceReader(List.of(source1, source2));

        String result =
                composite.getSequenceSlice(IdType.SUBMISSION_ID, "seq2", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE);
        assertEquals("ATGAAATAA", result);

        // Verify reader1 was NOT called for getSequenceSlice
        verify(reader1, never()).getSequenceSlice(any(), any(), anyLong(), anyLong(), any());
    }

    @Test
    void throwsWhenNoSourceMatches() {
        SequenceReader reader = mock(SequenceReader.class);
        when(reader.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq1"));

        SequenceSource source = new FileSequenceProvider(reader);
        CompositeSequenceReader composite = new CompositeSequenceReader(List.of(source));

        assertThrows(
                IllegalArgumentException.class,
                () -> composite.getSequenceSlice(
                        IdType.SUBMISSION_ID, "unknown", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE));
    }

    @Test
    void aggregatesOrderedIds() {
        SequenceReader reader1 = mock(SequenceReader.class);
        when(reader1.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader1.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq1", "seq2"));

        SequenceReader reader2 = mock(SequenceReader.class);
        when(reader2.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader2.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq2", "seq3"));

        SequenceSource source1 = new FileSequenceProvider(reader1);
        SequenceSource source2 = new FileSequenceProvider(reader2);

        CompositeSequenceReader composite = new CompositeSequenceReader(List.of(source1, source2));
        List<String> ids = composite.getOrderedIds(IdType.SUBMISSION_ID);

        assertEquals(List.of("seq1", "seq2", "seq3"), ids);
    }

    @Test
    void plainSourceTranslatesId() throws Exception {
        SequenceReader plainReader = mock(SequenceReader.class);
        when(plainReader.submissionType()).thenReturn(SubmissionType.PLAIN_SEQUENCE);
        when(plainReader.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("0"));
        when(plainReader.getSequenceSlice(IdType.SUBMISSION_ID, "0", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE))
                .thenReturn("ATGAAATAA");

        SequenceSource source = new FileSequenceProvider(plainReader);
        CompositeSequenceReader composite = new CompositeSequenceReader(List.of(source));

        // Request with arbitrary ID "chr1" — should be translated to "0" for plain reader
        String result =
                composite.getSequenceSlice(IdType.SUBMISSION_ID, "chr1", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE);
        assertEquals("ATGAAATAA", result);

        // Verify the reader was called with "0", not "chr1"
        verify(plainReader).getSequenceSlice(IdType.SUBMISSION_ID, "0", 1L, 9L, SequenceRangeOption.WHOLE_SEQUENCE);
    }

    @Test
    void setAccessionIdsThrowsUnsupported() {
        CompositeSequenceReader composite = new CompositeSequenceReader(List.of());
        assertThrows(UnsupportedOperationException.class, () -> composite.setAccessionIds(List.of("a")));
    }

    @Test
    void setAccessionIdForSubmissionIdThrowsUnsupported() {
        CompositeSequenceReader composite = new CompositeSequenceReader(List.of());
        assertThrows(UnsupportedOperationException.class, () -> composite.setAccessionIdForSubmissionId("a", "b"));
    }

    @Test
    void closeIsNoOp() throws Exception {
        CompositeSequenceReader composite = new CompositeSequenceReader(List.of());
        composite.close(); // should not throw
    }
}
