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
package uk.ac.ebi.embl.gff3tools;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.api.entry.Entry;
import uk.ac.ebi.embl.api.entry.Text;
import uk.ac.ebi.embl.flatfile.reader.ReaderOptions;
import uk.ac.ebi.embl.flatfile.reader.embl.EmblEntryReader;
import uk.ac.ebi.embl.gff3tools.metadata.EmblEntryMetadataSource;
import uk.ac.ebi.embl.gff3tools.metadata.MasterMetadata;

class Gff3ProviderFactoryTest {

    private static final String LINEAGE =
            "Eukaryota; Metazoa; Arthropoda; Insecta; Coleoptera; Tenebrionidae; Tribolium";

    private Path getResourcePath(String resourceName) {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(resourceName);
        assertNotNull(resource, "Resource not found: " + resourceName);
        return Paths.get(resource.getPath());
    }

    /**
     * A master carrying AC * / ST * / CO / SQ lines must parse those lines in their own blocks
     * (sequencetools MASTER_FORMAT would fold ST * text into the lineage and AC * into the
     * secondary accessions) while still reading the WGS line.
     */
    @Test
    void emblMasterReadsWgsLineWithoutCorruptingOtherFields() throws Exception {
        MasterMetadata meta = ((EmblEntryMetadataSource) Gff3ProviderFactory.parseMasterEntrySource(
                        getResourcePath("metadata/master_with_hidden_lines.embl")))
                .getMetadata();

        assertEquals("WGS", meta.getContigDataclass());
        assertEquals("CAXMYH01", meta.getWgsSet());
        assertEquals(LINEAGE, meta.getLineage());
        assertEquals(List.of("AB000001"), meta.getSecondaryAccessions());
        assertEquals("7070", meta.getTaxon());
    }

    /**
     * sequencetools keeps the valid-tag set in a thread-local shared by every EntryReader on the
     * thread. Parsing a master mid-way through another EMBL read must not change how that read
     * parses its remaining entries.
     */
    @Test
    void parsingMasterDoesNotDisturbAnEmblReadInProgressOnTheSameThread() throws Exception {
        Path master = getResourcePath("metadata/master_with_hidden_lines.embl");
        String entry = Files.readString(master);
        Path twoEntries = Files.createTempFile("two_entries", ".embl");
        Files.writeString(twoEntries, entry + entry.replace("CAXMYH010000000", "CAXMYI010000000"));

        try (BufferedReader reader = Files.newBufferedReader(twoEntries)) {
            ReaderOptions options = new ReaderOptions();
            options.setIgnoreSequence(true);
            EmblEntryReader input = new EmblEntryReader(reader, EmblEntryReader.Format.EMBL_FORMAT, "input", options);
            input.read();

            Gff3ProviderFactory.parseMasterEntrySource(master);

            input.read();
            Entry second = input.getEntry();
            assertEquals("CAXMYI010000000", second.getPrimaryAccession());
            assertEquals(
                    List.of("AB000001"),
                    second.getSecondaryAccessions().stream().map(Text::getText).toList());
            assertEquals(LINEAGE, second.getPrimarySourceFeature().getTaxon().getLineage());
        } finally {
            Files.deleteIfExists(twoEntries);
        }
    }
}
