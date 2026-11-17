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
package uk.ac.ebi.embl.gff3tools.tsvconverter;

import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.ebi.embl.gff3tools.TestUtils.recordingSequenceSource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;
import uk.ac.ebi.embl.gff3tools.validation.meta.RuleSeverity;
import uk.ac.ebi.embl.gff3tools.validation.provider.CompositeSequenceProvider;

class TSVToGff3ConverterReleaseTest {

    @Test
    void releasesEachEntryAfterItIsConverted() throws Exception {
        List<String> events = new ArrayList<>();
        CompositeSequenceProvider sequenceProvider = new CompositeSequenceProvider();
        sequenceProvider.addSource(recordingSequenceSource(events, 1000));

        // The recording source reports one fixed length for every entry, so the length check is
        // switched off; this test is only about release().
        try (ValidationEngine engine = new ValidationEngineBuilder()
                        .overrideMethodRules(Map.of("SEQUENCE_REGION_OUT_OF_BOUNDS", RuleSeverity.OFF))
                        .failFast(false)
                        .withProvider(sequenceProvider)
                        .build();
                BufferedReader input =
                        Files.newBufferedReader(Path.of("src/test/resources/tsvtogff3/its-two-entries.tsv"));
                BufferedWriter output = new BufferedWriter(new StringWriter())) {
            new TSVToGFF3Converter(engine).convert(input, output);
        }

        assertEquals(2, events.size(), "each TSV entry should be released once: " + events);
        assertEquals(2, events.stream().distinct().count(), "entries should be released once each: " + events);
    }
}
