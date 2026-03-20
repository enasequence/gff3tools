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

import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;

/**
 * A source of nucleotide sequences that can be queried by ID.
 *
 * <p>Used by {@link CompositeSequenceProvider} to chain multiple sequence sources
 * (local files, plugins, etc.) in a chain-of-responsibility pattern.
 */
public interface SequenceSource {

    /**
     * Returns {@code true} if this source can provide a sequence for the given ID.
     */
    boolean hasSequence(IdType idType, String id);

    /**
     * Returns the underlying {@link SequenceReader} for this source.
     */
    SequenceReader getReader();

    /** Release any resources held by this source. */
    default void close() {}
}
