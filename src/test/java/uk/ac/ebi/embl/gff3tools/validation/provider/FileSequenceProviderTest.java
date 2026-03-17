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
package uk.ac.ebi.embl.gff3tools.validation.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;

class FileSequenceProviderTest {

    @Test
    void hasSequenceReturnsFalseWhenNoReaderSet() {
        FileSequenceProvider provider = new FileSequenceProvider();
        assertFalse(provider.hasSequence(IdType.SUBMISSION_ID, "any"));
    }

    @Test
    void getReaderReturnsNullWhenNoReaderSet() {
        FileSequenceProvider provider = new FileSequenceProvider();
        assertNull(provider.getReader());
    }

    @Test
    void hasSequenceReturnsTrueForPlainSequenceAnyId() {
        SequenceReader mockReader = mock(SequenceReader.class);
        when(mockReader.submissionType()).thenReturn(SubmissionType.PLAIN_SEQUENCE);

        FileSequenceProvider provider = new FileSequenceProvider(mockReader);
        assertTrue(provider.hasSequence(IdType.SUBMISSION_ID, "any-id"));
        assertTrue(provider.hasSequence(IdType.ACCESSION_ID, "other-id"));
    }

    @Test
    void hasSequenceChecksFastaIndex() {
        SequenceReader mockReader = mock(SequenceReader.class);
        when(mockReader.submissionType()).thenReturn(SubmissionType.FASTA);
        when(mockReader.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq1", "seq2"));

        FileSequenceProvider provider = new FileSequenceProvider(mockReader);
        assertTrue(provider.hasSequence(IdType.SUBMISSION_ID, "seq1"));
        assertTrue(provider.hasSequence(IdType.SUBMISSION_ID, "seq2"));
        assertFalse(provider.hasSequence(IdType.SUBMISSION_ID, "seq3"));
    }

    @Test
    void getReaderReturnsSetReader() {
        SequenceReader mockReader = mock(SequenceReader.class);
        FileSequenceProvider provider = new FileSequenceProvider();
        provider.setSequenceReader(mockReader);
        assertSame(mockReader, provider.getReader());
    }

    @Test
    void constructorSetsReader() {
        SequenceReader mockReader = mock(SequenceReader.class);
        FileSequenceProvider provider = new FileSequenceProvider(mockReader);
        assertSame(mockReader, provider.getReader());
    }
}
