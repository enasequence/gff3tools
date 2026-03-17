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

import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.validation.ContextProvider;
import uk.ac.ebi.embl.gff3tools.validation.ValidationContext;

/**
 * Provides a {@link SequenceReader} to the validation/fix pipeline.
 *
 * <p>When auto-discovered with no reader set, {@link #get} returns {@code null}.
 * The CLI sets the reader when {@code --sequence-fasta} is provided.
 */
public class FileSequenceProvider implements ContextProvider<SequenceReader> {

    private SequenceReader sequenceReader;

    public SequenceReader getSequenceReader() {
        return sequenceReader;
    }

    public void setSequenceReader(SequenceReader sequenceReader) {
        this.sequenceReader = sequenceReader;
    }

    @Override
    public SequenceReader get(ValidationContext context) {
        return sequenceReader;
    }

    @Override
    public Class<SequenceReader> type() {
        return SequenceReader.class;
    }
}
