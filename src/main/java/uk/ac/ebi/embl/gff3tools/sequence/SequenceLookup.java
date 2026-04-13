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

/**
 * Minimal interface for looking up nucleotide sequence slices by GFF3 seqId.
 *
 * <p>Backed by the library's {@code SequenceFormatReader}; the string-to-ordinal
 * ID mapping is handled by the implementing provider layer.
 */
public interface SequenceLookup {

    /**
     * Returns a nucleotide subsequence for the given GFF3 sequence ID.
     *
     * @param seqId the GFF3 seqId (e.g. chromosome name)
     * @param fromBase 1-based start position (inclusive)
     * @param toBase 1-based end position (inclusive)
     * @return the nucleotide string
     */
    String getSequenceSlice(String seqId, long fromBase, long toBase) throws Exception;
}
