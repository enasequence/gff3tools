package uk.ac.ebi.embl.gff3tools.sequence;

import uk.ac.ebi.embl.gff3tools.fasta.headerutils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.sequence.wrappers.FastaSubmissionReader;
import uk.ac.ebi.embl.gff3tools.sequence.wrappers.PlainSequenceSubmissionReader;

import java.io.File;

import java.io.File;

/**
 * Factory methods for opening {@link SubmissionSequenceReader} instances.
 *
 * <p>This is the single entry point for reading sequence submissions in a unified way,
 * regardless of whether the submission is a multi-record FASTA file or a single-record
 * plain sequence file.</p>
 *
 * <h2>Supported submission types</h2>
 * <ul>
 *   <li><b>FASTA</b>: a FASTA file containing one or more entries. Each entry is addressable by
 *       its submission ID (parsed from the JSON header). Accession IDs may be supplied later via
 *       {@link SubmissionSequenceReader#setAccessionIds(java.util.List)}.</li>
 *   <li><b>PLAIN_SEQUENCE</b>: a single sequence file containing exactly one sequence record.
 *       The record is addressed by the provided accession ID. A {@link FastaHeader} may be provided
 *       separately (optional).</li>
 * </ul>
 *
 * <h2>Resource management</h2>
 * <p>Readers returned by this factory hold file handles. Always close them when done.</p>
 *
 * <pre>{@code
 * try (SubmissionSequenceReader reader = SubmissionReaders.openFasta(fastaFile)) {
 *     // use reader...
 * }
 * }</pre>
 */
public final class SubmissionReaders {

    private SubmissionReaders() {
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
     *   <li>{@code RecordIdType.SUBMISSION_ID} immediately (submission IDs come from the parsed headers)</li>
     *   <li>{@code RecordIdType.ACCESSION_ID} only after providing a mapping via
     *       {@link SubmissionSequenceReader#setAccessionIds(java.util.List)}</li>
     * </ul>
     *
     * <p>If accession IDs are supplied later, they must be provided in the same order as the records
     * appear in the FASTA file.</p>
     *
     * @param fastaFile FASTA file to open (must not be {@code null})
     * @return a {@link SubmissionSequenceReader} backed by the FASTA file
     * @throws Exception if the file cannot be opened, parsed, or validated (e.g. invalid FASTA,
     *                   invalid headers, duplicate IDs, I/O errors)
     */
    public static SubmissionSequenceReader openFasta(File fastaFile) throws Exception {
        return new FastaSubmissionReader(fastaFile);
    }

    /**
     * Opens a reader for a plain (single-record) sequence submission.
     *
     * <p>The sequence file is expected to contain exactly one sequence record. The record is addressed
     * by the provided {@code accessionId}. A {@link FastaHeader} may be supplied separately; if {@code null},
     * header access via {@link SubmissionSequenceReader#getHeader(RecordIdType, String)} will return empty.</p>
     *
     * @param sequenceFile plain sequence file to open (must not be {@code null})
     * @param accessionId accession ID identifying the single record (must not be {@code null} or blank)
     * @param optionalHeader optional metadata for the record; may be {@code null}
     * @return a {@link SubmissionSequenceReader} backed by the plain sequence file
     * @throws Exception if the file cannot be opened, parsed, or validated (e.g. not UTF-8, wrong format,
     *                   empty file, more than one record, I/O errors)
     */
    public static SubmissionSequenceReader openPlainSequence(
            File sequenceFile,
            String accessionId,
            FastaHeader optionalHeader
    ) throws Exception {
        return new PlainSequenceSubmissionReader(sequenceFile, accessionId, optionalHeader);
    }
}
