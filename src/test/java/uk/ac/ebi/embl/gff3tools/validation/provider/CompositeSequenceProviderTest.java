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
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class CompositeSequenceProviderTest {

    @Test
    void getReturnsNullWhenNoSources() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        assertNull(provider.get(new ValidationContext()));
        assertFalse(provider.hasSources());
    }

    @Test
    void getReturnsReaderWhenSourceAdded() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        SequenceReader mockReader = mock(SequenceReader.class);
        when(mockReader.submissionType()).thenReturn(SubmissionType.FASTA);
        when(mockReader.getOrderedIds(any())).thenReturn(List.of("seq1"));

        FileSequenceSource source = new FileSequenceSource(mockReader);
        provider.addSource(source);

        assertTrue(provider.hasSources());
        assertNotNull(provider.get(new ValidationContext()));
    }

    @Test
    void typeReturnsSequenceReaderClass() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        assertEquals(SequenceReader.class, provider.type());
    }

    @Test
    void singleSourceDelegatesCorrectly() throws Exception {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        SequenceReader mockReader = mock(SequenceReader.class);
        when(mockReader.submissionType()).thenReturn(SubmissionType.FASTA);
        when(mockReader.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq1"));

        FileSequenceSource source = new FileSequenceSource(mockReader);
        provider.addSource(source);

        SequenceReader composite = provider.get(new ValidationContext());
        assertNotNull(composite);
        assertEquals(List.of("seq1"), composite.getOrderedIds(IdType.SUBMISSION_ID));
    }

    @Test
    void chainFirstSourceMissesSecondSourceHits() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();

        // First source: FASTA with only "seq1"
        SequenceReader reader1 = mock(SequenceReader.class);
        when(reader1.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader1.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq1"));
        FileSequenceSource source1 = new FileSequenceSource(reader1);

        // Second source: FASTA with "seq2"
        SequenceReader reader2 = mock(SequenceReader.class);
        when(reader2.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader2.getOrderedIds(IdType.SUBMISSION_ID)).thenReturn(List.of("seq2"));
        FileSequenceSource source2 = new FileSequenceSource(reader2);

        provider.addSource(source1);
        provider.addSource(source2);

        SequenceReader composite = provider.get(new ValidationContext());
        // Aggregates IDs from both sources
        List<String> ids = composite.getOrderedIds(IdType.SUBMISSION_ID);
        assertTrue(ids.contains("seq1"));
        assertTrue(ids.contains("seq2"));
        assertEquals(2, ids.size());
    }

    @Test
    void closeClosesSources() throws Exception {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        SequenceReader reader1 = mock(SequenceReader.class);
        when(reader1.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader1.getOrderedIds(any())).thenReturn(List.of("seq1"));
        SequenceReader reader2 = mock(SequenceReader.class);
        when(reader2.submissionType()).thenReturn(SubmissionType.FASTA);
        when(reader2.getOrderedIds(any())).thenReturn(List.of("seq2"));

        provider.addSource(new FileSequenceSource(reader1));
        provider.addSource(new FileSequenceSource(reader2));

        provider.close();

        verify(reader1).close();
        verify(reader2).close();
    }

    @Test
    void getCachesSameInstance() {
        CompositeSequenceProvider provider = new CompositeSequenceProvider();
        SequenceReader mockReader = mock(SequenceReader.class);
        when(mockReader.submissionType()).thenReturn(SubmissionType.FASTA);
        when(mockReader.getOrderedIds(any())).thenReturn(List.of("seq1"));
        provider.addSource(new FileSequenceSource(mockReader));

        ValidationContext ctx = new ValidationContext();
        SequenceReader first = provider.get(ctx);
        SequenceReader second = provider.get(ctx);
        assertSame(first, second);
    }
}
