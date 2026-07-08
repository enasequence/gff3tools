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
package uk.ac.ebi.embl.gff3tools.gff3toff;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.flatfile.writer.embl.EmblEntryWriter;
import uk.ac.ebi.embl.gff3tools.sequence.SequenceLookup;

/**
 * Writes the EMBL sequence body by streaming from the {@link SequenceLookup} rather than from an
 * in-memory {@code byte[]}, keeping peak memory bounded regardless of sequence length. Emitted output
 * is byte-for-byte identical to the {@code byte[]} path.
 */
final class StreamingEmblEntryWriter extends EmblEntryWriter {

    private final SequenceLookup lookup;
    private final StreamingSequenceContext ctx;

    StreamingEmblEntryWriter(Entry entry, SequenceLookup lookup, StreamingSequenceContext ctx) {
        super(entry);
        this.lookup = lookup;
        this.ctx = ctx;
    }

    @Override
    protected void writeSequence(Writer writer) throws IOException {
        // Only the wrapper is managed here; closing it closes the underlying reader exactly once.
        try (Reader lower = new LowerCaseReader(
                lookup.getSequenceSliceReader(ctx.seqId(), 1L, ctx.totalBases(), SequenceRangeOption.WHOLE_SEQUENCE))) {
            writeStreamingSequence(writer, ctx.totalBases(), ctx.baseCounts(), lower, 0L);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to stream sequence for '" + ctx.seqId() + "': " + e.getMessage(), e);
        }
    }

    @Override
    protected boolean isExpandedEntry() {
        return true;
    }
}
