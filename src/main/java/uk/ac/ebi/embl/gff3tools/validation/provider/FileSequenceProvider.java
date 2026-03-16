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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import uk.ac.ebi.embl.fastareader.SequenceRangeOption;
import uk.ac.ebi.embl.fastareader.SequenceReader;
import uk.ac.ebi.embl.fastareader.exception.SequenceFileException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;

public class FileSequenceProvider implements SequenceProvider {

    private final Path processDir;

    public FileSequenceProvider(Path processDir) {
        this.processDir = processDir;
    }

    @Override
    public byte[] getSequenceBytes(String accessionId, List<GFF3Feature> features) throws IOException {
        Path sequencePath = processDir.resolve(accessionId + ".seq");
        if (!Files.exists(sequencePath)) {
            throw new IOException("Sequence file not found: " + sequencePath);
        }
        try (SequenceReader reader = new SequenceReader(sequencePath.toFile())) {

            int totalLength =
                    (int) features.stream().mapToLong(GFF3Feature::getLength).sum();
            ByteBuffer segmentBuffer = ByteBuffer.wrap(new byte[totalLength]);

            for (GFF3Feature feature : features) {
                segmentBuffer.put(reader.getSequenceSliceString(
                                feature.getStart(), feature.getEnd(), SequenceRangeOption.WHOLE_SEQUENCE)
                        .getBytes());
            }

            return segmentBuffer.array();
        } catch (IOException e) {
            throw new IOException("Error reading sequence for accession " + accessionId, e);
        } catch (SequenceFileException e) {
            throw new RuntimeException(e);
        }
    }
}
