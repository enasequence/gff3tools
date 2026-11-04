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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.AggregatedValidationException;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngine;
import uk.ac.ebi.embl.gff3tools.validation.ValidationEngineBuilder;

class Gff3SortingTest {

    @Test
    void geneModelListedLeafFirstIsRejected() throws Exception {
        ValidationEngine engine = assertDoesNotThrow(() -> run("sorting/child_before_parent.gff3"));

        List<ValidationException> errors = engine.getCollectedErrors();
        assertEquals(1, errors.size(), "Errors: " + errors);
        assertEquals(ParentBeforeChildValidation.VALIDATION_RULE, errors.get(0).getValidationRule());
        assertTrue(errors.get(0).getMessage().contains("before its Parent \"mrna1\""), "Errors: " + errors);
        assertTrue(errors.get(0).getMessage().contains("before its Parent \"gene1\""), "Errors: " + errors);
        assertThrows(AggregatedValidationException.class, engine::throwIfErrorsCollected);
    }

    private static ValidationEngine run(String resource) throws Exception {
        ValidationEngine engine = new ValidationEngineBuilder()
                .overrideMethodFixs(Map.of("GAP_GENERATION", false))
                .failFast(false)
                .build();
        try (GFF3FileReader reader =
                new GFF3FileReader(engine, TestUtils.getResourceFile(resource).toPath())) {
            reader.readHeader();
            reader.read(annotation -> {});
        }
        return engine;
    }
}
