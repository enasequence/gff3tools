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

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.ebi.embl.gff3tools.TestUtils;
import uk.ac.ebi.embl.gff3tools.exception.ValidationException;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Attributes;
import uk.ac.ebi.embl.gff3tools.gff3.GFF3Feature;
import uk.ac.ebi.embl.gff3tools.gff3.reader.GFF3FileReader;

class OneParentOnlyValidationTest {

    private static final String MRNA_ID = "mrna_1";

    private OneParentOnlyValidation validation;

    @BeforeEach
    void setUp() {
        validation = new OneParentOnlyValidation();
    }

    @Test
    void validateOneParentOnlyWithSingleParent() throws IOException {
        GFF3Feature mrna = buildmRNAWithCol9String("Parent=gene_A");

        assertDoesNotThrow(() -> validation.validateOneParentOnly(mrna, 1));
    }

    @Test
    void validateOneParentOnlyWithRepeatedCommaSeparatedParent() throws IOException {
        GFF3Feature mrna = buildmRNAWithCol9String("Parent=gene_A,gene_A");

        assertDoesNotThrow(() -> validation.validateOneParentOnly(mrna, 1));
    }

    @Test
    void validateOneParentOnlyWithRepeatedParent() throws IOException {
        GFF3Feature mrna = buildmRNAWithCol9String("Parent=gene_A;Parent=gene_A");

        assertDoesNotThrow(() -> validation.validateOneParentOnly(mrna, 1));
    }

    @Test
    void throwsValidationExceptionWhenMultipleCommaSeparatedParents() throws IOException {
        GFF3Feature mrna = buildmRNAWithCol9String("Parent=gene_A,gene_B");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateOneParentOnly(mrna, 1));

        assertTrue(exception.getMessage().contains("Feature \"" + MRNA_ID + "\""));
        assertTrue(exception.getMessage().contains("references 2 parents (gene_A, gene_B)"));
    }

    @Test
    void throwsValidationExceptionWhenMultipleParentAttributes() throws IOException {
        GFF3Feature mrna = buildmRNAWithCol9String("Parent=gene_A;Parent=gene_B");

        ValidationException exception =
                assertThrows(ValidationException.class, () -> validation.validateOneParentOnly(mrna, 1));

        assertTrue(exception.getMessage().contains("Feature \"" + MRNA_ID + "\""));
        assertTrue(exception.getMessage().contains("references 2 parents (gene_A, gene_B)"));
    }

    private static GFF3Feature buildmRNAWithCol9String(String attributes) throws IOException {
        Map<String, List<String>> attributesMap;
        // Only attribute parsing is used, so the reader needs no validation engine and no real file.
        try (GFF3FileReader reader = new GFF3FileReader(null, new StringReader(""), Path.of("input.gff3"))) {
            attributesMap = reader.attributesFromString(attributes);
        }
        String parentId = attributesMap.get(GFF3Attributes.ATTRIBUTE_PARENT).get(0);
        GFF3Feature mrna = TestUtils.createGFF3Feature(MRNA_ID, parentId, "mRNA", "ACC123", 100, 500);
        mrna.addAttributes(attributesMap);
        return mrna;
    }
}
