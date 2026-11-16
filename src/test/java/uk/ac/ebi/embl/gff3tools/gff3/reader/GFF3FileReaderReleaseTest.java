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

import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.ebi.embl.gff3tools.TestUtils.recordingSequenceSource;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceStats;
import uk.ac.ebi.embl.fastareader.sequenceutils.GapRegion;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;
import uk.ac.ebi.embl.gff3tools.validation.provider.SequenceSource;

class GFF3FileReaderReleaseTest {

    /** A source that fails on release(); used to check the failure propagates to the caller. */
    private static SequenceSource failingReleaseSource(List<String> events) {
        SequenceSource delegate = recordingSequenceSource(events, 1000);
        return new SequenceSource() {
            @Override
            public boolean hasSequence(String seqId) {
                return false;
            }

            @Override
            public String getSequenceSlice(String seqId, long fromBase, long toBase, SequenceRangeOption option)
                    throws Exception {
                return delegate.getSequenceSlice(seqId, fromBase, toBase, option);
            }

            @Override
            public long getSequenceLength(String seqId, SequenceRangeOption option) throws Exception {
                return delegate.getSequenceLength(seqId, option);
            }

            @Override
            public SequenceStats getSequenceStats(String seqId) throws Exception {
                return delegate.getSequenceStats(seqId);
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, SequenceRangeOption option) throws Exception {
                return delegate.getGapRegions(seqId, option);
            }

            @Override
            public List<GapRegion> getGapRegions(String seqId, long fromBase, long toBase, SequenceRangeOption option)
                    throws Exception {
                return delegate.getGapRegions(seqId, fromBase, toBase, option);
            }

            @Override
            public Set<String> knownSeqIds() {
                return Set.of();
            }

            @Override
            public Reader getSequenceSliceReader(String seqId, long fromBase, long toBase, SequenceRangeOption option) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void release(String seqId) {
                throw new IllegalStateException("release failed for " + seqId);
            }
        };
    }

    private List<String> readAndRecord(String content) throws Exception {
        return readAndRecord(content, false);
    }

    private List<String> readAndRecord(String content, boolean withFailingSourceFirst) throws Exception {
        List<String> events = new ArrayList<>();
        CompositeSequenceProvider sequenceProvider = new CompositeSequenceProvider();
        if (withFailingSourceFirst) {
            sequenceProvider.addSource(failingReleaseSource(events));
        }
        sequenceProvider.addSource(recordingSequenceSource(events, 1000));

        try (ValidationEngine engine = new ValidationEngineBuilder()
                        .failFast(false)
                        .withProvider(sequenceProvider)
                        .build();
                GFF3FileReader reader = new GFF3FileReader(engine, new StringReader(content), Path.of("input.gff3"))) {
            reader.readHeader();
            reader.read(annotation -> events.add("handle:" + annotation.getAccession()));
        }
        return events;
    }

    @Test
    void releasesEachAccessionRightAfterItIsHandled() throws Exception {
        String content = "##gff-version 3\n"
                + "SEQ1.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n"
                + "SEQ1.1\t.\tgene\t200\t300\t.\t+\t.\tID=gene2\n"
                + "SEQ2.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene3\n";

        assertEquals(
                List.of("handle:SEQ1.1", "release:SEQ1.1", "handle:SEQ2.1", "release:SEQ2.1"), readAndRecord(content));
    }

    @Test
    void mergedBlocksOfSameAccessionAreReleasedOnce() throws Exception {
        String content = "##gff-version 3\n"
                + "SEQ1.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n"
                + "###\n"
                + "SEQ1.1\t.\tgene\t200\t300\t.\t+\t.\tID=gene2\n"
                + "SEQ2.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene3\n";

        assertEquals(
                List.of("handle:SEQ1.1", "release:SEQ1.1", "handle:SEQ2.1", "release:SEQ2.1"), readAndRecord(content));
    }

    @Test
    void accessionReappearingLaterIsReleasedAgain() throws Exception {
        String content = "##gff-version 3\n"
                + "SEQ1.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n"
                + "SEQ2.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene2\n"
                + "SEQ1.1\t.\tgene\t200\t300\t.\t+\t.\tID=gene3\n";

        assertEquals(
                List.of(
                        "handle:SEQ1.1",
                        "release:SEQ1.1",
                        "handle:SEQ2.1",
                        "release:SEQ2.1",
                        "handle:SEQ1.1",
                        "release:SEQ1.1"),
                readAndRecord(content));
    }

    @Test
    void releaseFailurePropagatesToCaller() {
        String content = "##gff-version 3\n"
                + "SEQ1.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n"
                + "SEQ2.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene2\n";

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> readAndRecord(content, true));
        assertEquals("release failed for SEQ1.1", e.getMessage());
    }
}
