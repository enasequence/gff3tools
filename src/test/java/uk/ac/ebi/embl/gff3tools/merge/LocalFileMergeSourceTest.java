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
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void objectIdDerivedFromFilename() throws IOException {
        Path file = writeFile("ERZ123456.1", "##gff-version 3\n");

        LocalFileMergeSource source = new LocalFileMergeSource(file);

        String objectId = source.getObjectId();
        assertFalse(objectId.isBlank(), "objectId must not be blank");
        assertFalse(objectId.endsWith(".gff3"), "objectId must not retain .gff3 extension");
    }

    @Test
    void objectIdStripsGzExtensionBeforeGff3() throws IOException {
        // simulate a .gff3.gz file — constructor takes Path, so just name it accordingly
        tempFile = Files.createTempFile("ERZ999999.1", ".gff3.gz");
        Files.write(tempFile, "##gff-version 3\n".getBytes(StandardCharsets.UTF_8));

        LocalFileMergeSource source = new LocalFileMergeSource(tempFile);

        assertFalse(source.getObjectId().endsWith(".gz"), "objectId must not retain .gz extension");
        assertFalse(source.getObjectId().endsWith(".gff3"), "objectId must not retain .gff3 extension");
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
