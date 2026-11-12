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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LocalFileMergeSourceTest {

    private Path tempFile;

    @AfterEach
    void cleanup() throws IOException {
        if (tempFile != null) {
            Files.deleteIfExists(tempFile);
        }
    }

    private Path writeFile(String name, String content) throws IOException {
        tempFile = Files.createTempFile(name, ".gff3");
        Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
        return tempFile;
    }

    @Test
    void objectIdFromSequenceRegionDirective() throws IOException {
        String content =
                "##gff-version 3\n##sequence-region ERZ123456.1 1 10000\nERZ123456.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n";
        Path file = writeFile("somefile", content);

        LocalFileMergeSource source = new LocalFileMergeSource(file);

        assertEquals("ERZ123456.1", source.getObjectId());
    }

    @Test
    void objectIdFallsBackToFilenameWhenNoSequenceRegion() throws IOException {
        String content = "##gff-version 3\nERZ999999.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n";
        // temp file name contains a prefix we can't control, so just check it's not blank
        Path file = writeFile("ERZ999999.1", content);

        LocalFileMergeSource source = new LocalFileMergeSource(file);

        String objectId = source.getObjectId();
        assertTrue(!objectId.isBlank(), "objectId should not be blank when falling back to filename");
        // Fallback strips .gff3; it should not contain the extension
        assertTrue(!objectId.endsWith(".gff3"), "objectId must not retain .gff3 extension");
    }

    @Test
    void objectIdUsesFirstSequenceRegionForSetFile() throws IOException {
        String content = "##gff-version 3\n"
                + "##sequence-region ERZ111111.1 1 5000\n"
                + "##sequence-region ERZ222222.1 1 3000\n"
                + "ERZ111111.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n";
        Path file = writeFile("set_file", content);

        LocalFileMergeSource source = new LocalFileMergeSource(file);

        assertEquals("ERZ111111.1", source.getObjectId());
    }

    @Test
    void fastaOffsetIsMinusOneForAnnotationOnlyFile() throws IOException {
        String content =
                "##gff-version 3\n##sequence-region ERZ123456.1 1 10000\nERZ123456.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n";
        Path file = writeFile("anno_only", content);

        LocalFileMergeSource source = new LocalFileMergeSource(file);

        assertEquals(-1L, source.getFastaOffset());
    }

    @Test
    void fastaOffsetPointsToFastaDirective() throws IOException {
        String annotationPart =
                "##gff-version 3\n##sequence-region ERZ123456.1 1 10000\nERZ123456.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n";
        String fastaPart = "##FASTA\n>ERZ123456.1|cds1\nMACKW\n";
        String content = annotationPart + fastaPart;
        Path file = writeFile("with_fasta", content);

        LocalFileMergeSource source = new LocalFileMergeSource(file);

        long offset = source.getFastaOffset();
        assertEquals(annotationPart.getBytes(StandardCharsets.UTF_8).length, offset);
    }

    @Test
    void fastaOffsetIsCachedOnSecondCall() throws IOException {
        String content =
                "##gff-version 3\n##sequence-region ERZ123456.1 1 10000\nERZ123456.1\t.\tgene\t1\t100\t.\t+\t.\tID=gene1\n##FASTA\n>ERZ123456.1|cds1\nMACKW\n";
        Path file = writeFile("cached_fasta", content);

        LocalFileMergeSource source = new LocalFileMergeSource(file);
        long first = source.getFastaOffset();
        long second = source.getFastaOffset();

        assertEquals(first, second);
    }
}
