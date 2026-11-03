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
package uk.ac.ebi.embl.gff3tools.validation.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.ebi.embl.gff3tools.exception.AggregatedValidationException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

/**
 * Runs GFF3 whose children are listed before their parents through the full validation engine.
 * GFF3 allows a {@code Parent} to point forward to a feature later in the same {@code ###} block,
 * but {@code PARENT_BEFORE_CHILD} does not, so a gene model written leaf first is reported.
 */
class ChildBeforeParentTest {

    private static final String ACCESSION = "SEQ1";

    @TempDir
    Path tempDir;

    @Test
    void geneModelListedLeafFirstIsRejected() throws Exception {
        String gff3 = gff3(
                feature("CDS", 51, 950, "0", "ID=cds1;Parent=mrna1;"),
                feature("mRNA", 1, 1000, ".", "ID=mrna1;Parent=gene1;transcript_id=tx_A;"),
                feature("gene", 1, 1000, ".", "ID=gene1;gene=geneA;"));

        ValidationEngine engine = assertDoesNotThrow(() -> run(gff3));

        List<ValidationException> errors = engine.getCollectedErrors();
        assertEquals(1, errors.size(), "Errors: " + errors);
        assertEquals(ParentBeforeChildValidation.VALIDATION_RULE, errors.get(0).getValidationRule());
        assertTrue(errors.get(0).getMessage().contains("before its Parent \"mrna1\""), "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().contains("before its Parent \"gene1\""), "Errors: " + errors);
        assertThrows(AggregatedValidationException.class, engine::throwIfErrorsCollected);
    }

    /** Reads {@code gff3} through a full (classpath-scanned) engine, collecting every error. */
    private ValidationEngine run(String gff3) throws Exception {
        Path file = tempDir.resolve("input.gff3");
        Files.writeString(file, gff3);

        ValidationEngine engine = new ValidationEngineBuilder()
                // Mirrors GFF3 conversion: gap generation only belongs to FASTA -> GFF3.
                .overrideMethodFixs(Map.of("GAP_GENERATION", false))
                .failFast(false)
                .build();
        try (GFF3FileReader reader = new GFF3FileReader(engine, file)) {
            reader.readHeader();
            reader.read(annotation -> {});
        }
        return engine;
    }

    private static String gff3(String... features) {
        StringBuilder sb = new StringBuilder("##gff-version 3.1.26\n");
        sb.append("##sequence-region ").append(ACCESSION).append(" 1 1000\n");
        for (String feature : features) {
            sb.append(feature);
        }
        return sb.toString();
    }

    private static String feature(String type, int start, int end, String phase, String attributes) {
        return String.join("\t", ACCESSION, ".", type, String.valueOf(start), String.valueOf(end), ".", "+", phase)
                + "\t" + attributes + "\n";
    }
}
