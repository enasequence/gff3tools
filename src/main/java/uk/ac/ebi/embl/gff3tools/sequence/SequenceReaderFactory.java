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

import java.io.File;
import java.util.Objects;
import uk.ac.ebi.embl.gff3tools.sequence.readers.JsonHeaderFastaReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.PlainSequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.fasta.header.utils.FastaHeader;

/**
 * Factory methods for opening {@link SequenceReader} instances.
 *
 * <p>This is the single entry point for reading sequence submissions in a unified way,
 * regardless of whether the submission is a multi-record FASTA file or a single-record
 * plain sequence file.</p>
 *
 * <h2>Supported submission types</h2>
 * <ul>
 *   <li><b>FASTA</b>: a FASTA file containing one or more entries. Each entry is addressable by
 *       its submission ID (parsed from the JSON header). Accession IDs may be supplied later via
 *       {@link SequenceReader#setAccessionIds(java.util.List)}.</li>
 *   <li><b>PLAIN_SEQUENCE</b>: a single sequence file containing exactly one sequence record.
 *       The record is addressed by the provided accession ID. A {@link FastaHeader} may be provided
 *       separately (optional).</li>
 * </ul>
 *
 * <h2>Resource management</h2>
 * <p>Readers returned by this factory hold file handles. Always close them when done.</p>
 *
 * <pre>{@code
 * try (SequenceReader reader = SequenceReaderFactory.openFasta(fastaFile)) {
 *     // use reader...
 * }
 * }</pre>
 */
public final class SequenceReaderFactory {

    private SequenceReaderFactory() {
        // Utility class; do not instantiate.
    }

    /**
     * Opens a reader for a FASTA submission.
     *
     * <p>The FASTA file may contain multiple sequence records. Each record is expected to include
     * a JSON header from which the submission ID and {@link FastaHeader} metadata are parsed.</p>
     *
     * <p>Records can be accessed using:</p>
     * <ul>
     *   <li>{@code IdType.SUBMISSION_ID} immediately (submission IDs come from the parsed headers)</li>
     *   <li>{@code IdType.ACCESSION_ID} only after providing a mapping via
     *       {@link SequenceReader#setAccessionIds(java.util.List)}</li>
     * </ul>
     *
     * <p>If accession IDs are supplied later, they must be provided in the same order as the records
     * appear in the FASTA file.</p>
     *
     * @param fastaFile FASTA file to open (must not be {@code null})
     * @return a {@link SequenceReader} backed by the FASTA file
     * @throws Exception if the file cannot be opened, parsed, or validated (e.g. invalid FASTA,
     *                   invalid headers, duplicate IDs, I/O errors)
     */
    public static SequenceReader readFasta(File fastaFile) throws Exception {
        return new JsonHeaderFastaReader(fastaFile);
    }

    /**
     * Opens a reader for a plain (single-record) sequence submission.
     *
     * <p>The sequence file is expected to contain exactly one sequence record. The record is addressed
     * by the provided {@code accessionId}. A {@link FastaHeader} is supplied via this constructor,
     * header access via {@link SequenceReader#getHeader(IdType, String)} will return empty.</p>
     *
     * @param sequenceFile plain sequence file to open (must not be {@code null})
     * @param accessionId accession ID identifying the single record (must not be {@code null} or blank)
     * @param header metadata for the record; if not present use the other constructor as null inputs are not accepted
     * @return a {@link SequenceReader} backed by the plain sequence file
     * @throws Exception if the file cannot be opened, parsed, or validated (e.g. not UTF-8, wrong format,
     *                   empty file, more than one record, I/O errors)
     */
    public static SequenceReader readPlainSequence(File sequenceFile, String accessionId, FastaHeader header)
            throws Exception {
        Objects.requireNonNull(header, "header");
        return new PlainSequenceReader(sequenceFile, accessionId, header);
    }

    /**
     * Opens a reader for a plain (single-record) sequence submission.
     *
     * <p>The sequence file is expected to contain exactly one sequence record. The record is addressed
     * by the provided {@code accessionId}. {@link FastaHeader} is assumed to be non-existent here, so
     * header access via {@link SequenceReader#getHeader(IdType, String)} will return empty.</p>
     *
     * @param sequenceFile plain sequence file to open (must not be {@code null})
     * @param accessionId accession ID identifying the single record (must not be {@code null} or blank)
     * @return a {@link SequenceReader} backed by the plain sequence file
     * @throws Exception if the file cannot be opened, parsed, or validated (e.g. not UTF-8, wrong format,
     *                   empty file, more than one record, I/O errors)
     */
    public static SequenceReader readPlainSequence(File sequenceFile, String accessionId) throws Exception {
        return new PlainSequenceReader(sequenceFile, accessionId, null);
    }
}
