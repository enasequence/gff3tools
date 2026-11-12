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
package uk.ac.ebi.embl.gff3tools.merge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import uk.ac.ebi.embl.gff3tools.gff3.reader.FastaSectionLocator;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;

/**
 * A {@link Gff3MergeSource} backed by a local GFF3 file. Uses {@link FastaSectionLocator} to
 * locate the {@code ##FASTA} boundary via binary search. Result is cached so subsequent calls are
 * free.
 */
@Slf4j
public class LocalFileMergeSource implements Gff3MergeSource {

    private final String objectId;
    private final Path gff3Path;
    private long cachedFastaOffset = Long.MIN_VALUE;

    public LocalFileMergeSource(Path gff3Path) throws IOException {
        this.gff3Path = gff3Path;
        List<String> ids = GFF3FileReader.readSequenceRegionIds(gff3Path);
        this.objectId = ids.isEmpty() ? objectIdFromFilename(gff3Path) : ids.get(0);
    }

    private static String objectIdFromFilename(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".gz")) name = name.substring(0, name.length() - 3);
        if (name.endsWith(".gff3")) name = name.substring(0, name.length() - 5);
        return name;
    }

    @Override
    public String getObjectId() {
        return objectId;
    }

    @Override
    public InputStream openStream() throws IOException {
        return Files.newInputStream(gff3Path);
    }

    @Override
    public long getFastaOffset() throws IOException {
        if (cachedFastaOffset != Long.MIN_VALUE) {
            return cachedFastaOffset;
        }
        cachedFastaOffset = FastaSectionLocator.locate(gff3Path);
        log.debug("##FASTA offset for {}: {}", objectId, cachedFastaOffset);
        return cachedFastaOffset;
    }
}
