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

import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class FileSequenceProviderTest {

    @Test
    void typeReturnsSequenceReaderClass() {
        FileSequenceProvider provider = new FileSequenceProvider();
        assertEquals(SequenceReader.class, provider.type());
    }

    @Test
    void getReturnsNullWhenNoReaderSet() {
        FileSequenceProvider provider = new FileSequenceProvider();
        assertNull(provider.get(new ValidationContext()));
    }

    @Test
    void getReturnsReaderAfterSet() {
        FileSequenceProvider provider = new FileSequenceProvider();
        SequenceReader mockReader = mock(SequenceReader.class);
        provider.setSequenceReader(mockReader);

        assertSame(mockReader, provider.get(new ValidationContext()));
        assertSame(mockReader, provider.getSequenceReader());
    }
}
