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

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.fastareader.SequenceFileFormat;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReader;
import uk.ac.ebi.embl.fastareader.api.SequenceFormatReaderFactory;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.FastaHeaderSource;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.JsonHeaderParser;
import uk.ac.ebi.embl.gff3tools.sequence.fasta.header.utils.ParsedHeader;

/**
 * A {@link SequenceSource} backed by a single file (FASTA or plain sequence).
 *
 * <p>The reader is opened lazily on first access and closed when {@link #close()} is called.
 *
 * <p>For plain sequences:
 * <ul>
 *   <li>If a {@code sequenceKey} is set, {@link #hasSequence} matches only that key.</li>
 *   <li>If no key is set, {@link #hasSequence} returns {@code true} for any ID
 *       (single sequence serves all GFF3 seqIds).</li>
 * </ul>
 *
 * <p>For FASTA, it parses headers to extract submission IDs and maps them
 * to the library's ordinal IDs for sequence retrieval.
 *
 * <p>By default, FASTA headers are expected in the {@code >ID | JSON} format and are
 * parsed by {@link JsonHeaderParser}. An optional {@link FastaHeaderSource} may be
 * supplied instead; in that case the seqId is the plain first whitespace-delimited
 * token after {@code >} and header metadata is resolved through the provided source.
 */
@Slf4j
public class FileSequenceSource implements SequenceSource {

    @Getter
    private final Path path;

    @Getter
    private final SequenceFormat format;

    @Getter
    private final String sequenceKey;

    @Getter
    private SequenceFormatReader formatReader;

    @Getter
    private final Map<String, Long> seqIdToOrdinal = new HashMap<>();

    private final Map<String, FastaHeader> seqIdToHeader = new HashMap<>();
    private final FastaHeaderSource fastaHeaderSource;
    private boolean initialized;

    /**
     * Creates a provider that will lazily open the sequence file on first access.
     *
     * @param path path to the sequence file
     * @param format the sequence format (fasta or plain)
     * @param sequenceKey optional key for plain sequences (GFF3 seqId); null to match any ID
     */
    public FileSequenceSource(Path path, SequenceFormat format, String sequenceKey) {
        this(path, format, sequenceKey, null);
    }

    /**
     * Creates a provider that will lazily open the sequence file on first access.
     *
     * <p>When {@code fastaHeaderSource} is non-null, FASTA headers are parsed using the
     * plain first-token convention ({@code >seqId ...}) and header metadata is resolved
     * through the supplied source instead of {@link JsonHeaderParser}.
     *
     * @param path path to the sequence file
     * @param format the sequence format (fasta or plain)
     * @param sequenceKey optional key for plain sequences (GFF3 seqId); null to match any ID
     * @param fastaHeaderSource optional source for FASTA header metadata; null uses JSON parser
     */
    public FileSequenceSource(
            Path path, SequenceFormat format, String sequenceKey, FastaHeaderSource fastaHeaderSource) {
        this.path = path;
        this.format = format;
        this.sequenceKey = sequenceKey;
        this.fastaHeaderSource = fastaHeaderSource;
    }

    /** Convenience constructor for tests that supply a pre-opened reader. */
    public FileSequenceSource(SequenceFormatReader formatReader, SequenceFormat format, String sequenceKey) {
        this(formatReader, format, sequenceKey, null);
    }

    /**
     * Convenience constructor for tests that supply a pre-opened reader.
     *
     * <p>When {@code fastaHeaderSource} is non-null, FASTA headers are parsed using the
     * plain first-token convention and header metadata is resolved through the supplied source.
     */
    FileSequenceSource(
            SequenceFormatReader formatReader,
            SequenceFormat format,
            String sequenceKey,
            FastaHeaderSource fastaHeaderSource) {
        this.path = null;
        this.format = format;
        this.sequenceKey = sequenceKey;
        this.formatReader = formatReader;
        this.fastaHeaderSource = fastaHeaderSource;
    }

    @Override
    public boolean hasSequence(String seqId) {
        ensureInitialized();
        if (formatReader == null) {
            return false;
        }
        if (formatReader.getSequenceFileFormat() == SequenceFileFormat.PLAIN_SEQUENCE) {
            return sequenceKey == null || sequenceKey.equals(seqId);
        }
        return seqIdToOrdinal.containsKey(seqId);
    }

    @Override
    public String getSequenceSlice(String seqId, long fromBase, long toBase) throws Exception {
        ensureInitialized();
        long ordinal = resolveOrdinal(seqId);
        return formatReader.getSequenceSlice(ordinal, fromBase, toBase);
    }

    @Override
    public void close() {
        if (formatReader != null) {
            try {
                formatReader.close();
            } catch (Exception e) {
                log.warn("Failed to close sequence reader: {}", e.getMessage());
            }
        }
    }

    /**
     * Returns an unmodifiable view of the parsed FASTA headers keyed by submission ID.
     * Only populated for FASTA-format sources after initialization.
     */
    public Map<String, FastaHeader> getSeqIdToHeader() {
        ensureInitialized();
        return Collections.unmodifiableMap(seqIdToHeader);
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (formatReader == null && path != null) {
            try {
                formatReader = openReader();
            } catch (Exception e) {
                throw new RuntimeException("Failed to open sequence file '%s': %s".formatted(path, e.getMessage()), e);
            }
        }
        if (formatReader != null) {
            buildIdMapping();
        }
    }

    private SequenceFormatReader openReader() throws Exception {
        return switch (format) {
            case fasta -> SequenceFormatReaderFactory.readFasta(path.toFile());
            case plain -> SequenceFormatReaderFactory.readPlainSequence(path.toFile());
        };
    }

    private void buildIdMapping() {
        if (formatReader.getSequenceFileFormat() == SequenceFileFormat.FASTA) {
            if (fastaHeaderSource != null) {
                buildIdMappingFromSource();
            } else {
                buildIdMappingFromJsonParser();
            }
        }
        // For plain sequences, no ID mapping needed — resolveOrdinal uses the single ordinal directly
    }

    /** Parses FASTA headers in {@code >ID | JSON} format using {@link JsonHeaderParser}. */
    private void buildIdMappingFromJsonParser() {
        JsonHeaderParser headerParser = new JsonHeaderParser();
        for (long ordinal : formatReader.getOrderedIds()) {
            String headerLine = formatReader
                    .getHeaderline(ordinal)
                    .orElseThrow(() -> new RuntimeException("No header found for ordinal " + ordinal));
            try {
                ParsedHeader parsed = headerParser.parse(headerLine);
                String submissionId = parsed.getId();
                if (seqIdToOrdinal.containsKey(submissionId)) {
                    throw new RuntimeException("Duplicate submission ID in FASTA: " + submissionId);
                }
                seqIdToOrdinal.put(submissionId, ordinal);
                seqIdToHeader.put(submissionId, parsed.getHeader());
            } catch (Exception e) {
                throw new RuntimeException(
                        ("Failed to parse FASTA header at ordinal %d: %s. "
                                        + "Expected format: >ID | {\"key\":\"value\",...}")
                                .formatted(ordinal, e.getMessage()),
                        e);
            }
        }
    }

    /**
     * Resolves FASTA headers via the caller-supplied {@link FastaHeaderSource}.
     *
     * <p>The seqId is the plain first whitespace-delimited token after {@code >}.
     * Sequences for which the source returns {@link java.util.Optional#empty()} are
     * still registered in the ordinal map — they simply have no header entry.
     */
    private void buildIdMappingFromSource() {
        for (long ordinal : formatReader.getOrderedIds()) {
            String headerLine = formatReader
                    .getHeaderline(ordinal)
                    .orElseThrow(() -> new RuntimeException("No header found for ordinal " + ordinal));
            try {
                String seqId = extractFirstToken(headerLine);
                if (seqId.isEmpty()) {
                    throw new RuntimeException("FASTA header has no id token. Header: " + headerLine);
                }
                if (seqIdToOrdinal.containsKey(seqId)) {
                    throw new RuntimeException("Duplicate submission ID in FASTA: " + seqId);
                }
                seqIdToOrdinal.put(seqId, ordinal);
                fastaHeaderSource.getHeader(seqId).ifPresent(h -> seqIdToHeader.put(seqId, h));
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to process FASTA header at ordinal %d: %s. Header: %s"
                                .formatted(ordinal, e.getMessage(), headerLine),
                        e);
            }
        }
    }

    /**
     * Extracts the first whitespace-delimited token from a FASTA header line,
     * stripping the leading {@code >}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code >chr1} → {@code chr1}</li>
     *   <li>{@code >chr1 some description} → {@code chr1}</li>
     *   <li>{@code >chr1\tmore info} → {@code chr1}</li>
     * </ul>
     */
    static String extractFirstToken(String headerLine) {
        String rest = headerLine.startsWith(">") ? headerLine.substring(1) : headerLine;
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            if (Character.isWhitespace(rest.charAt(i))) {
                end = i;
                break;
            }
        }
        return rest.substring(0, end);
    }

    private long resolveOrdinal(String seqId) {
        if (formatReader.getSequenceFileFormat() == SequenceFileFormat.PLAIN_SEQUENCE) {
            return formatReader.getOrderedIds().get(0);
        }
        Long ordinal = seqIdToOrdinal.get(seqId);
        if (ordinal == null) {
            throw new IllegalArgumentException("No sequence found for seqId: " + seqId);
        }
        return ordinal;
    }
}
