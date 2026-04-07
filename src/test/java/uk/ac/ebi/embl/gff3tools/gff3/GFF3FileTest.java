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
package uk.ac.ebi.embl.gff3tools.gff3;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.validation.provider.TranslationState;

public class GFF3FileTest {

    @Test
    void testWriteTranslation() throws Exception {

        String input = "##FASTA\n" + ">geneB\n" + "GGTTAA\n" + ">geneA\n" + "ATGC\n";
        String expectedOutput = "##FASTA\n" + input;

        // Inject cdsTranslationMap
        Map<String, String> testMap = new HashMap<>();
        testMap.put("geneA", "ATGC");
        testMap.put("geneB", "GGTTAA");

        Files.writeString(Path.of("translation.fasta"), input, Charset.defaultCharset());
        GFF3File obj =
                GFF3File.builder().fastaFilePath(Path.of("translation.fasta")).build();

        // obj.cdsTranslationMap = testMap;

        StringWriter writer = new StringWriter();

        // Access private method via reflection
        Method method = GFF3File.class.getDeclaredMethod("writeFastaFromExistingFile", Writer.class);
        method.setAccessible(true);

        // call method
        method.invoke(obj, writer);

        // Assert
        String output = writer.toString();
        assertEquals(expectedOutput, output);
        Files.deleteIfExists(Path.of("translation.fasta"));
    }

    @Test
    void writeGFF3String_writesFastaFromTranslationState() throws Exception {
        TranslationState state = new TranslationState();
        state.record("acc1|cds-1", "OLD", "MKTRANS");

        GFF3File file = GFF3File.builder()
                .annotations(List.of())
                .translationState(state)
                .build();

        StringWriter writer = new StringWriter();
        file.writeGFF3String(writer);

        String output = writer.toString();
        assertTrue(output.contains("##FASTA"));
        assertTrue(output.contains(">acc1|cds-1"));
        assertTrue(output.contains("MKTRANS"));
    }

    @Test
    void writeGFF3String_noFastaWhenTranslationStateIsNull() throws Exception {
        GFF3File file = GFF3File.builder().annotations(List.of()).build();

        StringWriter writer = new StringWriter();
        file.writeGFF3String(writer);

        assertFalse(writer.toString().contains("##FASTA"));
    }

    @Test
    void writeGFF3String_skipsEntryWithNullNewTranslation() throws Exception {
        TranslationState state = new TranslationState();
        state.record("acc1|cds-1", "OLD", null);

        GFF3File file = GFF3File.builder()
                .annotations(List.of())
                .translationState(state)
                .build();

        StringWriter writer = new StringWriter();
        file.writeGFF3String(writer);

        assertFalse(writer.toString().contains("##FASTA"));
    }
}
