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
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.cli.SequenceFormat;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceReaderFactory;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SequenceReader;
import uk.ac.ebi.embl.gff3tools.sequence.readers.SubmissionType;

/**
 * A {@link SequenceSource} backed by a single file (FASTA or plain sequence).
 *
 * <p>The reader is opened lazily on first access and closed when {@link #close()} is called.
 *
 * <p>For {@link SubmissionType#PLAIN_SEQUENCE}:
 * <ul>
 *   <li>If a {@code sequenceKey} is set, {@link #hasSequence} matches only that key.</li>
 *   <li>If no key is set, {@link #hasSequence} returns {@code true} for any ID
 *       (single sequence serves all GFF3 seqIds).</li>
 * </ul>
 *
 * <p>For {@link SubmissionType#FASTA}, it checks whether the requested ID exists
 * in the reader's index.
 */
@Slf4j
public class FileSequenceProvider implements SequenceSource {

    private final Path path;
    private final SequenceFormat format;
    private final String sequenceKey;
    private SequenceReader sequenceReader;

    /**
     * Creates a provider that will lazily open the sequence file on first access.
     *
     * @param path path to the sequence file
     * @param format the sequence format (fasta or plain)
     * @param sequenceKey optional key for plain sequences (GFF3 seqId); null to match any ID
     */
    public FileSequenceProvider(Path path, SequenceFormat format, String sequenceKey) {
        this.path = path;
        this.format = format;
        this.sequenceKey = sequenceKey;
    }

    /** Convenience constructor for tests that supply a pre-opened reader. */
    public FileSequenceProvider(SequenceReader sequenceReader) {
        this(sequenceReader, null);
    }

    /** Convenience constructor for tests that supply a pre-opened reader with a key. */
    public FileSequenceProvider(SequenceReader sequenceReader, String sequenceKey) {
        this.path = null;
        this.format = null;
        this.sequenceKey = sequenceKey;
        this.sequenceReader = sequenceReader;
    }

    @Override
    public boolean hasSequence(IdType idType, String id) {
        SequenceReader reader = getReader();
        if (reader == null) {
            return false;
        }
        if (reader.submissionType() == SubmissionType.PLAIN_SEQUENCE) {
            return sequenceKey == null || sequenceKey.equals(id);
        }
        return reader.getOrderedIds(idType).contains(id);
    }

    @Override
    public SequenceReader getReader() {
        if (sequenceReader == null && path != null) {
            try {
                sequenceReader = openReader();
            } catch (Exception e) {
                throw new RuntimeException("Failed to open sequence file '%s': %s".formatted(path, e.getMessage()), e);
            }
        }
        return sequenceReader;
    }

    private SequenceReader openReader() throws Exception {
        return switch (format) {
            case fasta -> SequenceReaderFactory.readFasta(path.toFile());
            case plain -> {
                String accessionId = (sequenceKey != null) ? sequenceKey : "0";
                yield SequenceReaderFactory.readPlainSequence(path.toFile(), accessionId);
            }
        };
    }

    @Override
    public void close() {
        if (sequenceReader != null) {
            try {
                sequenceReader.close();
            } catch (Exception e) {
                log.warn("Failed to close sequence reader: {}", e.getMessage());
            }
        }
    }
}
