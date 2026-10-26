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
package uk.ac.ebi.embl.gff3tools.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.gff3.directives.GFF3SequenceRegion;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

class ValidationUtilsTest {

    private static final String SEQ_ID = "SEQ1";

    private SequenceLookup lookup;
    private ValidationContext context;

    @BeforeEach
    void setUp() throws Exception {
        lookup = mock(SequenceLookup.class);
        when(lookup.getSequenceLength(SEQ_ID)).thenReturn(2000L);
        context = TestUtils.createTestContext();
        context.register(SequenceLookup.class, new ContextProvider<>() {
            @Override
            public SequenceLookup get(ValidationContext ctx) {
                return lookup;
            }

            @Override
            public Class<SequenceLookup> type() {
                return SequenceLookup.class;
            }
        });
    }

    @Test
    void sequenceRegionComesFirst() throws Exception {
        GFF3SequenceRegion region = new GFF3SequenceRegion(SEQ_ID, Optional.empty(), 1, 1000);

        assertEquals(1000L, ValidationUtils.resolveSequenceLength(region, SEQ_ID, new HashMap<>(), context));
        verify(lookup, never()).getSequenceLength(anyString());
    }

    @Test
    void sequenceReaderWithoutSequenceRegion() {
        assertEquals(2000L, ValidationUtils.resolveSequenceLength(null, SEQ_ID, new HashMap<>(), context));
    }

    @Test
    void nullWithoutSequenceRegionOrReader() {
        assertNull(ValidationUtils.resolveSequenceLength(null, SEQ_ID, new HashMap<>(), TestUtils.createTestContext()));
    }
}
