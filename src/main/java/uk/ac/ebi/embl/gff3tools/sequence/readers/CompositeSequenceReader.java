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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.gff3tools.sequence.IdType;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceStats;
import uk.ac.ebi.embl.gff3tools.sequence.readers.fasta.header.utils.FastaHeader;
import uk.ac.ebi.embl.gff3tools.validation.provider.SequenceSource;

/**
 * A {@link SequenceReader} that delegates to the first matching {@link SequenceSource}
 * in a chain-of-responsibility pattern.
 *
 * <p>For plain sequence sources (where {@code hasSequence} returns true for any ID),
 * the requested ID is translated to the reader's own accession ID to avoid
 * {@code PlainSequenceReader.validateId()} mismatch.
 */
public class CompositeSequenceReader implements SequenceReader {

    private final List<SequenceSource> sources;

    public CompositeSequenceReader(List<SequenceSource> sources) {
        this.sources = new ArrayList<>(sources);
    }

    @Override
    public SubmissionType submissionType() {
        return SubmissionType.FASTA;
    }

    @Override
    public List<String> getOrderedIds(IdType idType) {
        Set<String> ids = new LinkedHashSet<>();
        for (SequenceSource source : sources) {
            ids.addAll(source.getReader().getOrderedIds(idType));
        }
        return new ArrayList<>(ids);
    }

    @Override
    public void setAccessionIds(List<String> orderedAccessionIds) {
        throw new UnsupportedOperationException("CompositeSequenceReader does not support setAccessionIds");
    }

    @Override
    public void setAccessionIdForSubmissionId(String accessionId, String submissionId) {
        throw new UnsupportedOperationException(
                "CompositeSequenceReader does not support setAccessionIdForSubmissionId");
    }

    @Override
    public Optional<FastaHeader> getHeader(IdType idType, String id) {
        SourceMatch match = findSource(idType, id);
        return match.reader().getHeader(idType, match.resolvedId());
    }

    @Override
    public SequenceStats getStats(IdType idType, String id) {
        SourceMatch match = findSource(idType, id);
        return match.reader().getStats(idType, match.resolvedId());
    }

    @Override
    public String getSequenceSlice(IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option)
            throws Exception {
        SourceMatch match = findSource(idType, id);
        return match.reader().getSequenceSlice(idType, match.resolvedId(), fromBase, toBase, option);
    }

    @Override
    public Reader getSequenceSliceReader(
            IdType idType, String id, long fromBase, long toBase, SequenceRangeOption option) throws Exception {
        SourceMatch match = findSource(idType, id);
        return match.reader().getSequenceSliceReader(idType, match.resolvedId(), fromBase, toBase, option);
    }

    @Override
    public void close() {
        // Sources manage their own lifecycle
    }

    private SourceMatch findSource(IdType idType, String id) {
        for (SequenceSource source : sources) {
            if (source.hasSequence(idType, id)) {
                SequenceReader reader = source.getReader();
                // For plain sources, translate the requested ID to the reader's own accession ID
                if (reader.submissionType() == SubmissionType.PLAIN_SEQUENCE) {
                    String resolvedId = reader.getOrderedIds(idType).get(0);
                    return new SourceMatch(reader, resolvedId);
                }
                return new SourceMatch(reader, id);
            }
        }
        throw new IllegalArgumentException("No sequence source found for " + idType + ":" + id);
    }

    private record SourceMatch(SequenceReader reader, String resolvedId) {}
}
