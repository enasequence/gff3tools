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

/**
 * A source of nucleotide sequences that can be queried by GFF3 seqId.
 *
 * <p>Used by {@link CompositeSequenceProvider} to chain multiple sequence sources
 * (local files, plugins, etc.) in a chain-of-responsibility pattern.
 */
public interface SequenceSource {

    /**
     * Returns {@code true} if this source can provide a sequence for the given seqId.
     */
    boolean hasSequence(String seqId);

    /**
     * Returns a nucleotide subsequence for the given GFF3 sequence ID.
     */
    String getSequenceSlice(String seqId, long fromBase, long toBase) throws Exception;

    /** Release any resources held by this source. */
    default void close() {}
}
