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

import java.io.Reader;
import java.util.List;
import java.util.Optional;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.readers.fasta.header.utils.FastaHeader;

/**
 * Unified read-only abstraction over submission sequence inputs.
 *
 * <p>Supports two submission types:
 * <ul>
 *   <li>{@link SubmissionType#FASTA}: multi-entry FASTA file where each record has a submitter/submission id
 *       (from the JSON header) and may later be mapped to an accession id via {@link #setAccessionIds(List)}.</li>
 *   <li>{@link SubmissionType#PLAIN_SEQUENCE}: single-sequence file addressed by its accession id; FASTA header
 *       metadata may optionally be provided externally.</li>
 * </ul>
 *
 * <p>ID resolution rules:
 * <ul>
 *   <li>{@link IdType#SUBMISSION_ID} always refers to the "submission id" (FASTA: parsed from header; plain: may be
 *       treated as the accession id depending on implementation).</li>
 *   <li>{@link IdType#ACCESSION_ID} works only after accession mapping has been provided for FASTA.</li>
 * </ul>
 *
 */
public interface SubmissionSequenceReader extends AutoCloseable {

    /**
     * Returns the type of input backing this reader.
     *
     * <p>Use this to decide whether calling {@link #setAccessionIds(List)} is meaningful (FASTA) or merely validated
     * (plain sequence).
     */
    SubmissionType submissionType();

    /**
     * Returns record ids in a stable, deterministic order.
     *
     * <p>This order must match the order in the underlying file and must remain stable for the lifetime of the reader.
     * It exists mainly so callers can provide accession ids aligned to FASTA entries via {@link #setAccessionIds(List)}.
     *
     * <p>For FASTA:
     * <ul>
     *   <li>{@link IdType#SUBMISSION_ID}: returns submission ids in file order. Submission Ids are generally not present for plain sequences.</li>
     *   <li>{@link IdType#ACCESSION_ID}: returns accession ids in file order <b>only after</b> mapping is set;
     *       otherwise implementations may return an empty list or throw (implementation choice; document it).</li>
     * </ul>
     *
     * <p>For plain sequence:
     * <ul>
     *   <li>returns a single-element list containing the accession id for both id types (or throws for SUBMITTERID,
     *       depending on your chosen semantics).</li>
     * </ul>
     */
    List<String> getOrderedIds(IdType idType);

    /**
     * Configures/validates accession ids for this submission.
     *
     * <p>FASTA behavior:
     * <ul>
     *   <li>Maps each accession id to the corresponding FASTA record by file order.</li>
     *   <li>The list size must match the number of FASTA entries; otherwise an exception is thrown.</li>
     *   <li>After this call, {@link IdType#ACCESSION_ID} lookups become valid.</li>
     * </ul>
     *
     * <p>Plain sequence behavior:
     * <ul>
     *   <li>There is only one record; this call exists for API uniformity.</li>
     *   <li>Validates that exactly one accession id is provided and that it matches the configured accession id.</li>
     *   <li>Does not change any mapping because there is nothing to map.</li>
     * </ul>
     *
     * @param orderedAccessionIds accession ids aligned to file order
     * @throws Exception format/problem specific exceptions if mapping/validation fails
     */
    void setAccessionIds(List<String> orderedAccessionIds) throws Exception;

    /**
     * Returns optional FASTA header metadata for the record.
     *
     * <p>FASTA:
     * <ul>
     *   <li>Typically present and parsed from the header line.</li>
     *   <li>Missing id should return {@link Optional#empty()} or throw depending on implementation contract.</li>
     * </ul>
     *
     * <p>Plain sequence:
     * <ul>
     *   <li>May be absent because header metadata is not embedded in the sequence file.</li>
     *   <li>If provided externally, this returns it; otherwise {@link Optional#empty()}.</li>
     * </ul>
     *
     * <p><b>Important:</b> The returned Optional itself should never be null.
     */
    Optional<FastaHeader> getHeader(IdType idType, String id);

    /**
     * Returns normalized sequence statistics for the record (lengths, edge Ns, base counts).
     *
     * <p>This unifies FASTA stats ({@code FastaEntry}) and plain sequence stats ({@code SequenceEntry}) into one type.
     *
     * @throws IllegalArgumentException if the id does not exist or cannot be resolved
     *         (e.g., {@link IdType#ACCESSION_ID} used before {@link #setAccessionIds(List)} in FASTA).
     */
    SequenceStats getStats(IdType idType, String id);

    /**
     * Returns a substring slice of the sequence as a String.
     *
     * <p>{@link SequenceRangeOption} controls whether edge N bases are included or excluded when interpreting the range.
     * @param fromBase start base (starts from 1)
     * @param toBase end base (end base should be smaller than total length of the sequence)
     *
     * @throws Exception format-specific exceptions on I/O, invalid ranges, or id resolution failures
     */
    String getSequenceSlice(IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;

    /**
     * Returns a streaming {@link Reader} over a slice of the sequence.
     *
     * <p>Useful for large slices to avoid allocating large Strings.
     * The returned reader must be closed by the caller.
     *
     * @throws Exception format-specific exceptions on I/O, invalid ranges, or id resolution failures
     */
    Reader getSequenceSliceReader(IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception;
}
