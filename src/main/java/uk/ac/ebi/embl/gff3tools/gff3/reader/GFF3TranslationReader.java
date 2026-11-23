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
package uk.ac.ebi.embl.gff3tools.gff3.reader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.FastaReader;
import uk.ac.ebi.embl.fastareader.exception.FastaFileException;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceAlphabet;
import uk.ac.ebi.embl.fastareader.sequenceutils.SequenceIndex;
import uk.ac.ebi.embl.gff3tools.exception.NonExistingFile;
import uk.ac.ebi.embl.gff3tools.exception.ReadException;
import uk.ac.ebi.embl.gff3tools.gff3.TranslationKey;
import uk.ac.ebi.embl.gff3tools.utils.GzipUtils;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;

/**
 * Reads protein translation sequences stored in the FASTA section at the end of
 * a GFF3 file.
 *
 * <p>The FASTA boundary is located with {@link FastaSectionLocator} (an
 * O(log fileSize) binary search over raw byte offsets). A single
 * {@link FastaReader} is then opened at that offset, indexes the embedded FASTA
 * section in one forward pass, and serves each translation via buffered
 * positional slice reads. The reader is cached so extraction can reuse it, and
 * must therefore be closed once all translations have been read.
 */
@Slf4j
public class GFF3TranslationReader implements AutoCloseable {

    // Retained for constructor-signature stability; sequence-character validation now
    // happens eagerly in FastaReader, not here.
    ValidationEngine validationEngine;
    Path gff3Path;
    private FastaReader fastaReader;

    // Built once per reader: readTranslationOffset opens a new FastaReader on every call.
    private Map<String, Long> offsets;
    private Map<String, Map<String, Long>> offsetsByAccession;
    // A failed read is not retried for every CDS that asks; the same error is rethrown instead.
    private ReadException offsetsFailure;

    public GFF3TranslationReader(ValidationEngine validationEngine, Path gff3Path) {
        this.validationEngine = validationEngine;
        this.gff3Path = gff3Path;
    }

    /**
     * The {@code ##FASTA} header → entry id map, read on first use and reused for the reader's life.
     */
    public Map<String, Long> getTranslationOffsets() throws ReadException {
        if (offsetsFailure != null) {
            throw offsetsFailure;
        }
        if (offsets == null) {
            try {
                offsets = Collections.unmodifiableMap(readTranslationOffset());
            } catch (ReadException e) {
                offsetsFailure = e;
                throw e;
            }
        }
        return offsets;
    }

    /**
     * The translations belonging to one accession, keyed as {@code accession|featureId}, in the
     * offset map's order.
     *
     * <p>Grouped by the accession inside each header, never by position in the file: nothing
     * guarantees the {@code ##FASTA} entries of one accession are contiguous. Accessions are matched
     * exactly rather than by prefix, so {@code AB123.1} never claims the translations of
     * {@code AB123.10}. Buckets are {@link LinkedHashMap}s filled in the offset map's own sorted
     * order, so written output stays byte-stable across runs.
     */
    public Map<String, Long> getTranslationOffsetsForAccession(String accession) throws ReadException {
        if (offsetsByAccession == null) {
            Map<String, Map<String, Long>> byAccession = new HashMap<>();
            for (Map.Entry<String, Long> entry : getTranslationOffsets().entrySet()) {
                String entryAccession = TranslationKey.accessionOf(entry.getKey());
                if (entryAccession != null) {
                    byAccession
                            .computeIfAbsent(entryAccession, key -> new LinkedHashMap<>())
                            .put(entry.getKey(), entry.getValue());
                }
            }
            offsetsByAccession = byAccession;
        }
        return offsetsByAccession.getOrDefault(accession, Map.of());
    }

    /**
     * The submitted {@code ##FASTA} protein for one feature, or empty when the file has none for it.
     * The header must match {@link TranslationKey#of(String, String)} exactly.
     */
    public Optional<String> getTranslation(String accession, String featureId) throws ReadException {
        if (accession == null || featureId == null) {
            return Optional.empty();
        }
        Long id = getTranslationOffsetsForAccession(accession).get(TranslationKey.of(accession, featureId));
        return id == null ? Optional.empty() : Optional.of(readTranslation(id));
    }

    /**
     * Locates the FASTA section and builds a map from translation id (the FASTA
     * header text with the single leading {@code >} stripped) to the
     * {@link FastaReader} sequential entry id. Returns an empty map when the file
     * has no FASTA section, opening no {@link FastaReader} in that case.
     *
     * <p>A {@link TreeMap} preserves the alphabetical external ordering of ids.
     */
    public Map<String, Long> readTranslationOffset() throws ReadException {
        Map<String, Long> offsetMap = new TreeMap<>();

        // gff3Path is not always a real, seekable file (e.g. stdin input, represented as an
        // empty or "-" path): there is no FASTA/translation section to read back in that case.
        if (!Files.isRegularFile(gff3Path)) {
            log.info("Translation sequence not found");
            return offsetMap;
        }

        // The FASTA section is located by seeking in the file's raw bytes, which for gzip or bgzip
        // input are compressed: offsets found there do not point into the decompressed text.
        // Callers that need translations from compressed input decompress it to a file first.
        if (isGzipped()) {
            log.warn("The input file is compressed: any translations in its ##FASTA section cannot be read "
                    + "and are omitted from the output. Decompress the file first to keep them.");
            return offsetMap;
        }

        try {
            long boundary = FastaSectionLocator.locate(gff3Path);
            if (boundary < 0) {
                return offsetMap;
            }

            fastaReader = new FastaReader(gff3Path.toFile(), SequenceAlphabet.defaultProteinAlphabet(), boundary);
        } catch (FastaFileException | IOException | UncheckedIOException e) {
            throw new ReadException(
                    "Error reading translations from " + gff3Path + ": " + rootCauseMessage(e),
                    ReadException.wrapAsIOException(e));
        }

        for (long id : fastaReader.getOrderedIds()) {
            String header = fastaReader.getHeaderline(id).orElse("");
            String translationId = header.startsWith(">") ? header.substring(1) : header;
            // Overwriting would silently pick one of the copies; which one is never the submitter's choice.
            if (offsetMap.putIfAbsent(translationId, id) != null) {
                throw new ReadException("Error reading translations from " + gff3Path + ": the FASTA header \">"
                        + translationId + "\" appears more than once");
            }
        }

        return offsetMap;
    }

    /**
     * Reads the translation sequence for the given {@link FastaReader} entry id,
     * with newlines stripped and the result uppercased. A zero-base entry yields
     * an empty string.
     *
     * <p>Must be called only after {@link #readTranslationOffset()} returns a non-empty map;
     * {@code fastaReader} is not initialised when no {@code ##FASTA} section was found.
     */
    public String readTranslation(Long id) throws ReadException {
        if (fastaReader == null) {
            throw new ReadException(
                    "Cannot read translation from " + gff3Path + ": no FASTA section was found",
                    new IOException("fastaReader is not initialised"));
        }
        SequenceIndex idx = fastaReader.getSequenceIndex(id);
        long n = idx.totalBases();
        if (n == 0) {
            return "";
        }
        try {
            return fastaReader.getSequenceSlice(id, 1, n).toUpperCase();
        } catch (Exception e) {
            throw new ReadException(
                    "Error reading translation from " + gff3Path + ": " + rootCauseMessage(e),
                    ReadException.wrapAsIOException(e));
        }
    }

    private boolean isGzipped() throws ReadException {
        try {
            return GzipUtils.isGzipped(gff3Path);
        } catch (NonExistingFile e) {
            throw new ReadException(
                    "Error reading translations from " + gff3Path + ": " + e.getMessage(),
                    ReadException.wrapAsIOException(e));
        }
    }

    /**
     * The message of the innermost cause. fastareader wraps its errors, and a wrapper's own
     * message is its cause's {@code toString()}, which puts a Java class name in front of the
     * text a submitter needs. The full chain stays available as the exception cause.
     */
    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : e.getMessage();
    }

    @Override
    public void close() throws IOException {
        if (fastaReader != null) {
            fastaReader.close();
        }
    }
}
